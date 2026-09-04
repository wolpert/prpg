package com.prpg.world.stage;

/**
 * A fully resolved "this is in the world right now" record: the flat, condition-free answer
 * {@link StageDirector} hands to the entity factory once the {@code when}/{@code unless} rules and
 * any imperative overrides have been applied.
 *
 * <p>Immutable and libGDX-free, so the whole resolution path is unit-testable headless. Marker
 * coordinates are deliberately <em>not</em> resolved here: a placement names a map and a marker,
 * and the marker is looked up only when that map is actually built.
 */
public record StagedPlacement(
        Kind kind,
        // Actor id, obstacle id, trigger id, or prop name: the stable handle overrides key on.
        String id,
        String mapId,
        String marker,
        // Ink knot to run on interact, or null.
        String dialogue,
        boolean solid,
        // Placeholder swatch colour (6-hex, no '#'), or null.
        String color,
        // Sprite descriptor for an actor with real art, or null.
        String sprite,
        // ActivityLauncher key ("match3"/"merge"/"lightsout"/...) for an obstacle that launches one.
        String activityType,
        String activityId,
        String itemId,
        String setFlag,
        String requireFlag,
        boolean fireOnce,
        String event) {

    public enum Kind { ACTOR, OBSTACLE, ITEM, TRIGGER, PROP }

    public static StagedPlacement actor(String id, String mapId, String marker, String dialogue,
                                        boolean solid, String color, String sprite) {
        return new StagedPlacement(Kind.ACTOR, id, mapId, marker, dialogue, solid, color, sprite,
                null, null, null, null, null, false, null);
    }

    public static StagedPlacement obstacle(String id, String mapId, String marker, String dialogue,
                                           boolean solid, String color,
                                           String activityType, String activityId) {
        return new StagedPlacement(Kind.OBSTACLE, id, mapId, marker, dialogue, solid, color, null,
                activityType, activityId, null, null, null, false, null);
    }

    public static StagedPlacement item(String itemId, String mapId, String marker) {
        return new StagedPlacement(Kind.ITEM, itemId, mapId, marker, null, false, null, null,
                null, null, itemId, null, null, false, null);
    }

    public static StagedPlacement trigger(String id, String mapId, String marker, String setFlag,
                                          String requireFlag, boolean fireOnce, String event) {
        return new StagedPlacement(Kind.TRIGGER, id, mapId, marker, null, false, null, null,
                null, null, null, setFlag, requireFlag, fireOnce, event);
    }

    /** A prop override carries no position: the map owns where the prop stands. */
    public static StagedPlacement prop(String name, String dialogue, boolean solid) {
        return new StagedPlacement(Kind.PROP, name, null, null, dialogue, solid, null, null,
                null, null, null, null, null, false, null);
    }

    /** Whether this placement launches an activity on interact. */
    public boolean hasActivity() {
        return activityType != null && activityId != null;
    }
}
