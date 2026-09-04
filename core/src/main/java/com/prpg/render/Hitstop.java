package com.prpg.render;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Generic hitstop / freeze-frame utility. Call {@link #freeze(float)} to request a brief pause —
 * e.g. 60–80ms on a hit — and the game-loop driver multiplies its per-frame {@code delta} by
 * {@link #getEngineDeltaScale()} before passing it to the ECS engine. While frozen, the scale is
 * 0 (so physics, animation, and movement systems see a zero delta and don't advance); otherwise
 * it's 1.
 *
 * <p>The screen is still expected to render every frame — only ECS time stops, the GPU clock
 * doesn't. The remaining freeze time is consumed using the <em>real</em> wall-clock delta supplied
 * to {@link #tick(float)}, so the freeze always ends after the requested duration regardless of
 * frame rate.
 */
@Singleton
public class Hitstop {

    private float remainingSec;

    @Inject
    public Hitstop() {}

    /**
     * Start (or extend) a freeze. Back-to-back triggers take the max so a short freeze can't
     * cancel a longer pending one.
     */
    public void freeze(float durationSec) {
        if (durationSec <= 0f) return;
        if (durationSec > remainingSec) {
            remainingSec = durationSec;
        }
    }

    /**
     * Returns the multiplier to apply to the engine delta this frame: 0 while frozen, 1 otherwise.
     */
    public float getEngineDeltaScale() {
        return remainingSec > 0f ? 0f : 1f;
    }

    /**
     * Consume real wall-clock delta. Call this once per frame with the unscaled delta — the
     * remaining freeze time decreases regardless of {@link #getEngineDeltaScale()}.
     */
    public void tick(float realDelta) {
        if (remainingSec > 0f) {
            remainingSec -= realDelta;
            if (remainingSec < 0f) remainingSec = 0f;
        }
    }

    public boolean isActive() {
        return remainingSec > 0f;
    }
}
