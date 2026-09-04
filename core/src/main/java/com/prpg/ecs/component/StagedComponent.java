package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marks an entity as act-staged content (placed by {@code StageDirector}, not by the map). Lets the
 * world re-derive its population — after a flag change, an Ink move, or a solved activity — by removing
 * and re-spawning exactly these entities, leaving the player and the map alone.
 */
public class StagedComponent implements Component, Pool.Poolable {
    /** The staged id this entity was built from (actor id, obstacle id, trigger id, item id). */
    public String id;

    @Override
    public void reset() {
        id = null;
    }
}
