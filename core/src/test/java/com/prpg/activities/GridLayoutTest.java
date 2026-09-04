package com.prpg.activities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GridLayoutTest {

    @Test
    void fitsCellSizeToTheSmallerAxisAndClamps() {
        GridLayout grid = new GridLayout();
        grid.setBoard(6, 6);
        grid.fit(300f, 600f, 24, 96);
        assertEquals(50, grid.cellSize(), "300 / 6 columns is the binding constraint");
        grid.fit(5000f, 5000f, 24, 96);
        assertEquals(96, grid.cellSize(), "clamped to max");
        grid.fit(10f, 10f, 24, 96);
        assertEquals(24, grid.cellSize(), "clamped to min");
    }

    @Test
    void rowZeroIsTheTopRowInStageCoordinates() {
        GridLayout grid = new GridLayout();
        grid.setBoard(3, 2);
        grid.fit(300f, 200f, 10, 100);
        assertEquals(100f, grid.cellY(0), "top row sits above the bottom row in y-up space");
        assertEquals(0f, grid.cellY(1));
        assertArrayEquals(new int[]{2, 0}, grid.cellAt(250f, 150f));
        assertArrayEquals(new int[]{0, 1}, grid.cellAt(5f, 5f));
        assertNull(grid.cellAt(-1f, 5f));
        assertNull(grid.cellAt(301f, 5f));
    }
}
