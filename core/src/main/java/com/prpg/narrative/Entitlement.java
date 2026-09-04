package com.prpg.narrative;

/**
 * Whether the player is entitled to an act's content (store receipt / purchase). The Act II paywall
 * is modelled as {@code owns("act3") == false} until purchase, so the same gate machinery serves
 * both narrative gating and entitlement gating (see {@code docs/content-guide.md} §1, §14).
 *
 * <p>An interface so tests inject a fake and the real, platform-specific receipt check
 * (Play Billing / desktop license) can be swapped in without touching the bridge or progression.
 */
public interface Entitlement {

    /** True if the player owns (is entitled to) the act with this stable id. */
    boolean owns(String actId);
}
