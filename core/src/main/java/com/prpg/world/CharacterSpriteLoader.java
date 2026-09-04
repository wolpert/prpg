package com.prpg.world;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.prpg.config.CharacterSpriteDef;
import com.prpg.content.ContentResolver;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Turns a {@link CharacterSpriteDef} into ready-to-use {@link CharacterSprites}. Shared by the player
 * and (later) NPCs — any character authored in Aseprite and packed into an atlas loads through here.
 *
 * <p>Atlases are cached per logical path, so characters sharing an atlas (e.g. everything in
 * {@code baseline}) reuse one {@link TextureAtlas} (and one GL texture). Atlas regions are looked up
 * by {@code <region>_<state>_<direction>}; the atlas's own Nearest filter (baked in by the
 * {@code buildAtlases} pipeline) keeps the pixel art crisp.
 */
@Singleton
public class CharacterSpriteLoader implements Disposable {

    private static final int DIRS = 4; // index: 0=DOWN, 1=UP, 2=RIGHT, 3=LEFT
    private static final String[] DIR_SUFFIX = {"down", "up", "right"}; // LEFT derived from RIGHT
    private static final int IDX_DOWN = 0, IDX_UP = 1, IDX_RIGHT = 2, IDX_LEFT = 3;

    private final ContentResolver content;
    private final Map<String, TextureAtlas> atlases = new HashMap<>();

    @Inject
    public CharacterSpriteLoader(ContentResolver content) {
        this.content = content;
    }

    @SuppressWarnings("unchecked")
    public CharacterSprites load(CharacterSpriteDef def) {
        TextureAtlas atlas = atlas(def.atlas);

        Animation<TextureRegion>[][] animations = new Animation[DIRS][2];
        TextureRegion[] idle = new TextureRegion[DIRS];

        // Authored directions: down, up, right.
        animations[IDX_DOWN][0] = anim(atlas, def, "idle", "down", false);
        animations[IDX_DOWN][1] = anim(atlas, def, "walk", "down", false);
        animations[IDX_UP][0] = anim(atlas, def, "idle", "up", false);
        animations[IDX_UP][1] = anim(atlas, def, "walk", "up", false);
        animations[IDX_RIGHT][0] = anim(atlas, def, "idle", "right", false);
        animations[IDX_RIGHT][1] = anim(atlas, def, "walk", "right", false);

        // Left: flip of right (default), or its own authored frames.
        boolean flip = def.flipRightForLeft;
        animations[IDX_LEFT][0] = flip ? anim(atlas, def, "idle", "right", true)
                : anim(atlas, def, "idle", "left", false);
        animations[IDX_LEFT][1] = flip ? anim(atlas, def, "walk", "right", true)
                : anim(atlas, def, "walk", "left", false);

        for (int d = 0; d < DIRS; d++) {
            // getKeyFrame(0f) returns the first frame (an element cast); getKeyFrames() would return the
            // backing array, whose runtime type is Object[] (Array<>(int) backing) and fails an array cast.
            idle[d] = animations[d][0].getKeyFrame(0f);
        }
        return new CharacterSprites(animations, idle, def.frameWidth, def.frameHeight);
    }

    private TextureAtlas atlas(String logicalPath) {
        return atlases.computeIfAbsent(logicalPath, p -> new TextureAtlas(content.resolve(p)));
    }

    private static Animation<TextureRegion> anim(TextureAtlas atlas, CharacterSpriteDef def,
                                                 String state, String dir, boolean flip) {
        String name = def.region + "_" + state + "_" + dir;
        Array<TextureAtlas.AtlasRegion> regions = atlas.findRegions(name);
        if (regions.size == 0) {
            throw new GdxRuntimeException("atlas " + def.atlas + " has no regions named '" + name + "'");
        }
        Array<TextureRegion> frames = new Array<>(regions.size);
        for (TextureAtlas.AtlasRegion r : regions) {
            TextureRegion copy = new TextureRegion(r);
            if (flip) copy.flip(true, false);
            frames.add(copy);
        }
        return new Animation<>(def.frameDuration, frames, Animation.PlayMode.LOOP);
    }

    @Override
    public void dispose() {
        for (TextureAtlas atlas : atlases.values()) {
            atlas.dispose();
        }
        atlases.clear();
    }
}
