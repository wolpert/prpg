package com.prpg.world.scene;

/**
 * A scripted effect (cutscene) a story runs with {@code >>> cutscene <id>}, for anything the story's
 * own commands can't express. Events are registered into a {@code Map<String, ScriptedEvent>} via
 * Dagger {@code @IntoMap @StringKey} and run by {@link SceneDirector}; the story waits until the
 * event finishes.
 */
public interface ScriptedEvent {

    /** Begin the event. Use {@code director} to drive the shared fade veil. */
    void begin(SceneDirector director);

    /** Advance one frame; return true while still running, false when finished. */
    boolean update(float delta);
}
