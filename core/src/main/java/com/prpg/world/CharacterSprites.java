package com.prpg.world;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.prpg.ecs.component.OrientationComponent.Direction;

/**
 * Ready-to-use per-direction idle/walk animations for one character, built by
 * {@link CharacterSpriteLoader} from a {@link com.prpg.config.CharacterSpriteDef}.
 * Indexed by {@link #directionIndex(Direction)} (DOWN/UP/RIGHT/LEFT) × {idle, walk}.
 */
public final class CharacterSprites {

    private final Animation<TextureRegion>[][] animations; // [dir][0=idle,1=walk]
    private final TextureRegion[] idle;                    // [dir]
    private final int frameWidth;
    private final int frameHeight;

    CharacterSprites(Animation<TextureRegion>[][] animations, TextureRegion[] idle,
                     int frameWidth, int frameHeight) {
        this.animations = animations;
        this.idle = idle;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public Animation<TextureRegion> animation(int dirIndex, boolean moving) {
        return animations[dirIndex][moving ? 1 : 0];
    }

    public TextureRegion idle(int dirIndex) {
        return idle[dirIndex];
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public static int directionIndex(Direction d) {
        return switch (d) {
            case DOWN -> 0;
            case UP -> 1;
            case RIGHT -> 2;
            case LEFT -> 3;
        };
    }
}
