package com.prpg.world.scene;

import com.prpg.util.Log;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Runs at most one {@link ScriptedEvent} at a time and exposes a shared full-screen fade level the
 * active screen renders. Events are looked up by id from the Dagger multibound map, so triggering a
 * cutscene is {@code director.trigger("margaret_sighting")} with no compile-time coupling to the
 * event class.
 */
@Singleton
public class SceneDirector {

    private final Map<String, ScriptedEvent> events;

    private ScriptedEvent active;
    private float fadeAlpha;

    @Inject
    public SceneDirector(Map<String, ScriptedEvent> events) {
        this.events = events;
    }

    public boolean has(String eventId) {
        return events.containsKey(eventId);
    }

    /** Starts the named event if one isn't already running and the id is registered. */
    public boolean trigger(String eventId) {
        if (active != null) return false;
        ScriptedEvent event = events.get(eventId);
        if (event == null) {
            Log.info("SceneDirector", "no scripted event registered for id '" + eventId + "'; cutscene skipped");
            return false;
        }
        active = event;
        fadeAlpha = 0f;
        event.begin(this);
        return true;
    }

    public void update(float delta) {
        if (active == null) return;
        if (!active.update(delta)) {
            active = null;
            fadeAlpha = 0f;
        }
    }

    public boolean isActive() {
        return active != null;
    }

    /** Current veil opacity [0,1] for the active screen to render. */
    public float fadeAlpha() {
        return fadeAlpha;
    }

    /** Called by the active event to drive the shared fade veil. */
    public void setFade(float alpha) {
        this.fadeAlpha = alpha;
    }
}
