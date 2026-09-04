package com.prpg.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.prpg.util.Log;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Single source of truth for screen transitions. Screens register themselves into a Dagger
 * {@code Map<Class<? extends Screen>, Provider<Screen>>} via
 * {@code @Provides @IntoMap @ScreenKey(SomeScreen.class)} in the providing module, and the
 * navigator dispatches by class through {@link #goTo(Class)}. Adding a new screen is a single
 * edit (the {@code @IntoMap} provider in the owning module).
 *
 * <p>Each entry is held as a {@link Provider} so its Scene2D tree is only built when first
 * navigated to. {@link #disposeAll()} only resolves screens that were actually visited.
 *
 * <p>Screens themselves take a {@code Provider<ScreenNavigator>} to break the construction cycle
 * (the navigator transitively references every screen).
 */
@Singleton
public class ScreenNavigator {

    private final Game game;
    private final Map<Class<? extends Screen>, Provider<Screen>> screens;
    private final Set<Class<? extends Screen>> resolved = new HashSet<>();

    @Inject
    public ScreenNavigator(Game game,
                           Map<Class<? extends Screen>, Provider<Screen>> screens) {
        this.game = game;
        this.screens = screens;
    }

    /** True iff {@code key} has been registered into the screen map by some module. */
    public boolean has(Class<? extends Screen> key) {
        return screens.containsKey(key);
    }

    /** Navigate to the screen registered under {@code key}. No-op when nothing is registered. */
    public void goTo(Class<? extends Screen> key) {
        Provider<Screen> provider = screens.get(key);
        if (provider == null) {
            Log.error("ScreenNavigator", "no Screen registered for " + key.getSimpleName() + " (navigation ignored)");
            return;
        }
        resolved.add(key);
        game.setScreen(provider.get());
    }

    public void goToLoading() { goTo(LoadingScreen.class); }
    public void goToMainMenu() { goTo(MainMenuScreen.class); }
    public void goToPreferences() { goTo(PreferencesScreen.class); }
    /** Return to the overworld; used by full-screen activities so they don't import WorldScreen. */
    public void goToWorld() { goTo(com.prpg.world.WorldScreen.class); }

    /** Dispose every screen actually constructed during this run. */
    public void disposeAll() {
        for (Class<? extends Screen> key : resolved) {
            screens.get(key).get().dispose();
        }
    }
}
