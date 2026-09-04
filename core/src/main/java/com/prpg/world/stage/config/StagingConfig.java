package com.prpg.world.stage.config;

import java.util.ArrayList;
import java.util.List;

/**
 * One act's staging: who and what is placed in the world, where, and under which conditions. Read
 * from {@code staging/<actId>.yaml} in that act's pack: the single file that answers "what does the
 * world contain during this act".
 *
 * <p>This is the seam that keeps a TMX to pure geography. A map owns terrain, collision, spawns,
 * portals, named {@code markers} and immovable props; everything act-scoped (cast, obstacles, items,
 * triggers) is placed here, against a marker name rather than pixel coordinates, so re-laying-out a
 * map never breaks act content.
 *
 * <p><b>Resolution:</b> entries are ordered story-early to story-late. For an actor, the <em>last</em>
 * entry whose {@code when}/{@code unless} conditions hold wins; no match means that actor is not in
 * the world at all. Non-actor entries are independent and appear whenever their conditions hold.
 * Because it is a pure function of {@code FlagStore} it costs nothing in the save file.
 *
 * <p>Plain public fields with snake_case names matching the YAML keys, populated by SnakeYAML field
 * access.
 */
public class StagingConfig {

    /** The act this staging belongs to; must match the pack's act id. */
    public String act;

    /** Characters placed during this act. Ordered story-early to story-late (last match wins). */
    public List<CastEntry> cast = new ArrayList<>();

    /** Blocking/interactive act content: a hedge barring a doorway, an activity launcher, debris. */
    public List<ObstacleEntry> obstacles = new ArrayList<>();

    /** Pick-ups placed for this act. */
    public List<ItemEntry> items = new ArrayList<>();

    /** Walk-in zones (flags / cutscenes) placed for this act. */
    public List<TriggerEntry> triggers = new ArrayList<>();

    /** Per-act dialogue (and solidity) for the immovable props a map declares in its own layer. */
    public List<PropEntry> props = new ArrayList<>();

    /** A location: a named marker object in a map's {@code markers} layer. */
    public static class At {
        public String map;
        public String marker;
    }

    /**
     * Fields shared by every staged thing. Kept in a base class so the condition and solidity
     * vocabulary is identical everywhere; SnakeYAML's field access walks superclass fields.
     */
    public static class Entry {
        /** Where this sits. Required for everything except a {@code props} override. */
        public At at;

        /** Present only while every listed flag is set. Empty = unconditional. */
        public List<String> when = new ArrayList<>();

        /** Absent while any listed flag is set. */
        public List<String> unless = new ArrayList<>();

        /** Whether this blocks player movement (the base value; the conditions below refine it). */
        public boolean solid;

        /** Solid only while every listed flag is set. Empty = no extra condition. */
        public List<String> solid_when = new ArrayList<>();

        /** Not solid while any listed flag is set. */
        public List<String> solid_unless = new ArrayList<>();
    }

    /** A character placement. {@code actor} names an {@link ActorDef}. */
    public static class CastEntry extends Entry {
        public String actor;
        /** Ink knot to run on interact, resolved against <em>this act's</em> story. */
        public String dialogue;
    }

    /** Act content that occupies space: the "hedge bars the doorway until cleared" mechanic. */
    public static class ObstacleEntry extends Entry {
        public String id;
        /** Optional knot barked on interact when there is no activity (or the activity is done). */
        public String dialogue;
        /** Placeholder swatch colour, 6-digit hex with no {@code #}. */
        public String color;
        /** Optional activity this obstacle launches when the player interacts with it. */
        public ActivityRef activity;
        /** Once this flag is set the obstacle is gone for good; solved content stays solved. */
        public String cleared_when;
    }

    /** The activity an obstacle launches: {@code type} is the ActivityLauncher key. */
    public static class ActivityRef {
        public String type;
        public String id;
    }

    public static class ItemEntry extends Entry {
        public String itemId;
    }

    public static class TriggerEntry extends Entry {
        public String id;
        public String set_flag;
        public String require_flag;
        public boolean fire_once;
        public String event;
    }

    /**
     * A per-act override for a prop the map itself declares. Without this, a prop's {@code dialogueId}
     * is resolved against whatever act is current, so an act-1 knot silently does nothing from act 2
     * onward. {@code at} is unused here: the map owns the prop's position.
     */
    public static class PropEntry extends Entry {
        public String prop;
        public String dialogue;
    }
}
