package com.prpg.activities.lightsout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightsOutBoardTest {

    @Test
    void startsSolvedAndScrambleUnsolvesIt() {
        LightsOutBoard board = new LightsOutBoard(3, 3);
        assertTrue(board.isSolved());
        board.scramble(4, 1L);
        assertFalse(board.isSolved());
        assertEquals(0, board.presses(), "scrambling doesn't count as player presses");
    }

    @Test
    void pressTogglesCellAndOrthogonalNeighbours() {
        LightsOutBoard board = new LightsOutBoard(3, 3);
        assertTrue(board.press(1, 1));
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                boolean plus = (x == 1 && y == 1) || (x == 1 && y != 1) || (y == 1 && x != 1);
                assertEquals(!plus, board.isLit(x, y), "cell " + x + "," + y);
            }
        }
        assertEquals(1, board.presses());
    }

    @Test
    void pressingTheScrambleSequenceAgainSolvesIt() {
        // Presses commute and self-cancel, so replaying the scramble presses restores the board.
        LightsOutBoard board = new LightsOutBoard(3, 3);
        board.scramble(3, 42L);
        java.util.Random replay = new java.util.Random(42L);
        for (int i = 0; i < 3; i++) {
            board.press(replay.nextInt(3), replay.nextInt(3));
        }
        assertTrue(board.isSolved());
    }

    @Test
    void outOfBoundsPressIsIgnored() {
        LightsOutBoard board = new LightsOutBoard(2, 2);
        assertFalse(board.press(5, 5));
        assertEquals(0, board.presses());
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new LightsOutBoard(0, 3));
    }
}
