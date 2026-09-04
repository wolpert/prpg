package com.prpg.activities.match3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure match-3 board logic, free of libGDX. Cells store a piece-type index (0..numTypes-1) or
 * {@link #EMPTY}. Coordinates use y=0 as the top row; gravity pulls pieces toward the bottom
 * (higher y). Deterministic given a seed, so it's unit-testable without GL or asset loading.
 */
public class Match3Board {

    public static final int EMPTY = -1;
    private static final int MAX_CASCADES = 100;

    private final int width;
    private final int height;
    private final int numTypes;
    private final int[][] cells; // [x][y]
    private final Random random;
    private int totalCleared;
    private final int[] clearedByType; // cumulative cleared count per piece-type index

    public Match3Board(int width, int height, int numTypes, long seed) {
        if (numTypes < 3) {
            throw new IllegalArgumentException("match-3 needs at least 3 piece types");
        }
        this.width = width;
        this.height = height;
        this.numTypes = numTypes;
        this.random = new Random(seed);
        this.cells = new int[width][height];
        this.clearedByType = new int[numTypes];
        fillNoMatches();
    }

    public int width() { return width; }
    public int height() { return height; }
    public int numTypes() { return numTypes; }
    public int totalCleared() { return totalCleared; }

    /** Cumulative number of pieces of the given type index that have been cleared. */
    public int clearedOf(int type) { return clearedByType[type]; }

    public int get(int x, int y) { return cells[x][y]; }

    public boolean areAdjacent(int ax, int ay, int bx, int by) {
        return Math.abs(ax - bx) + Math.abs(ay - by) == 1;
    }

    /**
     * Swaps two adjacent cells. If the swap forms at least one match, resolves all resulting
     * cascades (incrementing {@link #totalCleared}) and returns true. Otherwise reverts the swap
     * and returns false. This is the all-at-once form; UI that wants to animate each cascade step
     * should use {@link #trySwap} plus {@link #findMatches}/{@link #clearMatches}/
     * {@link #applyGravity}/{@link #refill}.
     */
    public boolean swap(int ax, int ay, int bx, int by) {
        if (!trySwap(ax, ay, bx, by)) {
            return false;
        }
        resolve(findMatches());
        return true;
    }

    /**
     * Swaps two adjacent cells if it forms at least one match, leaving the board in the swapped
     * (un-resolved) state and returning true. Reverts and returns false if no match results.
     */
    public boolean trySwap(int ax, int ay, int bx, int by) {
        if (!inBounds(ax, ay) || !inBounds(bx, by) || !areAdjacent(ax, ay, bx, by)) {
            return false;
        }
        swapCells(ax, ay, bx, by);
        if (findMatches().isEmpty()) {
            swapCells(ax, ay, bx, by); // revert
            return false;
        }
        return true;
    }

    /**
     * Clears the given matched cells to {@link #EMPTY}, adds to {@link #totalCleared} and to the
     * per-type tally read by {@link #clearedOf}.
     */
    public int clearMatches(Set<Long> matches) {
        for (long p : matches) {
            int x = unpackX(p);
            int y = unpackY(p);
            int type = cells[x][y];
            if (type != EMPTY) {
                clearedByType[type]++;
            }
            cells[x][y] = EMPTY;
        }
        totalCleared += matches.size();
        return matches.size();
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private void swapCells(int ax, int ay, int bx, int by) {
        int tmp = cells[ax][ay];
        cells[ax][ay] = cells[bx][by];
        cells[bx][by] = tmp;
    }

    private void fillNoMatches() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int type;
                do {
                    type = random.nextInt(numTypes);
                } while (wouldExtendRun(x, y, type));
                cells[x][y] = type;
            }
        }
    }

    /** True if placing {@code type} at (x,y) completes a 3-run with already-filled left/up cells. */
    private boolean wouldExtendRun(int x, int y, int type) {
        if (x >= 2 && cells[x - 1][y] == type && cells[x - 2][y] == type) return true;
        return y >= 2 && cells[x][y - 1] == type && cells[x][y - 2] == type;
    }

    public Set<Long> findMatches() {
        Set<Long> matched = new HashSet<>();
        // Horizontal runs.
        for (int y = 0; y < height; y++) {
            int runStart = 0;
            for (int x = 1; x <= width; x++) {
                boolean same = x < width && cells[x][y] != EMPTY && cells[x][y] == cells[runStart][y];
                if (!same) {
                    if (cells[runStart][y] != EMPTY && x - runStart >= 3) {
                        for (int i = runStart; i < x; i++) matched.add(pack(i, y));
                    }
                    runStart = x;
                }
            }
        }
        // Vertical runs.
        for (int x = 0; x < width; x++) {
            int runStart = 0;
            for (int y = 1; y <= height; y++) {
                boolean same = y < height && cells[x][y] != EMPTY && cells[x][y] == cells[x][runStart];
                if (!same) {
                    if (cells[x][runStart] != EMPTY && y - runStart >= 3) {
                        for (int i = runStart; i < y; i++) matched.add(pack(x, i));
                    }
                    runStart = y;
                }
            }
        }
        return matched;
    }

    private void resolve(Set<Long> initial) {
        Set<Long> matches = initial;
        int guard = 0;
        while (!matches.isEmpty() && guard++ < MAX_CASCADES) {
            clearMatches(matches);
            applyGravity();
            refill();
            matches = findMatches();
        }
    }

    /**
     * Compacts each column's non-empty cells toward the bottom (higher y). Returns the list of
     * moves as {@code {x, fromY, toY}} so the UI can animate each falling piece.
     */
    public List<int[]> applyGravity() {
        List<int[]> moves = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            int writeY = height - 1;
            for (int y = height - 1; y >= 0; y--) {
                if (cells[x][y] != EMPTY) {
                    if (writeY != y) {
                        cells[x][writeY] = cells[x][y];
                        cells[x][y] = EMPTY;
                        moves.add(new int[]{x, y, writeY});
                    }
                    writeY--;
                }
            }
        }
        return moves;
    }

    /**
     * Fills every empty cell with a new random piece. Returns the spawned cells as
     * {@code {x, y, type}} so the UI can animate them dropping in.
     */
    public List<int[]> refill() {
        List<int[]> spawns = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (cells[x][y] == EMPTY) {
                    int type = random.nextInt(numTypes);
                    cells[x][y] = type;
                    spawns.add(new int[]{x, y, type});
                }
            }
        }
        return spawns;
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static int unpackX(long p) {
        return (int) (p >> 32);
    }

    private static int unpackY(long p) {
        return (int) (p & 0xffffffffL);
    }
}
