package com.prpg.activities;

import com.prpg.activities.config.OnCompleteConfig;
import com.prpg.items.Inventory;
import com.prpg.util.Log;
import com.prpg.world.FlagStore;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Applies an activity's {@link OnCompleteConfig} exactly the same way for every activity type:
 * the reward item and the bark fire only the <em>first</em> time (judged by {@code set_flag} not
 * already being set), then the flag is set. An activity with {@code give_item} but no
 * {@code set_flag} has no way to know it was solved before, so its reward repeats; the content
 * validation test flags that pairing.
 */
@Singleton
public class OnCompleteApplier {

    private final FlagStore flags;
    private final Inventory inventory;

    @Inject
    public OnCompleteApplier(FlagStore flags, Inventory inventory) {
        this.flags = flags;
        this.inventory = inventory;
    }

    /**
     * Applies {@code oc} for the activity {@code activityId}. Returns the dialogue knot to bark on
     * return to the world, or null when there is none (or this isn't the first completion).
     */
    public String apply(String activityId, OnCompleteConfig oc) {
        if (oc == null) return null;
        boolean firstTime = oc.set_flag == null || !flags.hasFlag(oc.set_flag);
        if (firstTime && oc.give_item != null) {
            boolean granted = inventory.add(oc.give_item, 1);
            if (!granted) {
                Log.error("OnCompleteApplier", "reward item \"" + oc.give_item
                        + "\" not granted (unknown id or inventory full) after solving \"" + activityId + "\"");
            }
        }
        if (oc.set_flag != null) {
            flags.setFlag(oc.set_flag);
        }
        Log.debug("OnCompleteApplier", "activity \"" + activityId + "\" complete: set_flag=" + oc.set_flag
                + " give_item=" + oc.give_item + " dialogue=" + oc.dialogue + " firstTime=" + firstTime);
        return firstTime ? oc.dialogue : null;
    }
}
