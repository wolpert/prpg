package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class OrientationComponent implements Component, Pool.Poolable {

    public enum Direction { DOWN, UP, LEFT, RIGHT }

    public Direction direction = Direction.DOWN;

    @Override
    public void reset() {
        direction = Direction.DOWN;
    }
}
