package com.prpg.config;

/**
 * Engine tunables loaded from {@code config/game.yaml} (baseline pack). Plain public fields for
 * SnakeYAML field access; every field has a default so a partial YAML still boots.
 *
 * <p>There is deliberately no "start map" here: the opening map belongs to the first act's pack
 * ({@code pack.yaml} {@code provides: entryMap/entrySpawn}), so the world never depends on a map
 * the baseline would have to own.
 */
public class GameConfig {
    /** Window / main-menu title. */
    public String title = "prpg";
    public PlayerConfig player = new PlayerConfig();
    public WorldConfig world = new WorldConfig();

    public static class PlayerConfig {
        /** Movement speed in world units (pixels) per second. */
        public float speed = 80f;
    }

    public static class WorldConfig {
        /** Minimum visible world size (pixels); the viewport extends to keep aspect. */
        public float viewWidth = 180f;
        public float viewHeight = 320f;
        /** Left/right gutter (dp) so the map never runs to the screen edge. 0 for edge-to-edge. */
        public float sideGutterDp = 24f;
    }
}
