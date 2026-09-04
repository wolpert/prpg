package com.prpg.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.graphics.Color;
import com.prpg.ecs.component.TextureComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TintFlashTest {

    private TintFlash system;
    private Engine engine;
    private Entity entity;
    private TextureComponent tex;

    @BeforeEach
    void setUp() {
        engine = new PooledEngine();
        system = new TintFlash();
        engine.addSystem(system);
        entity = new Entity();
        tex = new TextureComponent();
        entity.add(tex);
        engine.addEntity(entity);
    }

    @Test
    void flashAppliesColorImmediately() {
        system.flash(entity, Color.RED, 0.15f);
        assertEquals(Color.RED.r, tex.tint.r, 1e-5f);
        assertEquals(Color.RED.g, tex.tint.g, 1e-5f);
        assertEquals(Color.RED.b, tex.tint.b, 1e-5f);
    }

    @Test
    void tintLerpsTowardWhiteOverTime() {
        system.flash(entity, Color.RED, 0.1f);
        engine.update(0.05f); // halfway

        // The red channel should still be ~1, but green/blue should have lifted from 0.
        assertTrue(tex.tint.g > 0f && tex.tint.g < 1f, "green channel mid-lerp: " + tex.tint.g);
        assertTrue(tex.tint.b > 0f && tex.tint.b < 1f);
    }

    @Test
    void tintRestoresToWhiteAfterDuration() {
        system.flash(entity, Color.RED, 0.1f);
        engine.update(0.05f);
        engine.update(0.1f); // past the duration

        assertEquals(1f, tex.tint.r, 1e-5f);
        assertEquals(1f, tex.tint.g, 1e-5f);
        assertEquals(1f, tex.tint.b, 1e-5f);
        assertEquals(1f, tex.tint.a, 1e-5f);
    }

    @Test
    void flashOnEntityWithoutTextureIsSafe() {
        Entity bare = new Entity();
        engine.addEntity(bare);
        // Should not throw — the entity won't be in the family view but we still record the flash.
        system.flash(bare, Color.RED, 0.1f);
        engine.update(0.05f);
        engine.update(0.1f);
    }

    @Test
    void zeroDurationIsNoOp() {
        tex.tint.set(Color.WHITE);
        system.flash(entity, Color.RED, 0f);
        // No flash recorded — tint stays white.
        assertEquals(1f, tex.tint.r, 0f);
    }
}
