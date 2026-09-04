package com.prpg.input;

import com.badlogic.gdx.Input;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Single source of truth for movement key bindings. Both the movement system and any UI that needs
 * "is the player trying to move" query this instead of hardcoding {@code Input.Keys.*} in several
 * files (which could drift). Centralizing the bindings here is also the seam for future
 * remapping/gamepad support — swap the key arrays and every consumer follows.
 */
@Singleton
public class InputBindings {

    private final Input input;

    // Default WASD + arrow keys. Mutable arrays so a future settings screen can rebind in place.
    private int[] upKeys = {Input.Keys.W, Input.Keys.UP};
    private int[] downKeys = {Input.Keys.S, Input.Keys.DOWN};
    private int[] leftKeys = {Input.Keys.A, Input.Keys.LEFT};
    private int[] rightKeys = {Input.Keys.D, Input.Keys.RIGHT};

    @Inject
    public InputBindings(Input input) {
        this.input = input;
    }

    public boolean up() { return anyPressed(upKeys); }
    public boolean down() { return anyPressed(downKeys); }
    public boolean left() { return anyPressed(leftKeys); }
    public boolean right() { return anyPressed(rightKeys); }

    /** True if any movement key is currently held. */
    public boolean anyMovement() {
        return up() || down() || left() || right();
    }

    public void setUpKeys(int... keys) { this.upKeys = keys; }
    public void setDownKeys(int... keys) { this.downKeys = keys; }
    public void setLeftKeys(int... keys) { this.leftKeys = keys; }
    public void setRightKeys(int... keys) { this.rightKeys = keys; }

    private boolean anyPressed(int[] keys) {
        for (int key : keys) {
            if (input.isKeyPressed(key)) return true;
        }
        return false;
    }
}
