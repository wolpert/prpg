package com.prpg.world;

import com.badlogic.ashley.core.EntitySystem;
import com.prpg.activities.FullScreenActivity;
import com.prpg.activities.PopupActivity;
import com.prpg.activities.lightsout.LightsOutPopup;
import com.prpg.activities.match3.Match3Screen;
import com.prpg.activities.merge.MergeScreen;
import com.prpg.di.ScreenKey;
import com.prpg.ecs.system.InteractionSystem;
import com.prpg.ecs.system.PlayerMovementSystem;
import com.prpg.ecs.system.TriggerSystem;
import com.prpg.world.scene.FadeBeatEvent;
import com.prpg.world.scene.ScriptedEvent;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import javax.inject.Singleton;

/**
 * Gameplay wiring. Three string-keyed registries live here and are the seams content refers to by
 * name: scripted events (a trigger's {@code event} / an Ink {@code # event:} tag), full-screen
 * activities and popup activities (an obstacle's {@code activity.type}). Add a binding, and the
 * matching string starts working in content; nothing in {@code WorldScreen} changes.
 */
@Module
public class WorldModule {

    @Provides @Singleton @IntoSet
    static EntitySystem bindPlayerMovementSystem(PlayerMovementSystem s) { return s; }

    @Provides @Singleton @IntoSet
    static EntitySystem bindInteractionSystem(InteractionSystem s) { return s; }

    @Provides @Singleton @IntoSet
    static EntitySystem bindTriggerSystem(TriggerSystem s) { return s; }

    @Provides @Singleton @IntoMap @ScreenKey(WorldScreen.class)
    static com.badlogic.gdx.Screen bindWorldScreen(WorldScreen s) { return s; }

    // --- full-screen activities: registered as screens AND under their activity type -------------

    @Provides @Singleton @IntoMap @ScreenKey(Match3Screen.class)
    static com.badlogic.gdx.Screen bindMatch3Screen(Match3Screen s) { return s; }

    @Provides @Singleton @IntoMap @ScreenKey(MergeScreen.class)
    static com.badlogic.gdx.Screen bindMergeScreen(MergeScreen s) { return s; }

    @Provides @Singleton @IntoMap @StringKey(Match3Screen.TYPE)
    static FullScreenActivity bindMatch3Activity(Match3Screen s) { return s; }

    @Provides @Singleton @IntoMap @StringKey(MergeScreen.TYPE)
    static FullScreenActivity bindMergeActivity(MergeScreen s) { return s; }

    // --- popup activities: open over the world ---------------------------------------------------

    @Provides @Singleton @IntoMap @StringKey(LightsOutPopup.TYPE)
    static PopupActivity bindLightsOutActivity(LightsOutPopup p) { return p; }

    // --- scripted events (cutscenes), keyed by the id content references ---------------------------

    @Provides @Singleton @IntoMap @StringKey(FadeBeatEvent.ID)
    static ScriptedEvent bindFadeBeat(FadeBeatEvent e) { return e; }
}
