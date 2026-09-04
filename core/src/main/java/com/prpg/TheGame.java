package com.prpg;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.prpg.di.DaggerGameComponent;
import com.prpg.di.GameComponent;
import com.prpg.lifecycle.AppLifecycle;
import com.prpg.screens.ScreenNavigator;
import com.prpg.ui.ColorTextures;
import com.prpg.ui.Fonts;
import javax.inject.Inject;

/**
 * Entry point — builds the Dagger graph and hands control to {@link ScreenNavigator}, which
 * drives all screen transitions. The persistent resources (batch, asset manager, world, skin)
 * live here for app-lifetime ownership; per-screen state (Stages) lives on the screens themselves.
 *
 * <p>{@link AssetManager} owns every {@link com.badlogic.gdx.graphics.Texture} and
 * {@link com.badlogic.gdx.graphics.g2d.TextureAtlas} loaded by the game, so disposing it
 * releases the lot — no need to track those individually here.
 */
public class TheGame extends Game {

    @Inject SpriteBatch batch;
    @Inject AssetManager assets;
    @Inject Skin skin;
    @Inject ScreenNavigator nav;
    @Inject AppLifecycle appLifecycle;
    @Inject Fonts fonts;
    @Inject ColorTextures colorTextures;

    @Override
    public void create() {
        // Providers may touch GL/Gdx, which is only valid after create() — so build here.
        GameComponent component = DaggerGameComponent.builder().game(this).build();
        // Mount content packs (extract bundled packs + any dropped zips, refresh the registry) BEFORE
        // inject() resolves content-backed singletons (Skin, Fonts, GameConfig), so they read from the
        // mounted content root. No-op until packs are staged/installed; falls back to bundled assets.
        component.packMounter().mount();
        component.inject(this);
        nav.goToLoading();
    }

    @Override
    public void pause() {
        // Android activity onPause / desktop window minimize. Flip the lifecycle flag BEFORE
        // delegating so any screen.pause() callback below already sees the gate as inactive.
        appLifecycle.setActive(false);
        super.pause();
    }

    @Override
    public void resume() {
        // Foregrounded again — re-open the gate. Screens decide independently whether to auto-
        // resume gameplay; we just signal "the JVM is live again".
        appLifecycle.setActive(true);
        super.resume();
    }

    @Override
    public void dispose() {
        Screen current = getScreen();
        if (current != null) {
            current.hide();
        }
        nav.disposeAll();
        batch.dispose();
        assets.dispose();
        skin.dispose();
        // Both own native GL resources (FreeType fonts / cached placeholder textures) outside the
        // AssetManager, so they must be disposed explicitly or they leak across an Activity restart.
        fonts.dispose();
        colorTextures.dispose();
    }
}
