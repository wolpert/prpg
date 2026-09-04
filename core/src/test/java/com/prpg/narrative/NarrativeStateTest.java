package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure unit tests for the canonical model (no Ink, no libGDX). */
class NarrativeStateTest {

    @Test
    void startsWithNoActAndNothingUnlocked() {
        NarrativeState state = new NarrativeState();
        assertNull(state.getCurrentActId());
        assertFalse(state.isActUnlocked("act1"));
    }

    @Test
    void unlockAndResetRoundTrip() {
        NarrativeState state = new NarrativeState();
        state.setCurrentActId("act1");
        state.unlockAct("act1");
        state.unlockAct("act2");
        state.unlockAct(null); // ignored
        assertTrue(state.isActUnlocked("act2"));

        state.reset();

        assertNull(state.getCurrentActId());
        assertTrue(state.getUnlockedActs().isEmpty());
    }
}
