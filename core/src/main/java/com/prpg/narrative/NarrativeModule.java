package com.prpg.narrative;

import com.prpg.narrative.content.ActContentSource;
import com.prpg.narrative.content.PackContentSource;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * Dagger wiring for the Ink narrative layer. Only the two interface bindings need declaring; every
 * other narrative class is {@code @Singleton} with an {@code @Inject} constructor and is provided
 * automatically. Added to {@code GameComponent.modules}.
 */
@Module
public final class NarrativeModule {

    private NarrativeModule() {}

    /** The live content source: every act resolves from whichever mounted pack provides it. */
    @Provides
    @Singleton
    static ActContentSource provideContentSource(PackContentSource source) {
        return source;
    }

    /** Default entitlement: free acts + anything mounted; swap for a store-backed implementation here. */
    @Provides
    @Singleton
    static Entitlement provideEntitlement(DefaultEntitlement impl) {
        return impl;
    }
}
