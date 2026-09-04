package com.prpg.config;

/**
 * Data-driven description of a top-down character's sprites, loaded from a {@code *.sprite.yaml}
 * content file (via {@link ConfigLoader}). One descriptor per character (player and, later, NPCs);
 * {@code world/CharacterSpriteLoader} turns it into ready-to-use per-direction animations.
 *
 * <p>Animations are looked up in the atlas by region name {@code <region>_<state>_<direction>}, with
 * states {@code idle}/{@code walk} and directions {@code down}/{@code up}/{@code right}. LEFT is not
 * authored — when {@link #flipRightForLeft} is true the loader derives it by flipping the RIGHT
 * frames (the Mystic Woods sheets have no left-facing art).
 *
 * <p>Lives in the {@code config} package so the Android proguard rule {@code -keep class **.config.**}
 * keeps these fields for SnakeYAML's FIELD-access binding.
 */
public class CharacterSpriteDef {

    /** Logical content path to the texture atlas, e.g. {@code atlases/baseline.atlas}. */
    public String atlas;

    /** Region-name prefix, e.g. {@code player} for regions {@code player_walk_down}. */
    public String region;

    /** Source frame size in pixels (used for camera/pointer centering). */
    public int frameWidth = 48;
    public int frameHeight = 48;

    /** Seconds per frame; atlases carry no timing, so it is declared here. */
    public float frameDuration = 0.12f;

    /** When true, the LEFT animation is the RIGHT animation flipped horizontally. */
    public boolean flipRightForLeft = true;
}
