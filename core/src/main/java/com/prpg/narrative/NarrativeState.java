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
 * <p>Everything a story needs to remember is an Ink variable (kept by name in
 * {@link StoryVariables}); this model holds only the act spine, which Ink reaches through
 * {@link StateBridge} ({@code unlock_act}, {@code advance_act}, {@code current_act}). Add fields here
 * only for state that genuinely needs Java-typed structure.
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
