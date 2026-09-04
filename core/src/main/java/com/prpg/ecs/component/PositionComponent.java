package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class PositionComponent implements Component, Pool.Poolable {
    public float x;
    public float y;
    /** Render layer; lower values draw first (further back). */
    public int z;
    /** Rotation in degrees, applied around the texture's center by RenderSystem. */
    public float angle;

    @Override
    public void reset() {
        x = 0f;
        y = 0f;
        z = 0;
        angle = 0f;
    }
}
