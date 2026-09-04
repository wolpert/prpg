package com.prpg.narrative;

/**
 * Outcome of an attempted act transition. One transition distinguishes three user-facing situations —
 * "the story isn't there yet", "this is paid", and "this DLC isn't downloaded" — so the same gate
 * machinery drives both narrative gating and entitlement/DLC gating.
 */
public enum GateResult {
    /** Advanced into the next act. */
    ADVANCED,
    /** Next act's narrative gate isn't crossed yet (keep playing the current act). */
    BLOCKED_NARRATIVE,
    /** Next act is reached in the story but not owned — show the paywall (the Act II gate). */
    BLOCKED_NOT_OWNED,
    /** Next act is owned but its DLC content isn't installed — offer to download/restore. */
    BLOCKED_NOT_INSTALLED,
    /** There is no further act (already at the Epilogue). */
    COMPLETE
}
