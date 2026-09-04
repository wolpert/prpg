package com.prpg.narrative.content;

import com.badlogic.gdx.files.FileHandle;
import com.prpg.content.ContentResolver;
import com.prpg.util.Log;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The single {@link ActContentSource}: reads an act's compiled story from whichever mounted content
 * pack provides {@code narrative/<id>.ink.json} (committed by {@code ./gradlew compileInk}), resolved
 * through the {@link ContentResolver} content seam. A free/bundled pack and a delivered DLC pack are
 * mounted the same way, so they are read identically — there is no separate DLC delivery path.
 */
@Singleton
public class PackContentSource implements ActContentSource {

    static final String DIR = "narrative/";

    private final ContentResolver content;

    @Inject
    public PackContentSource(ContentResolver content) {
        this.content = content;
    }

    private FileHandle file(String actId) {
        return content.resolve(DIR + actId + ".ink.json");
    }

    @Override
    public boolean has(String actId) {
        return file(actId).exists();
    }

    @Override
    public String read(String actId) {
        try {
            return file(actId).readString("UTF-8");
        } catch (RuntimeException e) {
            Log.error("PackContentSource",
                    "failed to read compiled Ink for act '" + actId + "' at " + DIR + actId + ".ink.json", e);
            throw e;
        }
    }
}
