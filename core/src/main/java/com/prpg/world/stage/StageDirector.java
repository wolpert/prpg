package com.prpg.world.stage;

import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.util.Log;
import com.prpg.world.FlagStore;
import com.prpg.world.stage.config.ActorDef;
import com.prpg.world.stage.config.StagingConfig;
import com.prpg.world.stage.config.StagingConfig.CastEntry;
import com.prpg.world.stage.config.StagingConfig.Entry;
import com.prpg.world.stage.config.StagingConfig.ItemEntry;
import com.prpg.world.stage.config.StagingConfig.ObstacleEntry;
import com.prpg.world.stage.config.StagingConfig.PropEntry;
import com.prpg.world.stage.config.StagingConfig.TriggerEntry;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The runtime authority on what the world contains: it applies an act's {@link StagingConfig} against
 * the current {@link FlagStore} and answers "what is staged on map X right now".
 *
 * <p><b>Two layers, in order.</b> Declarative rules are the default: a pure function of the flags,
 * re-derived on every refresh, so nothing about them needs saving and editing an act's staging file
 * fixes existing saves. Imperative <em>overrides</em> (written by the Ink externals in
 * {@code StateBridge}) sit on top and win, for the moves a flag condition can't express; only those
 * are durable, which keeps the save small.
 *
 * <p><b>The new act's staging is authoritative.</b> {@link #enterAct} clears the previous act's
 * overrides and re-reads from scratch, so an act's opening tableau is fully specified by its own
 * file and a DLC act can run standalone: the same "Java is canonical, content is declarative"
 * split the narrative model already uses.
 *
 * <p>libGDX-free apart from the {@link ContentResolver} read, so resolution is unit-testable
 * headless via {@link #useStaging}.
 */
@Singleton
public class StageDirector {

    private static final String STAGING_DIR = "staging/";

    private final ConfigLoader configLoader;
    private final ContentResolver content;
    private final FlagStore flags;
    private final ActorRegistry actors;

    private StagingConfig staging = new StagingConfig();
    private String currentActId;

    /** Imperative overrides keyed by staged id (actor / obstacle / prop). Durable; saved. */
    private final Map<String, StagedOverride> overrides = new LinkedHashMap<>();

    /** Bumped on every override write so consumers can cheaply detect "the world changed". */
    private int revision;

    @Inject
    public StageDirector(ConfigLoader configLoader, ContentResolver content, FlagStore flags,
                         ActorRegistry actors) {
        this.configLoader = configLoader;
        this.content = content;
        this.flags = flags;
        this.actors = actors;
    }

    /** An imperative placement change: Ink moved, removed, re-voiced, or re-solidified something. */
    public static final class StagedOverride {
        public String id;
        public boolean removed;
        /** Non-null once moved; names the destination map. */
        public String mapId;
        public String marker;
        /** Non-null once re-voiced. */
        public String dialogue;
        /** Non-null once explicitly made solid or non-solid. */
        public Boolean solid;

        public StagedOverride() {}

        public StagedOverride(String id) {
            this.id = id;
        }
    }

    // --- act lifecycle ------------------------------------------------------------------------

    /**
     * Loads {@code staging/<actId>.yaml} and discards the previous act's overrides. An act with no
     * staging file is legal (it simply stages nothing): the legacy TMX object layers still populate
     * such a map, which is what keeps the game playable mid-migration.
     */
    public void enterAct(String actId) {
        currentActId = actId;
        overrides.clear();
        staging = new StagingConfig();
        revision++;
        if (actId == null) return;

        String path = STAGING_DIR + actId + ".yaml";
        if (!content.exists(path)) {
            Log.debug("StageDirector", "act '" + actId + "' has no " + path + "; nothing staged");
            return;
        }
        try (Reader reader = content.resolve(path).reader()) {
            StagingConfig loaded = configLoader.load(StagingConfig.class, reader);
            if (loaded != null) {
                staging = loaded;
                Log.info("StageDirector", "staged act '" + actId + "' (" + staging.cast.size()
                        + " cast, " + staging.obstacles.size() + " obstacle(s))");
            }
        } catch (Exception e) {
            Log.error("StageDirector", "failed to load " + path + "; nothing staged for this act", e);
        }
    }

    /** Test seam: install staging directly, bypassing the content read. */
    public void useStaging(String actId, StagingConfig config) {
        this.currentActId = actId;
        this.staging = config != null ? config : new StagingConfig();
        overrides.clear();
        revision++;
    }

    public String currentActId() {
        return currentActId;
    }

    /** Monotonic counter; changes whenever an override is written (or an act is staged). */
    public int revision() {
        return revision;
    }

    // --- resolution ---------------------------------------------------------------------------

    /**
     * Everything staged on {@code mapId} right now: actors, obstacles, items and triggers, with all
     * conditions and overrides applied. Props are excluded (the map owns those; see
     * {@link #propOverride}).
     */
    public List<StagedPlacement> stagedFor(String mapId) {
        List<StagedPlacement> out = new ArrayList<>();
        if (mapId == null) return out;

        for (StagedPlacement p : resolveCast()) {
            if (mapId.equals(p.mapId())) out.add(p);
        }
        for (ObstacleEntry e : staging.obstacles) {
            if (e.id == null || !present(e)) continue;
            // Solved content stays solved: a cleared obstacle never comes back.
            if (e.cleared_when != null && flags.hasFlag(e.cleared_when)) continue;
            StagedOverride o = overrides.get(e.id);
            if (o != null && o.removed) continue;
            // Filter on the RESOLVED map, not the declared one, so an override that relocates an
            // obstacle actually moves it rather than leaving a ghost on its original map.
            String map = mapOf(e, o);
            if (!mapId.equals(map)) continue;
            String activityType = e.activity != null ? e.activity.type : null;
            String activityId = e.activity != null ? e.activity.id : null;
            out.add(StagedPlacement.obstacle(e.id, map, markerOf(e, o),
                    dialogueOf(e.dialogue, o), solidOf(e, o), e.color, activityType, activityId));
        }
        for (ItemEntry e : staging.items) {
            if (e.itemId == null || !onMap(e, mapId) || !present(e)) continue;
            out.add(StagedPlacement.item(e.itemId, e.at.map, e.at.marker));
        }
        for (TriggerEntry e : staging.triggers) {
            if (!onMap(e, mapId) || !present(e)) continue;
            out.add(StagedPlacement.trigger(e.id, e.at.map, e.at.marker,
                    e.set_flag, e.require_flag, e.fire_once, e.event));
        }
        return out;
    }

    /**
     * Resolves the cast across all maps: for each actor the <em>last</em> matching entry wins, then
     * overrides are applied. An actor Ink placed who has no matching entry is included too, so a
     * character can be introduced mid-act without a staging rule for them.
     */
    private List<StagedPlacement> resolveCast() {
        Map<String, CastEntry> winning = new LinkedHashMap<>();
        for (CastEntry e : staging.cast) {
            if (e.actor == null) continue;
            if (present(e)) {
                winning.put(e.actor, e);
            } else {
                // A later non-matching entry must not resurrect an earlier match, but it must not
                // erase one either: only a matching entry replaces the incumbent.
                winning.putIfAbsent(e.actor, null);
            }
        }

        List<StagedPlacement> out = new ArrayList<>();
        for (Map.Entry<String, CastEntry> pair : winning.entrySet()) {
            String actorId = pair.getKey();
            CastEntry e = pair.getValue();
            StagedOverride o = overrides.get(actorId);
            if (o != null && o.removed) continue;
            // No matching rule and no override placement: this actor simply isn't in the world.
            if (e == null && (o == null || o.mapId == null)) continue;

            ActorDef def = actors.get(actorId);
            String map = e != null ? mapOf(e, o) : o.mapId;
            String marker = e != null ? markerOf(e, o) : o.marker;
            if (map == null) continue;
            out.add(StagedPlacement.actor(actorId, map, marker,
                    dialogueOf(e != null ? e.dialogue : null, o),
                    e != null ? solidOf(e, o) : (o.solid != null && o.solid),
                    def != null ? def.color : null,
                    def != null ? def.sprite : null));
        }

        // Overrides may name an actor the staging file never mentions at all.
        for (StagedOverride o : overrides.values()) {
            if (o.removed || o.mapId == null || winning.containsKey(o.id)) continue;
            ActorDef def = actors.get(o.id);
            if (def == null) continue; // not an actor: an obstacle/prop override, handled elsewhere
            out.add(StagedPlacement.actor(o.id, o.mapId, o.marker, o.dialogue,
                    o.solid != null && o.solid, def.color, def.sprite));
        }
        return out;
    }

    /**
     * The per-act override for a map-declared prop, or null if this act says nothing about it. Lets
     * an act re-voice (or silence) furniture whose knot only exists in another act's story.
     */
    public StagedPlacement propOverride(String propName) {
        if (propName == null) return null;
        PropEntry winner = null;
        for (PropEntry e : staging.props) {
            if (propName.equals(e.prop) && present(e)) winner = e;
        }
        StagedOverride o = overrides.get(propName);
        if (winner == null && o == null) return null;
        if (o != null && o.removed) return StagedPlacement.prop(propName, null, false);
        boolean solid = winner != null ? solidOf(winner, o) : (o.solid != null && o.solid);
        return StagedPlacement.prop(propName, dialogueOf(winner != null ? winner.dialogue : null, o), solid);
    }

    // --- condition helpers --------------------------------------------------------------------

    /** Whether an entry's presence conditions hold: every {@code when} set, no {@code unless} set. */
    private boolean present(Entry e) {
        return allSet(e.when) && noneSet(e.unless);
    }

    private boolean onMap(Entry e, String mapId) {
        return e.at != null && mapId.equals(e.at.map);
    }

    /**
     * Solidity is re-derived on every refresh rather than baked in at spawn, so a story beat (or an
     * explicit {@code set_solid}) can open or bar a path without reloading the map.
     */
    private boolean solidOf(Entry e, StagedOverride o) {
        if (o != null && o.solid != null) return o.solid;
        return e.solid && allSet(e.solid_when) && noneSet(e.solid_unless);
    }

    private String mapOf(Entry e, StagedOverride o) {
        if (o != null && o.mapId != null) return o.mapId;
        return e.at != null ? e.at.map : null;
    }

    private String markerOf(Entry e, StagedOverride o) {
        if (o != null && o.mapId != null) return o.marker;
        return e.at != null ? e.at.marker : null;
    }

    private static String dialogueOf(String declared, StagedOverride o) {
        return o != null && o.dialogue != null ? o.dialogue : declared;
    }

    private boolean allSet(List<String> keys) {
        if (keys == null) return true;
        for (String k : keys) {
            if (k != null && !flags.hasFlag(k)) return false;
        }
        return true;
    }

    private boolean noneSet(List<String> keys) {
        if (keys == null) return true;
        for (String k : keys) {
            if (k != null && flags.hasFlag(k)) return false;
        }
        return true;
    }

    // --- imperative overrides (the Ink seam) --------------------------------------------------

    private StagedOverride overrideFor(String id) {
        return overrides.computeIfAbsent(id, StagedOverride::new);
    }

    /** Moves (or introduces) something at {@code marker} on {@code mapId}. Undoes a prior removal. */
    public void place(String id, String mapId, String marker) {
        if (id == null || mapId == null) return;
        StagedOverride o = overrideFor(id);
        o.mapId = mapId;
        o.marker = marker;
        o.removed = false;
        revision++;
        Log.debug("StageDirector", "placed '" + id + "' at " + mapId + ":" + marker);
    }

    /** Takes something out of the world for the rest of the act. */
    public void remove(String id) {
        if (id == null) return;
        overrideFor(id).removed = true;
        revision++;
        Log.debug("StageDirector", "removed '" + id + "' from the world");
    }

    /** Re-voices something: subsequent interactions run {@code knot} instead of the declared one. */
    public void setDialogue(String id, String knot) {
        if (id == null) return;
        overrideFor(id).dialogue = knot;
        revision++;
    }

    /** Explicitly bars or opens a path, overriding the declared solidity and its conditions. */
    public void setSolid(String id, boolean solid) {
        if (id == null) return;
        overrideFor(id).solid = solid;
        revision++;
        Log.debug("StageDirector", "'" + id + "' is now " + (solid ? "solid" : "passable"));
    }

    /** Whether {@code id} is currently staged on {@code mapId} (the Ink read). */
    public boolean isOn(String id, String mapId) {
        if (id == null || mapId == null) return false;
        for (StagedPlacement p : stagedFor(mapId)) {
            if (id.equals(p.id())) return true;
        }
        return false;
    }

    // --- save / restore -----------------------------------------------------------------------

    /** The durable half of the state: only the overrides (declarative placement is re-derived). */
    public Collection<StagedOverride> overrides() {
        return overrides.values();
    }

    public void restoreOverrides(Collection<StagedOverride> saved) {
        overrides.clear();
        if (saved != null) {
            for (StagedOverride o : saved) {
                if (o != null && o.id != null) overrides.put(o.id, o);
            }
        }
        revision++;
    }

    /** Drops all staging state (a fresh playthrough); {@link #enterAct} repopulates. */
    public void reset() {
        staging = new StagingConfig();
        overrides.clear();
        currentActId = null;
        revision++;
    }
}
