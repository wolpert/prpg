package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** A map-owned walk-in exit to another map. The story can disable it with {@code lock(name)}. */
public class PortalComponent implements Component, Pool.Poolable {
    /** The portal object's name in the TMX (what {@code lock()} refers to). */
    public String name;
    public String targetMap;
    public String targetSpawn;

    @Override
    public void reset() {
        name = null;
        targetMap = null;
        targetSpawn = null;
    }
}
