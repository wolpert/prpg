package com.prpg.content;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.prpg.assets.AssetManifest;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The single seam through which game code resolves a <em>logical</em> content path (e.g.
 * {@code "maps/foo.tmx"}, {@code "items/items.yaml"}) to a readable {@link FileHandle}. Every
 * consumer that previously called {@code Gdx.files.internal(path)} for content goes through here.
 *
 * <p><b>Resolution order:</b> a mounted content pack ({@link PackRegistry}) first, then the bundled
 * internal assets. With no packs mounted this is identical to the old direct {@code Gdx.files.internal}
 * calls, so the game runs unchanged until content is migrated into packs.
 */
@Singleton
public class ContentResolver {

    private final AssetManifest manifest;
    private final PackRegistry packs;
    /** Fallback for paths no mounted pack provides — the bundled internal assets. */
    private final FileHandleResolver internal;

    @Inject
    public ContentResolver(AssetManifest manifest, PackRegistry packs) {
        this(manifest, packs, Gdx.files::internal);
    }

    /** Test seam: supply the internal-assets fallback directly, bypassing {@code Gdx.files}. */
    ContentResolver(AssetManifest manifest, PackRegistry packs, FileHandleResolver internal) {
        this.manifest = manifest;
        this.packs = packs;
        this.internal = internal;
    }

    /** Resolves a logical content path to a {@link FileHandle} for reading (pack, then bundled). */
    public FileHandle resolve(String logicalPath) {
        FileHandle fromPack = packs.resolve(logicalPath);
        if (fromPack != null) {
            return fromPack;
        }
        if (!manifest.contains(logicalPath)) {
            Log.debug("ContentResolver", "\"" + logicalPath
                    + "\" not in any pack and not in the manifest; falling back to bundled handle (may not exist)");
        }
        return internal.resolve(logicalPath);
    }

    /** Whether the given logical content path is known to exist (in a pack or bundled). */
    public boolean exists(String logicalPath) {
        if (packs.has(logicalPath)) {
            return true;
        }
        return manifest.contains(logicalPath) || internal.resolve(logicalPath).exists();
    }

    /** All known content paths under {@code prefix} (pack ∪ bundled), sorted and de-duplicated. */
    public List<String> list(String prefix) {
        TreeSet<String> out = new TreeSet<>(packs.list(prefix));
        for (String path : manifest.files()) {
            if (path.startsWith(prefix)) {
                out.add(path);
            }
        }
        return new ArrayList<>(out);
    }

    /** Adapts this resolver to the libGDX {@link FileHandleResolver} SPI (e.g. for {@code TmxMapLoader}). */
    public FileHandleResolver fileHandleResolver() {
        return this::resolve;
    }
}
