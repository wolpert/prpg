package com.prpg.content;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.prpg.AppInfo;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The single source of truth for the writable, per-user storage root the game owns at runtime:
 * where the save file and installed content packs live.
 *
 * <p><b>Why two locations:</b> the desktop launcher runs with its working dir set to the git-tracked
 * {@code assets/} folder, so {@link com.badlogic.gdx.Files#local} would scribble into the repo.
 * Desktop therefore uses {@link com.badlogic.gdx.Files#external} (the user's home dir); Android uses
 * {@link com.badlogic.gdx.Files#local} (the app-private files dir), since Android external storage is
 * public and permission-gated. Both are working-directory-independent.
 */
@Singleton
public class ContentRoot {

    /** Per-app sub-directory under the user's home dir (the desktop external-storage root). */
    static final String DESKTOP_HOME_DIR = "." + AppInfo.NAME + "/";

    /** Leaf under the writable root that holds extracted content packs (see {@code PackMounter}). */
    public static final String PACKS_DIR = "content/";

    @Inject
    public ContentRoot() {}

    /** True on Android, where the writable root is the app-private local files dir. */
    public boolean isAndroid() {
        return Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
    }

    /**
     * A handle under the writable per-user root. On desktop this is {@code ~/.<app>/<relPath>};
     * on Android it is the app-private {@code <relPath>}.
     */
    public FileHandle writable(String relPath) {
        if (isAndroid()) {
            return Gdx.files.local(relPath);
        }
        return Gdx.files.external(DESKTOP_HOME_DIR + relPath);
    }

    /** A handle under the extracted-content-packs root ({@code <writable>/content/<relPath>}). */
    public FileHandle packs(String relPath) {
        return writable(PACKS_DIR + relPath);
    }

    /** The extracted-content-packs root directory ({@code <writable>/content/}). */
    public FileHandle packsRoot() {
        return packs("");
    }
}
