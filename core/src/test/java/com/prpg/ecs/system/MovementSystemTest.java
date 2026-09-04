package com.prpg.ecs.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.ecs.component.VelocityComponent;
import org.junit.jupiter.api.Test;

class MovementSystemTest {

    @Test
    void integratesVelocityAcrossDeltaTime() {
        PositionComponent pos = new PositionComponent();
        pos.x = 100f;
        pos.y = 200f;
        VelocityComponent vel = new VelocityComponent();
        vel.dx = 50f;
        vel.dy = -30f;

        Entity entity = new Entity();
        entity.add(pos);
        entity.add(vel);

        Engine engine = new PooledEngine();
        engine.addSystem(new MovementSystem());
        engine.addEntity(entity);

        engine.update(0.5f);

        assertEquals(125f, pos.x, 1e-6, "x = 100 + 50 * 0.5");
        assertEquals(185f, pos.y, 1e-6, "y = 200 + (-30) * 0.5");

        engine.update(2f);

        assertEquals(225f, pos.x, 1e-6, "x = 125 + 50 * 2");
        assertEquals(125f, pos.y, 1e-6, "y = 185 + (-30) * 2");
    }
}
