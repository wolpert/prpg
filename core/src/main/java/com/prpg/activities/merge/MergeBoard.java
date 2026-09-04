package com.prpg.activities.merge;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure merge-puzzle logic, free of libGDX. Cells hold an item id or null (empty). A drag from a
 * source cell to a destination cell either moves the tile (empty target) or combines the two tiles
 * if their unordered pair matches a ladder recipe. Recipes are supplied as a map from a normalized
 * pair key to the produced id.
 */
public class MergeBoard {

    private final int width;
    private final int height;
    private final String[][] cells;
    private final Map<String, String> recipes; // pairKey -> result id

    public MergeBoard(int width, int height, Map<String, String> recipes) {
        this.width = width;
        this.height = height;
        this.cells = new String[width][height];
        this.recipes = new HashMap<>(recipes);
    }

    public int width() { return width; }
    public int height() { return height; }
    public String get(int x, int y) { return cells[x][y]; }

    public void set(int x, int y, String id) {
        cells[x][y] = id;
    }

    /** The kind of outcome a drag produced, for the UI to react to. */
    public enum DragResult { NONE, MOVED, MERGED }

    public DragResult resolveDrag(int sx, int sy, int dx, int dy) {
        if (!inBounds(sx, sy) || !inBounds(dx, dy)) return DragResult.NONE;
        if (sx == dx && sy == dy) return DragResult.NONE;

        String src = cells[sx][sy];
        if (src == null) return DragResult.NONE;

        String dst = cells[dx][dy];
        if (dst == null) {
            cells[dx][dy] = src;
            cells[sx][sy] = null;
            return DragResult.MOVED;
        }

        String result = recipes.get(pairKey(src, dst));
        if (result != null) {
            cells[dx][dy] = result;
            cells[sx][sy] = null;
            return DragResult.MERGED;
        }
        return DragResult.NONE;
    }

    public int count(String id) {
        int n = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (id.equals(cells[x][y])) n++;
            }
        }
        return n;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Order-independent key for a pair of ids. */
    public static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
