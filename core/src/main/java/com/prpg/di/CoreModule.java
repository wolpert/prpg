package com.prpg.di;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.prpg.AppInfo;
import com.prpg.assets.Asset;
import com.prpg.assets.LoadableAsset;
import com.prpg.config.ConfigLoader;
import com.prpg.config.GameConfig;
import com.prpg.content.ContentResolver;
import com.prpg.ecs.InputGate;
import com.prpg.ecs.system.AnimationSystem;
import com.prpg.ecs.system.MovementSystem;
import com.prpg.ecs.system.RenderSystem;
import com.prpg.lifecycle.AppLifecycle;
import com.prpg.lifecycle.LifecycleGate;
import com.prpg.render.TintFlash;
import com.prpg.screens.LoadingScreen;
import com.prpg.screens.MainMenuScreen;
import com.prpg.screens.PreferencesScreen;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import java.io.IOException;
import java.io.Reader;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Singleton;

/**
 * Engine-level wiring: libGDX globals, the ECS engine, the shared scaffold systems, and the menu
 * screens. Gameplay wiring (world screen, activities, cutscenes) lives in {@code world/WorldModule};
 * narrative wiring in {@code narrative/NarrativeModule}.
 */
@Module
public final class CoreModule {

    private CoreModule() {}

    private static final String GAME_CONFIG_PATH = "config/game.yaml";
    private static final String PREFERENCES_NAME = AppInfo.NAME;

    @Provides
    @Singleton
    static InputGate provideInputGate() {
        return () -> true;
    }

    @Provides
    @Singleton
    static Supplier<String> provideDebugOverlayExtra() {
        return () -> "";
    }

    /**
     * Bind the mutable {@link AppLifecycle} singleton behind the read-only {@link LifecycleGate}
     * interface. {@code TheGame} injects the concrete impl so it can flip the active flag from
     * {@code pause()}/{@code resume()}; everything else takes the gate.
     */
    @Provides
    @Singleton
    static LifecycleGate provideLifecycleGate(AppLifecycle app) {
        return app;
    }

    @Provides
    @Singleton
    static SpriteBatch provideSpriteBatch() {
        return new SpriteBatch();
    }

    @Provides
    @Singleton
    static AssetManager provideAssetManager() {
        return new AssetManager();
    }

    /**
     * Lifecycle foot-gun: {@link Gdx#app} is null until the platform launcher has constructed the
     * {@link com.badlogic.gdx.Application}. Don't resolve this provider during graph construction;
     * {@code TheGame.create()} runs the Dagger builder, so anything reached from {@code inject(this)}
     * is safe.
     */
    @Provides
    @Singleton
    static Preferences providePreferences() {
        return Gdx.app.getPreferences(PREFERENCES_NAME);
    }

    /** Same constraint as {@link #providePreferences()}: {@link Gdx#files} must be initialised. */
    @Provides
    @Singleton
    static Skin provideSkin(ContentResolver content) {
        return new Skin(content.resolve("ui/uiskin.json"));
    }

    /**
     * Lifecycle-dependent global: {@link Gdx#input} is the live polling singleton, not a snapshot.
     * Consumers must run inside a system's {@code update()} or a screen's {@code render()}.
     */
    @Provides
    @Singleton
    static Input provideInput() {
        return Gdx.input;
    }

    /** Lifecycle-dependent global: {@link Gdx#graphics} is the live frame/window object. */
    @Provides
    @Singleton
    static Graphics provideGraphics() {
        return Gdx.graphics;
    }

    @Provides
    @Singleton
    static GameConfig provideGameConfig(ConfigLoader loader, ContentResolver content) {
        try (Reader reader = content.resolve(GAME_CONFIG_PATH).reader()) {
            GameConfig config = loader.load(GameConfig.class, reader);
            return config != null ? config : new GameConfig();
        } catch (IOException e) {
            throw new GdxRuntimeException("failed to load " + GAME_CONFIG_PATH, e);
        }
    }

    // Scaffold ECS systems. Gameplay systems are contributed by WorldModule the same way.
    @Provides @Singleton @IntoSet
    static EntitySystem bindMovementSystem(MovementSystem s) { return s; }

    @Provides @Singleton @IntoSet
    static EntitySystem bindAnimationSystem(AnimationSystem s) { return s; }

    @Provides @Singleton @IntoSet
    static EntitySystem bindTintFlashSystem(TintFlash s) { return s; }

    @Provides @Singleton @IntoSet
    static EntitySystem bindRenderSystem(RenderSystem s) { return s; }

    /**
     * The single app-lifetime ECS engine. Systems are app-lifetime and must no-op when their family
     * is empty; only {@code WorldScreen} ticks the engine; a screen that populates the engine clears
     * it on entry so stale entities never leak across screens.
     */
    @Provides
    @Singleton
    static Engine provideEngine(Set<EntitySystem> systems) {
        Engine engine = new PooledEngine();
        for (EntitySystem s : systems) {
            engine.addSystem(s);
        }
        return engine;
    }

    // Assets queued by the loading screen (see the Asset enum).
    @Provides
    @ElementsIntoSet
    static Set<LoadableAsset> provideScaffoldAssets() {
        return Set.copyOf(EnumSet.allOf(Asset.class));
    }

    // Screen registry. Add a new screen by adding one @IntoMap @ScreenKey provider.
    @Provides @Singleton @IntoMap @ScreenKey(LoadingScreen.class)
    static com.badlogic.gdx.Screen bindLoadingScreen(LoadingScreen s) { return s; }

    @Provides @Singleton @IntoMap @ScreenKey(MainMenuScreen.class)
    static com.badlogic.gdx.Screen bindMainMenuScreen(MainMenuScreen s) { return s; }

    @Provides @Singleton @IntoMap @ScreenKey(PreferencesScreen.class)
    static com.badlogic.gdx.Screen bindPreferencesScreen(PreferencesScreen s) { return s; }
}
