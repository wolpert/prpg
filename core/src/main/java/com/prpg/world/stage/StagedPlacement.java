package com.prpg.world.stage;

/**
 * A fully resolved "this is in the world right now" record: one call the act's Ink {@code stage()}
 * function made, joined with that id's look from {@code cast()}.
 *
 * <p>Immutable and libGDX-free, so the whole resolution path is unit-testable headless. Marker
 * coordinates are deliberately <em>not</em> resolved here: a placement names a map and a marker,
 * and the marker is looked up only when that map is actually built.
 */
public record StagedPlacement(
        Kind kind,
        // The stable handle the story used: actor id, thing id or zone id.
        String id,
        String mapId,
        String marker,
        // Ink knot run on interact (actor, thing) or on walking in (zone); null for silent scenery.
        String knot,
        // Blocks player movement while staged (things only).
        boolean solid,
        // Sprite descriptor (sprites/<sprite>.sprite.yaml) from cast(), or null.
        String sprite,
        // Placeholder swatch colour (6-hex, no '#') from cast(), or null.
        String color) {

    /**
     * {@code ACTOR}: a character ({@code actor(...)}). {@code THING}: an object, optionally solid
     * ({@code thing(...)}). {@code ZONE}: an invisible walk-in area ({@code zone(...)}).
     */
    public enum Kind { ACTOR, THING, ZONE }
}
