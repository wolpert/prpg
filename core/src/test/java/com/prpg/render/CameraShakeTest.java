package com.prpg.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CameraShakeTest {

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    private CameraShake shake;
    private OrthographicCamera camera;

    @BeforeEach
    void setUp() {
        shake = new CameraShake();
        camera = new OrthographicCamera(800f, 600f);
        camera.position.set(400f, 300f, 0f);
        camera.update();
    }

    @Test
    void inactiveByDefault() {
        assertFalse(shake.isActive());
        shake.apply(camera, 0.016f);
        // No trigger — camera untouched.
        assertEquals(400f, camera.position.x, 1e-6f);
        assertEquals(300f, camera.position.y, 1e-6f);
    }

    @Test
    void triggerActivatesAndDecays() {
        shake.trigger(8f, 0.1f);
        assertTrue(shake.isActive());

        shake.apply(camera, 0.05f); // halfway through
        assertTrue(shake.isActive());
        // Camera offset should be within magnitude bounds.
        float dx = Math.abs(camera.position.x - 400f);
        float dy = Math.abs(camera.position.y - 300f);
        assertTrue(dx <= 8f, "x offset within magnitude: " + dx);
        assertTrue(dy <= 8f, "y offset within magnitude: " + dy);
    }

    @Test
    void restoresOriginAfterDuration() {
        shake.trigger(8f, 0.1f);
        shake.apply(camera, 0.05f);  // mid-shake
        shake.apply(camera, 0.06f);  // pushes remaining below zero — should restore

        assertFalse(shake.isActive());
        assertEquals(400f, camera.position.x, 1e-5f);
        assertEquals(300f, camera.position.y, 1e-5f);
    }

    @Test
    void zeroOrNegativeMagnitudeIsNoOp() {
        shake.trigger(0f, 0.1f);
        assertFalse(shake.isActive());
        shake.trigger(-1f, 0.1f);
        assertFalse(shake.isActive());
    }

    @Test
    void zeroOrNegativeDurationIsNoOp() {
        shake.trigger(8f, 0f);
        assertFalse(shake.isActive());
    }

    @Test
    void retriggerReplacesPriorShake() {
        shake.trigger(2f, 0.1f);
        shake.apply(camera, 0.05f); // capture base
        shake.trigger(8f, 0.1f); // replace
        // Drain — should still restore to (400, 300), not whatever the mid-shake offset was.
        shake.apply(camera, 0.2f);
        assertEquals(400f, camera.position.x, 1e-5f);
        assertEquals(300f, camera.position.y, 1e-5f);
    }
}
