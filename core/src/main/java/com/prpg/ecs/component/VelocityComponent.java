package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** Linear velocity in pixels-per-second, integrated by {@code MovementSystem}. */
public class VelocityComponent implements Component, Pool.Poolable {
    public float dx;
    public float dy;

    @Override
    public void reset() {
        dx = 0f;
        dy = 0f;
    }
}
