package com.prpg.content;

import com.badlogic.gdx.files.FileHandle;
import com.prpg.config.ConfigLoader;
import com.prpg.content.config.InstalledPacks;
import com.prpg.content.config.PackManifest;
import com.prpg.util.Log;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The runtime view of the content packs mounted under the content root. Reads the installed-pack
 * registry ({@code packs.yaml}) — never by listing the directory, so it works identically on Android
 * — and each pack's {@code pack.yaml}, then answers "which mounted pack owns this logical path?".
 *
 * <p>Resolution order is act/epilogue packs first, then {@code baseline} last, so a pack that ships
 * its own copy of a shared asset shadows the baseline. {@code ContentResolver} consults this ahead of
 * the bundled internal assets.
 */
@Singleton
public class PackRegistry {

    static final String REGISTRY_FILE = "packs.yaml";
    static final String PACK_MANIFEST = "pack.yaml";
    static final String BASELINE_KIND = "baseline";

    private final ContentRoot root;
    private final ConfigLoader configLoader;

    /** Mounted packs in priority order (baseline last); null until first loaded. */
    private List<MountedPack> packs;

    @Inject
    public PackRegistry(ContentRoot root, ConfigLoader configLoader) {
        this.root = root;
        this.configLoader = configLoader;
    }

    /** A single mounted pack: its extracted root and the authoritative set of files it owns. */
    public static final class MountedPack {
        public final String id;
        public final int version;
        public final FileHandle root;
        public final Set<String> files;
        public final PackManifest manifest;

        MountedPack(String id, int version, FileHandle root, Set<String> files, PackManifest manifest) {
            this.id = id;
            this.version = version;
            this.root = root;
            this.files = files;
            this.manifest = manifest;
        }
    }

    private synchronized void ensureLoaded() {
        if (packs != null) {
            return;
        }
        packs = load();
    }

    /** Drops the cached view so a subsequent lookup re-reads the registry (after a mount). */
    public synchronized void reload() {
        packs = null;
    }

    private List<MountedPack> load() {
        List<MountedPack> result = new ArrayList<>();
        FileHandle registry = root.packs(REGISTRY_FILE);
        if (!registry.exists() || registry.isDirectory()) {
            Log.debug("PackRegistry", "no " + REGISTRY_FILE + " at content root; no packs mounted");
            return result;
        }
        InstalledPacks installed;
        try (Reader r = registry.reader("UTF-8")) {
            installed = configLoader.load(InstalledPacks.class, r);
        } catch (Exception e) {
            Log.error("PackRegistry", "failed to read " + REGISTRY_FILE, e);
            return result;
        }
        if (installed == null || installed.packs == null) {
            return result;
        }
        for (InstalledPacks.Entry entry : installed.packs) {
            MountedPack pack = mount(entry);
            if (pack != null) {
                result.add(pack);
            }
        }
        // Baseline serves shared assets as a fallback, so it must rank below act/epilogue packs that
        // may ship their own copy. List.sort is stable, so other packs keep their registry order.
        result.sort((a, b) -> Boolean.compare(isBaseline(a.manifest), isBaseline(b.manifest)));
        return result;
    }

    private MountedPack mount(InstalledPacks.Entry entry) {
        if (entry == null || entry.id == null) {
            Log.error("PackRegistry", "skipping " + REGISTRY_FILE + " entry with no id");
            return null;
        }
        FileHandle dir = root.packs(entry.id);
        FileHandle manifestFile = dir.child(PACK_MANIFEST);
        if (!manifestFile.exists()) {
            Log.error("PackRegistry", "no " + PACK_MANIFEST + " for pack " + entry.id);
            return null;
        }
        PackManifest manifest;
        try (Reader r = manifestFile.reader("UTF-8")) {
            manifest = configLoader.load(PackManifest.class, r);
        } catch (Exception e) {
            Log.error("PackRegistry", "failed to read " + PACK_MANIFEST + " for " + entry.id, e);
            return null;
        }
        if (manifest == null) {
            Log.error("PackRegistry", "pack \"" + entry.id + "\" pack.yaml parsed to null (empty/invalid); not mounted");
            return null;
        }
        Set<String> files = new LinkedHashSet<>(manifest.files != null ? manifest.files : List.of());
        return new MountedPack(entry.id, manifest.version, dir, files, manifest);
    }

    private static boolean isBaseline(PackManifest manifest) {
        return manifest != null && BASELINE_KIND.equals(manifest.kind);
    }

    /** The owning pack's handle for {@code logicalPath}, or null if no mounted pack provides it. */
    public FileHandle resolve(String logicalPath) {
        ensureLoaded();
        for (MountedPack pack : packs) {
            if (pack.files.contains(logicalPath)) {
                return pack.root.child(logicalPath);
            }
        }
        return null;
    }

    /** Whether any mounted pack provides {@code logicalPath}. */
    public boolean has(String logicalPath) {
        ensureLoaded();
        for (MountedPack pack : packs) {
            if (pack.files.contains(logicalPath)) {
                return true;
            }
        }
        return false;
    }

    /** All pack-provided content paths under {@code prefix}, sorted and de-duplicated. */
    public List<String> list(String prefix) {
        ensureLoaded();
        TreeSet<String> out = new TreeSet<>();
        for (MountedPack pack : packs) {
            for (String file : pack.files) {
                if (file.startsWith(prefix)) {
                    out.add(file);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** Mounted packs in priority order (baseline last). */
    public List<MountedPack> mounted() {
        ensureLoaded();
        return List.copyOf(packs);
    }

    /** The manifests of all mounted packs (for merging pack-declared acts into the narrative catalog). */
    public List<PackManifest> manifests() {
        ensureLoaded();
        List<PackManifest> out = new ArrayList<>();
        for (MountedPack pack : packs) {
            out.add(pack.manifest);
        }
        return out;
    }
}
