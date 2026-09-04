package com.prpg.world.scene;

/**
 * A scripted story beat (cutscene) triggered by a TMX trigger's {@code event} property. Events are
 * registered into a {@code Map<String, ScriptedEvent>} via Dagger {@code @IntoMap @StringKey} and
 * run by {@link SceneDirector}, so authors add new beats in data + a small handler class rather
 * than branching inside {@code WorldScreen}.
 */
public interface ScriptedEvent {

    /** Begin the event. Use {@code director} to drive the shared fade veil. */
    void begin(SceneDirector director);

    /** Advance one frame; return true while still running, false when finished. */
    boolean update(float delta);
}
