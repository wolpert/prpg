package com.prpg.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Common Scene2D scaffolding for menu-style screens. Subclasses build their UI in their constructor
 * (the {@link Stage} is allocated in the {@code @Inject} ctor of this base class), and may override
 * {@link #render(float)} for custom drawing on top of the stage.
 *
 * <p>Uses a density-scaled {@link ScreenViewport} ({@code unitsPerPixel = 1 / Gdx.graphics.getDensity()})
 * so menu UIs render at a consistent physical size across DPIs — without this, buttons sized in
 * pixels become illegibly tiny on high-DPI Android devices. The rescale is re-applied on
 * {@link #resize(int, int)} because some devices alter density on orientation/window changes.
 *
 * <p>The {@link SpriteBatch} is shared (provided by {@code CoreModule.provideSpriteBatch()}) and
 * passed to {@link Stage}'s 2-arg constructor; that overload sets {@code ownsBatch=false}, so
 * {@link Stage#dispose()} won't dispose the shared batch — its lifecycle stays with Dagger.
 *
 * <p>BACK/ESCAPE handling is uniform: {@link #show()} installs an {@link InputMultiplexer} with the
 * {@link Stage} first (so open Scene2D Dialogs can consume the press via their own key bindings)
 * and a back-key {@link InputAdapter} second; it also calls
 * {@code Gdx.input.setCatchKey(Input.Keys.BACK, true)} so Android doesn't bubble the gesture up to
 * the OS and exit the app. Both {@link Input.Keys#BACK} and {@link Input.Keys#ESCAPE} route to
 * {@link #onBack()}, which subclasses override to customize the destination (default: no-op so
 * BaseScreen has zero Dagger dependencies; concrete scaffold screens override below).
 */
public abstract class BaseScreen implements Screen {

    protected final Stage stage;

    private final InputAdapter backKeyAdapter = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                onBack();
                return true;
            }
            return false;
        }
    };

    protected BaseScreen(SpriteBatch batch) {
        ScreenViewport viewport = new ScreenViewport();
        viewport.setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        // 2-arg Stage(viewport, batch) sets ownsBatch=false — Stage.dispose() will not dispose the
        // shared SpriteBatch, which is owned and disposed by Dagger / TheGame.
        this.stage = new Stage(viewport, batch);
    }

    @Override
    public void show() {
        // Catch BACK so Android doesn't deliver it as an app-exit. ESCAPE on desktop is delivered
        // to the input processor regardless. Stage gets first crack so an open Scene2D Dialog
        // (which binds its own ESCAPE/BACK keys) can consume the press before onBack() fires.
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(stage);
        mux.addProcessor(backKeyAdapter);
        Gdx.input.setInputProcessor(mux);
    }

    /**
     * Invoked when BACK (Android) or ESCAPE (desktop) is pressed while this screen is active.
     * Default is a no-op so BaseScreen carries zero Dagger dependencies; concrete scaffold screens
     * override to navigate (typically to the main menu) or to show a confirm-quit dialog.
     */
    protected void onBack() {
        // no-op
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.10f, 0.10f, 0.15f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        // Re-derive density on every resize: orientation/window-mode changes can alter
        // Gdx.graphics.getDensity() on some devices, and resize is the canonical re-derivation point.
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        // Don't dispose the stage here — screens are @Singleton and may be shown again.
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
