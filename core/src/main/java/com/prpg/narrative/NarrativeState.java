package com.prpg.narrative;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The durable, cross-act narrative model: which act is current and which acts have been unlocked.
 * Deliberately independent of any Ink {@code Story} (whose variable state is per-instance and
 * per-act) and of libGDX, so it serializes on its own (see {@code save/SaveData}) and is unit-testable
 * headless.
 *
 * <p>Ink stories are ephemeral: each act reads from and writes to this model, the {@code FlagStore}
 * and the {@code Inventory} through {@link StateBridge}. That is what lets any act, base or DLC, run
 * standalone with state handed in. Everything else a story needs to remember belongs in
 * {@code FlagStore} (flags and int counters), which is also saved; add fields here only for state
 * that genuinely needs Java-typed structure.
 *
 * <p>{@link #getCurrentActId()} is null until a game starts ({@code ActProgression.beginNewGame()}
 * or a save load sets it) because the first act is data, not a constant.
 */
@Singleton
public class NarrativeState {

    private String currentActId;
    private final Set<String> unlockedActs = new LinkedHashSet<>();

    @Inject
    public NarrativeState() {}

    public String getCurrentActId() {
        return currentActId;
    }

    public void setCurrentActId(String actId) {
        this.currentActId = actId;
    }

    public void unlockAct(String actId) {
        if (actId != null) unlockedActs.add(actId);
    }

    public boolean isActUnlocked(String actId) {
        return unlockedActs.contains(actId);
    }

    public Set<String> getUnlockedActs() {
        return unlockedActs;
    }

    /** Resets to "no game in progress". {@code ActProgression.beginNewGame()} then picks the first act. */
    public void reset() {
        currentActId = null;
        unlockedActs.clear();
    }
}
