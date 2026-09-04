package com.prpg.activities.config;

/**
 * What an activity does when solved. Shared by every activity definition so content authors learn
 * one shape, and applied by {@code OnCompleteApplier} so every activity behaves the same way.
 */
public class OnCompleteConfig {
    /** Flag set on completion; also what makes the reward one-time (see OnCompleteApplier). */
    public String set_flag;
    /** Item granted the first time only. Pair with {@link #set_flag} or it repeats. */
    public String give_item;
    /** Ink knot (in the current act's story) barked when the world resumes, first time only. */
    public String dialogue;
}
