package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marks an entity as story-staged content (placed by the act's Ink {@code stage()}, not by the map).
 * Lets the world re-derive its population whenever the story moves on, by removing and re-spawning
 * exactly these entities, leaving the player and the map alone.
 */
public class StagedComponent implements Component, Pool.Poolable {
    /** The staged id this entity was built from (actor, thing or zone id). */
    public String id;

    @Override
    public void reset() {
        id = null;
    }
}
