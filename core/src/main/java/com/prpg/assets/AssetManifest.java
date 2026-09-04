package com.prpg.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Read-only view of {@code assets/assets.txt}, the gradle-generated list of every file under
 * {@code assets/}. {@link com.prpg.screens.LoadingScreen} consults this to
 * confirm each {@link Asset} entry's path actually exists before queuing it — so a renamed
 * or deleted asset fails fast at startup with a precise error rather than a generic
 * {@code AssetManager} miss midway through loading.
 *
 * <p>{@code assets.txt} is rewritten by the {@code generateAssetList} task wired into every
 * non-Android subproject's {@code processResources}; never hand-edit it.
 */
@Singleton
public class AssetManifest {

    public static final String MANIFEST_PATH = "assets.txt";

    private final Set<String> files;

    @Inject
    public AssetManifest() {
        this(readInternal());
    }

    /** Test-only: bypass {@code Gdx.files} so the manifest can be unit-tested without libGDX init. */
    AssetManifest(Set<String> files) {
        this.files = Collections.unmodifiableSet(files);
    }

    public boolean contains(String path) {
        return files.contains(path);
    }

    public Set<String> files() {
        return files;
    }

    private static Set<String> readInternal() {
        try (Reader r = Gdx.files.internal(MANIFEST_PATH).reader()) {
            return parse(r);
        } catch (IOException e) {
            throw new GdxRuntimeException("failed to read " + MANIFEST_PATH
                    + " — re-run :core:processResources to regenerate it", e);
        }
    }

    static Set<String> parse(Reader reader) throws IOException {
        Set<String> set = new LinkedHashSet<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
        }
        return set;
    }
}
