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
import com.prpg.narrative.StoryCommand;
import com.prpg.world.command.CutsceneCommand;
import com.prpg.world.command.FadeCommand;
import com.prpg.world.command.GoCommand;
import com.prpg.world.command.PlayCommand;
import com.prpg.world.command.WaitCommand;
import com.prpg.world.scene.FadeBeatEvent;
import com.prpg.world.scene.ScriptedEvent;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import javax.inject.Singleton;

/**
 * Gameplay wiring. Four string-keyed registries live here and are the seams stories refer to by
 * name: story commands (the first word of an Ink {@code >>>} line), full-screen activities and popup
 * activities ({@code >>> play <type> <id>}), and scripted events ({@code >>> cutscene <id>}). Add a
 * binding, and the matching word starts working in Ink; nothing in {@code WorldScreen} changes.
 * A new command also goes in {@code world/command/Commands.ALL} so the validators know it.
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

    // --- story commands: `>>> <name> args...` lines in Ink --------------------------------------------

    @Provides @Singleton @IntoMap @StringKey(PlayCommand.NAME)
    static StoryCommand bindPlayCommand(PlayCommand c) { return c; }

    @Provides @Singleton @IntoMap @StringKey(GoCommand.NAME)
    static StoryCommand bindGoCommand(GoCommand c) { return c; }

    @Provides @Singleton @IntoMap @StringKey(WaitCommand.NAME)
    static StoryCommand bindWaitCommand(WaitCommand c) { return c; }

    @Provides @Singleton @IntoMap @StringKey(FadeCommand.NAME)
    static StoryCommand bindFadeCommand(FadeCommand c) { return c; }

    @Provides @Singleton @IntoMap @StringKey(CutsceneCommand.NAME)
    static StoryCommand bindCutsceneCommand(CutsceneCommand c) { return c; }

    // --- scripted events (cutscenes), keyed by the id `>>> cutscene <id>` names -------------------------

    @Provides @Singleton @IntoMap @StringKey(FadeBeatEvent.ID)
    static ScriptedEvent bindFadeBeat(FadeBeatEvent e) { return e; }
}
