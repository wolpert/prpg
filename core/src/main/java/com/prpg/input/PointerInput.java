package com.prpg.input;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Shared pointer (touch / mouse) intent, written by {@code WorldScreen}'s touch handlers and read by
 * the movement and interaction systems. This is what makes the game playable without a keyboard:
 * holding/dragging on the world sets a movement direction toward the pointer, and a tap requests an
 * interaction with the faced target. Keyboard input still works in parallel (it takes precedence when
 * both are active).
 */
@Singleton
public class PointerInput {

    /** Current movement intent as a unit-ish vector; {@code (0,0)} means "no pointer movement". */
    private float dirX;
    private float dirY;

    /** One-shot "interact" request (a tap), consumed by the interaction system. */
    private boolean interactRequested;

    @Inject
    public PointerInput() {}

    public void setMovement(float x, float y) {
        this.dirX = x;
        this.dirY = y;
    }

    public void clearMovement() {
        this.dirX = 0f;
        this.dirY = 0f;
    }

    public float dirX() {
        return dirX;
    }

    public float dirY() {
        return dirY;
    }

    public boolean hasMovement() {
        return dirX != 0f || dirY != 0f;
    }

    /** Requests a single interaction (a tap); cleared by {@link #consumeInteract()}. */
    public void requestInteract() {
        this.interactRequested = true;
    }

    /** Returns whether an interaction was requested and clears the flag. */
    public boolean consumeInteract() {
        boolean requested = interactRequested;
        interactRequested = false;
        return requested;
    }
}
