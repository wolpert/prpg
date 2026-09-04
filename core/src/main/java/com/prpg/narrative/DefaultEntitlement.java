package com.prpg.narrative;

import com.prpg.narrative.config.NarrativeManifest.ActEntry;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.narrative.content.ActContentSource;
import com.prpg.world.FlagStore;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default {@link Entitlement}: the <b>no-enforcement</b> policy. An act is owned if it is (a)
 * catalogued as {@code entitlement: FREE}, (b) provided by a mounted content pack
 * ({@link ActContentSource#has}), so a manually-dropped pack just works, or (c) marked by a durable
 * {@code meta.owns.<actId>} flag, the seam a real store-receipt check writes through.
 *
 * <p>To enforce purchases, drop rule (b) and have your store integration call {@link #grant}
 * (or replace this class in {@code NarrativeModule}); nothing in {@link ActProgression} changes.
 */
@Singleton
public class DefaultEntitlement implements Entitlement {

    /** Prefix for the durable "owns this act" flag. */
    public static final String OWNS_PREFIX = FlagStore.META_PREFIX + "owns.";

    private final FlagStore flagStore;
    private final ActContentSource source;
    private final ActContentRegistry registry;

    @Inject
    public DefaultEntitlement(FlagStore flagStore, ActContentSource source, ActContentRegistry registry) {
        this.flagStore = flagStore;
        this.source = source;
        this.registry = registry;
    }

    @Override
    public boolean owns(String actId) {
        if (actId == null) return false;
        ActEntry entry = registry.entry(actId);
        if (entry != null && entry.isFree()) return true;
        // No entitlement enforcement yet: anything mounted is playable.
        if (source.has(actId)) return true;
        return flagStore.hasFlag(OWNS_PREFIX + actId);
    }

    /** Grants entitlement to an act (what a successful purchase callback would invoke). */
    public void grant(String actId) {
        flagStore.setFlag(OWNS_PREFIX + actId);
    }
}
