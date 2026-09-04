package com.prpg.content;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.prpg.assets.AssetManifest;
import com.prpg.config.ConfigLoader;
import com.prpg.content.config.InstalledPacks;
import com.prpg.content.config.PackManifest;
import com.prpg.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Mounts content packs into the writable content root at startup, then refreshes the
 * {@link PackRegistry}. Three things happen, all idempotent and version-guarded:
 *
 * <ol>
 *   <li><b>Bundled extraction</b> — packs shipped inside the app (internal {@code packs/<id>/},
 *       enumerated via {@link AssetManifest} so no internal directory listing is needed) are copied
 *       out to the content root on first run or when a newer version ships.</li>
 *   <li><b>Zip install</b> — any {@code *.zip} dropped into the content root is extracted into
 *       {@code <root>/<packId>/} (the pack id read from the zip's {@code pack.yaml}), then deleted.</li>
 *   <li><b>Loose-folder backfill</b> — any {@code <root>/<id>/pack.yaml} present (e.g. a dev dropped an
 *       unzipped pack) is reconciled into the registry.</li>
 *   <li><b>Registry write</b> — {@code packs.yaml} is rewritten so the (Android-friendly,
 *       listing-free) enumeration source stays accurate.</li>
 * </ol>
 *
 * <p>Listing the <em>writable</em> content root works on both desktop and Android (it is a real
 * filesystem, unlike bundled APK assets), so discovery here needs no directory-listing workaround.
 * The whole operation is best-effort: any failure logs and leaves the game to fall back to bundled
 * internal assets via {@link ContentResolver}.
 */
@Singleton
public class PackMounter {

    private static final String ZIP_SUFFIX = ".zip";
    private static final String STAGING_DIR = ".staging";
    /** Internal-assets sub-directory under which bundled packs ship (staged at build time). */
    static final String INTERNAL_PACKS_DIR = "packs/";

    private final ContentRoot root;
    private final ConfigLoader configLoader;
    private final PackRegistry registry;
    private final AssetManifest manifest;
    /** Reads bundled packs from the internal assets; the seam lets tests supply a fake source. */
    private final FileHandleResolver internal;

    @Inject
    public PackMounter(ContentRoot root, ConfigLoader configLoader, PackRegistry registry,
                       AssetManifest manifest) {
        this(root, configLoader, registry, manifest, Gdx.files::internal);
    }

    /** Test seam: supply the internal-assets source directly, bypassing {@code Gdx.files}. */
    PackMounter(ContentRoot root, ConfigLoader configLoader, PackRegistry registry,
                AssetManifest manifest, FileHandleResolver internal) {
        this.root = root;
        this.configLoader = configLoader;
        this.registry = registry;
        this.manifest = manifest;
        this.internal = internal;
    }

    /** Runs the full mount sequence; safe to call once early in {@code TheGame.create()}. */
    public void mount() {
        try {
            FileHandle packsRoot = root.packsRoot();
            packsRoot.mkdirs();

            Map<String, Integer> installed = readRegistry(packsRoot);
            extractBundledPacks(installed);
            installZips(packsRoot, installed);
            backfillLooseFolders(packsRoot, installed);
            writeRegistry(packsRoot, installed);
            registry.reload();
            Log.info("PackMounter", "mounted packs " + installed);
        } catch (Exception e) {
            Log.error("PackMounter", "content mount failed; falling back to bundled assets", e);
        }
    }

    private Map<String, Integer> readRegistry(FileHandle packsRoot) {
        Map<String, Integer> out = new LinkedHashMap<>();
        FileHandle file = packsRoot.child(PackRegistry.REGISTRY_FILE);
        if (!file.exists() || file.isDirectory()) {
            return out;
        }
        try (Reader r = file.reader("UTF-8")) {
            InstalledPacks installed = configLoader.load(InstalledPacks.class, r);
            if (installed != null && installed.packs != null) {
                for (InstalledPacks.Entry e : installed.packs) {
                    if (e != null && e.id != null) {
                        out.put(e.id, e.version);
                    }
                }
            }
        } catch (Exception e) {
            Log.error("PackMounter", "unreadable registry; rebuilding", e);
        }
        return out;
    }

    /**
     * Copies packs shipped inside the app (internal {@code packs/<id>/}) out to the content root,
     * version-guarded so it only runs on first install or when a newer copy ships. Bundled pack ids
     * come from the {@link AssetManifest} (so Android needs no internal directory listing); each pack's
     * file set comes from its own {@code pack.yaml}.
     */
    private void extractBundledPacks(Map<String, Integer> installed) {
        for (String id : bundledPackIds()) {
            FileHandle manifestFile = internal.resolve(INTERNAL_PACKS_DIR + id + "/" + PackRegistry.PACK_MANIFEST);
            if (!manifestFile.exists()) {
                continue;
            }
            PackManifest manifest = parse(manifestFile);
            if (manifest == null || manifest.id == null) {
                Log.error("PackMounter", "bundled pack manifest unreadable or id-less; not extracted: "
                        + INTERNAL_PACKS_DIR + id + "/" + PackRegistry.PACK_MANIFEST);
                continue;
            }
            Integer existing = installed.get(manifest.id);
            if (existing != null && existing >= manifest.version) {
                continue; // already current or newer
            }
            FileHandle dest = root.packs(manifest.id);
            dest.deleteDirectory();
            copyInternal(INTERNAL_PACKS_DIR + id + "/" + PackRegistry.PACK_MANIFEST,
                    dest.child(PackRegistry.PACK_MANIFEST));
            for (String file : manifest.files) {
                copyInternal(INTERNAL_PACKS_DIR + id + "/" + file, dest.child(file));
            }
            installed.put(manifest.id, manifest.version);
            Log.debug("PackMounter", "extracted bundled pack " + manifest.id + " v" + manifest.version);
        }
    }

    /** Bundled pack ids, derived from internal {@code packs/<id>/pack.yaml} entries in the manifest. */
    private Set<String> bundledPackIds() {
        Set<String> ids = new LinkedHashSet<>();
        String suffix = "/" + PackRegistry.PACK_MANIFEST;
        for (String path : manifest.files()) {
            if (path.startsWith(INTERNAL_PACKS_DIR) && path.endsWith(suffix)) {
                String rest = path.substring(INTERNAL_PACKS_DIR.length());
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    ids.add(rest.substring(0, slash));
                }
            }
        }
        return ids;
    }

    private void copyInternal(String internalPath, FileHandle dest) {
        FileHandle src = internal.resolve(internalPath);
        if (!src.exists()) {
            Log.error("PackMounter", "bundled file missing: " + internalPath);
            return;
        }
        dest.parent().mkdirs();
        src.copyTo(dest);
    }

    private void installZips(FileHandle packsRoot, Map<String, Integer> installed) {
        for (FileHandle child : packsRoot.list()) {
            if (child.isDirectory() || !child.name().toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX)) {
                continue;
            }
            installZip(packsRoot, child, installed);
        }
    }

    private void installZip(FileHandle packsRoot, FileHandle zip, Map<String, Integer> installed) {
        FileHandle staging = packsRoot.child(STAGING_DIR);
        try {
            staging.deleteDirectory();
            unzip(zip, staging);

            FileHandle manifestFile = staging.child(PackRegistry.PACK_MANIFEST);
            if (!manifestFile.exists()) {
                Log.error("PackMounter", "zip has no pack.yaml: " + zip.name());
                return;
            }
            PackManifest manifest = parse(manifestFile);
            if (manifest == null || manifest.id == null) {
                Log.error("PackMounter", "zip \"" + zip.name() + "\" has empty/id-less pack.yaml; not installed");
                return;
            }
            Integer existing = installed.get(manifest.id);
            if (existing != null && existing >= manifest.version) {
                // Already at this version or newer — keep what's installed.
                return;
            }
            FileHandle dest = root.packs(manifest.id);
            dest.deleteDirectory();
            staging.moveTo(dest);
            installed.put(manifest.id, manifest.version);
            Log.debug("PackMounter", "installed pack " + manifest.id + " v" + manifest.version
                    + " from " + zip.name());
        } catch (Exception e) {
            Log.error("PackMounter", "failed to install " + zip.name(), e);
        } finally {
            staging.deleteDirectory();
            zip.delete();
        }
    }

    private void backfillLooseFolders(FileHandle packsRoot, Map<String, Integer> installed) {
        for (FileHandle child : packsRoot.list()) {
            if (!child.isDirectory() || child.name().startsWith(".")) {
                continue;
            }
            FileHandle manifestFile = child.child(PackRegistry.PACK_MANIFEST);
            if (!manifestFile.exists()) {
                continue;
            }
            PackManifest manifest = parse(manifestFile);
            if (manifest != null && manifest.id != null) {
                installed.put(manifest.id, manifest.version);
            } else {
                Log.debug("PackMounter", "loose folder \"" + child.name()
                        + "\" has empty/id-less pack.yaml; not backfilled");
            }
        }
    }

    private void writeRegistry(FileHandle packsRoot, Map<String, Integer> installed) {
        StringBuilder sb = new StringBuilder("# Installed content packs (generated by PackMounter).\npacks:\n");
        List<String> ids = new ArrayList<>(installed.keySet());
        ids.sort(String::compareTo);
        for (String id : ids) {
            sb.append("  - { id: ").append(id).append(", version: ").append(installed.get(id)).append(" }\n");
        }
        packsRoot.child(PackRegistry.REGISTRY_FILE).writeString(sb.toString(), false, "UTF-8");
    }

    private PackManifest parse(FileHandle manifestFile) {
        try (Reader r = manifestFile.reader("UTF-8")) {
            return configLoader.load(PackManifest.class, r);
        } catch (Exception e) {
            Log.error("PackMounter", "failed to parse " + manifestFile.path(), e);
            return null;
        }
    }

    private static void unzip(FileHandle zip, FileHandle dest) throws IOException {
        dest.mkdirs();
        String destCanonical = dest.file().getCanonicalPath();
        try (ZipInputStream zis = new ZipInputStream(zip.read())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                FileHandle out = dest.child(entry.getName());
                // Zip-slip guard: refuse any entry that escapes the destination directory.
                if (!out.file().getCanonicalPath().startsWith(destCanonical)) {
                    throw new IOException("zip entry escapes destination: " + entry.getName());
                }
                out.parent().mkdirs();
                try (OutputStream os = out.write(false)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }
}
