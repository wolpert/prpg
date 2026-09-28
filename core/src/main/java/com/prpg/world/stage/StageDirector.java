package com.prpg.world.stage;

import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.StoryCall;
import com.prpg.narrative.StoryVariables;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Answers "what is in the world right now" by asking the current act's story. The act's Ink declares
 * two functions the engine evaluates:
 *
 * <pre>
 * === function cast() ===                 // what each id looks like
 * ~ define("keeper", "npc", "C06A2E")     // id, sprite ("" for none), fallback colour
 *
 * === function stage() ===                // who and what is where, given the story so far
 * { hedge_cleared:
 *     ~ actor("keeper", "gatehouse_hall", "keeper_desk", -> keeper_inside)
 * - else:
 *     ~ actor("keeper", "gatehouse_yard", "keeper_post", -> keeper_greeting)
 *     ~ thing("hedge", "gatehouse_yard", "hall_door", -> hedge, true)
 * }
 * ~ zone("gate_arch", "gatehouse_yard", "gate_arch", -> gate_arch)
 * ~ lock("yard_door")                     // a portal (by its TMX name) that does not work
 * </pre>
 *
 * <p>Placement is a pure function of story state, so nothing about it is saved: the functions are
 * re-evaluated whenever story variables change, the act changes, or a conversation moves on (Ink
 * visit counts can feed a condition too). If the same id is placed twice, the last call wins.
 * Placements name a map and a marker, never coordinates, so re-laying-out a map never breaks a story.
 */
@Singleton
public class StageDirector {

    public static final String STAGE_FUNCTION = "stage";
    public static final String CAST_FUNCTION = "cast";

    /** What an id looks like, from {@code cast()}. */
    public record Look(String sprite, String color) {}

    private final NarrativeRunner runner;
    private final NarrativeState state;
    private final StoryVariables variables;

    private String resolvedAct;
    private int resolvedVersion = -1;
    private int resolvedTurn = -1;
    private boolean stale = true;

    private List<StagedPlacement> placements = List.of();
    private Set<String> lockedPortals = Set.of();

    /** Bumped only when the resolved world actually differs, so the world rebuilds only on change. */
    private int revision;

    @Inject
    public StageDirector(NarrativeRunner runner, NarrativeState state, StoryVariables variables) {
        this.runner = runner;
        this.state = state;
        this.variables = variables;
    }

    /** Everything staged on {@code mapId} right now. */
    public List<StagedPlacement> stagedFor(String mapId) {
        resolve();
        List<StagedPlacement> out = new ArrayList<>();
        if (mapId == null) return out;
        for (StagedPlacement p : placements) {
            if (mapId.equals(p.mapId())) out.add(p);
        }
        return out;
    }

    /** Every placement across all maps (tests and debugging). */
    public List<StagedPlacement> all() {
        resolve();
        return Collections.unmodifiableList(placements);
    }

    /** Whether the story {@code lock()}ed the portal with this TMX object name. */
    public boolean isPortalLocked(String portalName) {
        resolve();
        return portalName != null && lockedPortals.contains(portalName);
    }

    /** Changes whenever the staged world changes; cheap to poll every frame. */
    public int revision() {
        resolve();
        return revision;
    }

    /** Forces the next query to re-ask the story (a new game or a loaded save). */
    public void invalidate() {
        stale = true;
    }

    // --- resolution ---------------------------------------------------------------------------

    private void resolve() {
        String act = state.getCurrentActId();
        int version = variables.version();
        int turn = runner.turn();
        if (!stale && Objects.equals(act, resolvedAct) && version == resolvedVersion && turn == resolvedTurn) {
            return;
        }
        stale = false;
        resolvedAct = act;
        resolvedVersion = version;
        resolvedTurn = turn;

        Map<String, Look> looks = new LinkedHashMap<>();
        Map<String, StagedPlacement> byId = new LinkedHashMap<>();
        Set<String> locked = new LinkedHashSet<>();
        if (act != null) {
            runner.evaluate(act, CAST_FUNCTION, call -> collectLook(act, call, looks));
            runner.evaluate(act, STAGE_FUNCTION, call -> collectPlacement(act, call, looks, byId, locked));
        }

        List<StagedPlacement> next = new ArrayList<>(byId.values());
        if (!next.equals(placements) || !locked.equals(lockedPortals)) {
            placements = next;
            lockedPortals = locked;
            revision++;
            Log.debug("StageDirector", "act '" + act + "' staged " + next.size()
                    + " placement(s), " + locked.size() + " locked portal(s)");
        }
    }

    private static void collectLook(String act, StoryCall call, Map<String, Look> looks) {
        if (!"define".equals(call.function())) {
            Log.info("StageDirector", act + " cast() called " + call.function()
                    + "(...); only define(id, sprite, color) belongs there");
            return;
        }
        String id = call.arg(0);
        if (id == null) {
            Log.info("StageDirector", act + " cast(): define() without an id; ignored");
            return;
        }
        looks.put(id, new Look(call.arg(1), call.arg(2)));
    }

    private static void collectPlacement(String act, StoryCall call, Map<String, Look> looks,
                                         Map<String, StagedPlacement> byId, Set<String> locked) {
        String fn = call.function();
        if ("lock".equals(fn)) {
            if (call.arg(0) != null) locked.add(call.arg(0));
            return;
        }
        StagedPlacement.Kind kind = switch (fn) {
            case "actor" -> StagedPlacement.Kind.ACTOR;
            case "thing" -> StagedPlacement.Kind.THING;
            case "zone" -> StagedPlacement.Kind.ZONE;
            default -> null;
        };
        if (kind == null) {
            Log.info("StageDirector", act + " stage() called " + fn
                    + "(...); only actor/thing/zone/lock belong there");
            return;
        }
        String id = call.arg(0);
        String map = call.arg(1);
        String marker = call.arg(2);
        if (id == null || map == null || marker == null) {
            Log.info("StageDirector", act + " stage(): " + fn + call.args()
                    + " needs an id, a map and a marker; not staged");
            return;
        }
        Look look = looks.get(id);
        boolean solid = kind == StagedPlacement.Kind.THING && call.flag(4);
        byId.put(id, new StagedPlacement(kind, id, map, marker, call.arg(3), solid,
                look != null ? look.sprite() : null, look != null ? look.color() : null));
    }
}
