package com.prpg.narrative;

import com.prpg.narrative.config.NarrativeManifest.ActEntry;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.util.Log;
import com.prpg.world.stage.StageDirector;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The act-progression state machine: a one-way walk along the catalog's {@code order} with
 * data-driven gates. One transition distinguishes every reason a player can be held at an act
 * boundary ({@link GateResult}), so narrative gating, the paywall and "DLC not downloaded" share one
 * mechanism.
 *
 * <p>Holds no state of its own: {@code currentAct} lives in {@link NarrativeState} (the durable
 * model) and the spine lives in {@link ActContentRegistry} (the catalog).
 */
@Singleton
public class ActProgression {

    private final NarrativeState state;
    private final Entitlement entitlement;
    private final ActContentRegistry registry;
    private final StageDirector stage;

    @Inject
    public ActProgression(NarrativeState state, Entitlement entitlement, ActContentRegistry registry,
                          StageDirector stage) {
        this.state = state;
        this.entitlement = entitlement;
        this.registry = registry;
        this.stage = stage;
    }

    /** Starts a fresh playthrough at the catalog's first act (unlocked, current, staged). */
    public void beginNewGame() {
        state.reset();
        stage.reset();
        String first = registry.firstActId();
        if (first == null) {
            Log.error("ActProgression", "no acts declared in any pack; nothing to play");
            return;
        }
        state.unlockAct(first);
        state.setCurrentActId(first);
        stage.enterAct(first);
        Log.info("ActProgression", "new game at act '" + first + "'");
    }

    public String currentActId() {
        return state.getCurrentActId();
    }

    public ActEntry currentAct() {
        return registry.entry(state.getCurrentActId());
    }

    /** The act that would follow the current one, or null at the end of the spine. */
    public String nextActId() {
        return registry.nextActId(state.getCurrentActId());
    }

    /** Whether {@code actId} can be entered now: narratively unlocked, owned, and installed. */
    public boolean canEnter(String actId) {
        return actId != null && state.isActUnlocked(actId) && entitlement.owns(actId)
                && registry.isInstalled(actId);
    }

    /**
     * Evaluates the gates for the next act without changing anything. Ink reads this through the
     * bridge so a conversation can explain <em>why</em> the road is shut (paywall vs. not installed).
     */
    public GateResult evaluateAdvance() {
        String next = nextActId();
        if (next == null) return GateResult.COMPLETE;
        if (!state.isActUnlocked(next)) return GateResult.BLOCKED_NARRATIVE;
        if (!entitlement.owns(next)) return GateResult.BLOCKED_NOT_OWNED;
        if (!registry.isInstalled(next)) return GateResult.BLOCKED_NOT_INSTALLED;
        return GateResult.ADVANCED;
    }

    /**
     * Attempts to advance to the next act. Only mutates state on {@link GateResult#ADVANCED}: the
     * current act changes and the new act's staging becomes authoritative (the previous act's
     * imperative overrides are dropped, so a DLC act can run standalone). {@code WorldScreen} notices
     * the act change and moves the player to the new act's entry map.
     */
    public GateResult requestAdvance() {
        GateResult result = evaluateAdvance();
        String next = nextActId();
        if (result != GateResult.ADVANCED) {
            Log.debug("ActProgression", "advance to '" + next + "' blocked: " + result);
            return result;
        }
        state.setCurrentActId(next);
        stage.enterAct(next);
        Log.info("ActProgression", "advanced to act '" + next + "'");
        return result;
    }
}
