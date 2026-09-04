package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class CollisionComponent implements Component, Pool.Poolable {
    public float w;
    public float h;
    public float offsetX;
    public float offsetY;

    @Override
    public void reset() {
        w = 0f;
        h = 0f;
        offsetX = 0f;
        offsetY = 0f;
    }
}
