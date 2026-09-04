package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class InteractableComponent implements Component, Pool.Poolable {
    public String type;
    public String id;

    @Override
    public void reset() {
        type = null;
        id = null;
    }
}
