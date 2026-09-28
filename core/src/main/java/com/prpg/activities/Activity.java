package com.prpg.activities;

/**
 * A minigame launched by the story ({@code >>> play <type> <id>}) by a string {@code type} (the key
 * it is registered under) and an {@code activityId} (a tuning definition in
 * {@code activities/<type>/}). Two presentations exist: a {@link FullScreenActivity} replaces the
 * world screen until it returns; a {@link PopupActivity} opens over the world and closes in place.
 * {@link ActivityLauncher} dispatches to whichever registry holds the type, so content never knows
 * the difference.
 *
 * <p>An activity knows nothing about the story: it reports only whether it is still running and
 * whether the player won. The knot that played it decides what winning means.
 */
public interface Activity {

    /** Loads the definition and resets the instance. Called before the activity is shown/opened. */
    void launch(String activityId);

    /** True from {@link #launch} until the player leaves (solved, or backed out). */
    boolean isRunning();

    /** Whether the most recent run was solved. Meaningful once {@link #isRunning()} is false. */
    boolean wasWon();
}
