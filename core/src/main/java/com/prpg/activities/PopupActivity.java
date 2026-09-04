package com.prpg.activities;

import com.badlogic.gdx.scenes.scene2d.Stage;

/**
 * An activity that opens as a panel over the world instead of replacing the screen. The world keeps
 * rendering underneath (frozen), the popup's {@link Stage} sits ahead of world input in the
 * multiplexer while open, and closing it hands control straight back with no screen transition.
 * Registered into {@code Map<String, PopupActivity>} in {@code WorldModule} under its {@code type}.
 *
 * <p>Lifecycle per use: {@link #launch(String)} then {@link #open()}; the world calls
 * {@link #update(float)}/{@link #draw()} each frame while {@link #isOpen()}; the popup (or the
 * world, on ESC/BACK) calls {@link #close()}.
 */
public interface PopupActivity extends Activity {

    void open();

    void close();

    boolean isOpen();

    void update(float delta);

    void draw();

    void resize(int width, int height);

    /** The popup's stage, added to the world's input multiplexer (consumes input only while open). */
    Stage getStage();

    void dispose();
}
