package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** Something the player can interact with; interacting runs {@link #knot} in the current act's story. */
public class InteractableComponent implements Component, Pool.Poolable {
    /** The staged id (for logs). */
    public String id;
    /** The Ink knot to run. */
    public String knot;

    @Override
    public void reset() {
        id = null;
        knot = null;
    }
}
