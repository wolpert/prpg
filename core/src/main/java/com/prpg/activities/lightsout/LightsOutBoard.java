package com.prpg.activities.lightsout;

import java.util.Random;

/**
 * Pure lights-out logic, free of libGDX. Pressing a cell toggles it and its four neighbours; the
 * board is solved when every cell is lit. Scrambled by applying random presses from the solved
 * state, so every board is solvable. Deterministic given a seed, so it is unit-testable.
 */
public class LightsOutBoard {

    private final int width;
    private final int height;
    private final boolean[][] lit; // [x][y]
    private int presses;

    public LightsOutBoard(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("lights-out board needs positive dimensions");
        }
        this.width = width;
        this.height = height;
        this.lit = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                lit[x][y] = true;
            }
        }
    }

    public int width() { return width; }
    public int height() { return height; }
    public int presses() { return presses; }
    public boolean isLit(int x, int y) { return lit[x][y]; }

    /** Scrambles from the solved state with {@code moves} random presses (never leaves it solved). */
    public void scramble(int moves, long seed) {
        Random random = new Random(seed);
        int guard = 0;
        do {
            for (int i = 0; i < Math.max(1, moves); i++) {
                toggle(random.nextInt(width), random.nextInt(height));
            }
        } while (isSolved() && guard++ < 16);
        presses = 0;
    }

    /** A player press: toggles the cell and its orthogonal neighbours. Returns whether it was in bounds. */
    public boolean press(int x, int y) {
        if (!inBounds(x, y)) return false;
        toggle(x, y);
        presses++;
        return true;
    }

    private void toggle(int x, int y) {
        flip(x, y);
        flip(x - 1, y);
        flip(x + 1, y);
        flip(x, y - 1);
        flip(x, y + 1);
    }

    private void flip(int x, int y) {
        if (inBounds(x, y)) lit[x][y] = !lit[x][y];
    }

    public boolean isSolved() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!lit[x][y]) return false;
            }
        }
        return true;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }
}
