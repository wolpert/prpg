package com.prpg.activities;

/**
 * Cell-grid coordinate math shared by board-style activities (Scene2D y-up, board row 0 = top).
 * The cell size is fitted to an available area by {@link #fit}, so a board adapts to both the
 * window/device and the author-configurable board dimensions.
 */
public final class GridLayout {

    private int columns;
    private int rows;
    private int cellSize = 48;

    public void setBoard(int columns, int rows) {
        this.columns = Math.max(0, columns);
        this.rows = Math.max(0, rows);
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public int cellSize() { return cellSize; }
    public float boardWidth() { return columns * cellSize; }
    public float boardHeight() { return rows * cellSize; }

    /** Fits the cell size (clamped to [min, max]) so the whole board fills {@code availW x availH}. */
    public void fit(float availW, float availH, int minCell, int maxCell) {
        int bw = Math.max(1, columns);
        int bh = Math.max(1, rows);
        int fit = (int) Math.floor(Math.min(availW / bw, availH / bh));
        cellSize = Math.max(minCell, Math.min(maxCell, fit));
    }

    public float cellX(int x) {
        return x * cellSize;
    }

    public float cellY(int y) {
        return (rows - 1 - y) * cellSize;
    }

    /** Maps a board-local point to a {col,row} cell, or null if outside the board. */
    public int[] cellAt(float lx, float ly) {
        if (cellSize <= 0) return null;
        int col = (int) (lx / cellSize);
        int rowFromBottom = (int) (ly / cellSize);
        int row = rows - 1 - rowFromBottom;
        if (lx < 0 || ly < 0 || col >= columns || row < 0 || row >= rows) return null;
        return new int[]{col, row};
    }
}
