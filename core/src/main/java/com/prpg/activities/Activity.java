package com.prpg.activities;

/**
 * A minigame or interaction launched from the world by a string {@code type} (the key it is
 * registered under) and an {@code activityId} (a YAML definition in {@code activities/<type>/}).
 * Two presentations exist: a {@link FullScreenActivity} replaces the world screen until it returns;
 * a {@link PopupActivity} opens over the world and closes in place. {@link ActivityLauncher}
 * dispatches to whichever registry holds the type, so content never knows the difference.
 */
public interface Activity {

    /** Loads the definition and resets the instance. Called before the activity is shown/opened. */
    void launch(String activityId);

    /** Returns and clears any dialogue knot queued by a just-completed activity's on_complete. */
    String consumePendingDialogue();
}
