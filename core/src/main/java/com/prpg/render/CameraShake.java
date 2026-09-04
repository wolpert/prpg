package com.prpg.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Generic, reusable screen-shake utility. Decoupled from any particular camera or game system —
 * call {@link #trigger(float, float)} from anywhere (e.g. a contact listener), then call
 * {@link #apply(OrthographicCamera, float)} once per frame from your render loop. The camera's
 * pre-shake position is captured on the first {@code apply} call after a {@code trigger} and
 * restored when the shake decays to zero.
 *
 * <p>Lives in scaffold so any game can reuse it; the dodge sample is one consumer.
 */
@Singleton
public class CameraShake {

    private float magnitudePx;
    private float remainingSec;
    private float totalSec;

    /** True only while the shake is offsetting the camera; used to gate the position capture/restore. */
    private boolean active;
    private float baseCameraX;
    private float baseCameraY;

    @Inject
    public CameraShake() {}

    /**
     * Start (or restart) a shake. Calling {@code trigger} during an existing shake replaces it
     * — fine for hit feedback where back-to-back hits should reset, not stack.
     */
    public void trigger(float magnitudePx, float durationSec) {
        if (durationSec <= 0f || magnitudePx <= 0f) return;
        this.magnitudePx = magnitudePx;
        this.remainingSec = durationSec;
        this.totalSec = durationSec;
    }

    /**
     * Apply the current shake to {@code camera}: random x/y offset that decays linearly with
     * remaining time. Restores the captured pre-shake camera origin once the shake elapses.
     * Safe to call every frame regardless of whether a shake is active.
     */
    public void apply(OrthographicCamera camera, float dt) {
        if (remainingSec <= 0f) {
            if (active) {
                camera.position.x = baseCameraX;
                camera.position.y = baseCameraY;
                camera.update();
                active = false;
            }
            return;
        }
        if (!active) {
            baseCameraX = camera.position.x;
            baseCameraY = camera.position.y;
            active = true;
        }
        remainingSec -= dt;
        float t = MathUtils.clamp(remainingSec / totalSec, 0f, 1f);
        float mag = magnitudePx * t;
        camera.position.x = baseCameraX + MathUtils.random(-mag, mag);
        camera.position.y = baseCameraY + MathUtils.random(-mag, mag);
        camera.update();
        if (remainingSec <= 0f) {
            // Restore on the same frame the shake elapses so render uses the true origin.
            camera.position.x = baseCameraX;
            camera.position.y = baseCameraY;
            camera.update();
            active = false;
        }
    }

    /** True iff a shake is currently being applied. */
    public boolean isActive() {
        return remainingSec > 0f || active;
    }
}
