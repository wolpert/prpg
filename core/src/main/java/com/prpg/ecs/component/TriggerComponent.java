package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** A walk-in zone the story staged with {@code zone(...)}: entering it runs {@link #knot}. */
public class TriggerComponent implements Component, Pool.Poolable {
    /** The zone id the story used (for logs). */
    public String id;
    /** The Ink knot to run when the player walks in. */
    public String knot;

    @Override
    public void reset() {
        id = null;
        knot = null;
    }
}
