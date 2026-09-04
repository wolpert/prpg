package com.prpg.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HitstopTest {

    private Hitstop hitstop;

    @BeforeEach
    void setUp() {
        hitstop = new Hitstop();
    }

    @Test
    void inactiveByDefault() {
        assertFalse(hitstop.isActive());
        assertEquals(1f, hitstop.getEngineDeltaScale(), 0f);
    }

    @Test
    void freezeActivatesAndScaleGoesToZero() {
        hitstop.freeze(0.1f);
        assertTrue(hitstop.isActive());
        assertEquals(0f, hitstop.getEngineDeltaScale(), 0f);
    }

    @Test
    void tickConsumesRemainingTime() {
        hitstop.freeze(0.1f);
        hitstop.tick(0.05f);
        assertTrue(hitstop.isActive(), "halfway through, still frozen");
        hitstop.tick(0.06f);
        assertFalse(hitstop.isActive());
        assertEquals(1f, hitstop.getEngineDeltaScale(), 0f);
    }

    @Test
    void backToBackFreezeKeepsLongerDuration() {
        hitstop.freeze(0.2f);
        hitstop.freeze(0.05f); // shorter — should not shorten
        hitstop.tick(0.1f);
        assertTrue(hitstop.isActive(), "longer freeze should still be active");
    }

    @Test
    void backToBackFreezeExtendsToLongerDuration() {
        hitstop.freeze(0.05f);
        hitstop.freeze(0.2f); // longer — should extend
        hitstop.tick(0.06f);
        assertTrue(hitstop.isActive());
    }

    @Test
    void zeroOrNegativeFreezeIsNoOp() {
        hitstop.freeze(0f);
        assertFalse(hitstop.isActive());
        hitstop.freeze(-0.1f);
        assertFalse(hitstop.isActive());
    }
}
