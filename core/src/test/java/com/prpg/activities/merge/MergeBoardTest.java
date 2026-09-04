package com.prpg.activities.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.prpg.activities.merge.MergeBoard.DragResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MergeBoardTest {

    private MergeBoard board;

    @BeforeEach
    void setUp() {
        Map<String, String> recipes = new HashMap<>();
        recipes.put(MergeBoard.pairKey("wormwood_sprig", "wormwood_sprig"), "wormwood_bundle");
        recipes.put(MergeBoard.pairKey("yarrow_sprig", "yarrow_sprig"), "yarrow_bundle");
        recipes.put(MergeBoard.pairKey("wormwood_bundle", "yarrow_bundle"), "ward_charm");
        board = new MergeBoard(4, 4, recipes);
    }

    @Test
    void sameTypeMergeProducesNextTier() {
        board.set(0, 0, "wormwood_sprig");
        board.set(1, 0, "wormwood_sprig");

        assertEquals(DragResult.MERGED, board.resolveDrag(0, 0, 1, 0));
        assertNull(board.get(0, 0));
        assertEquals("wormwood_bundle", board.get(1, 0));
    }

    @Test
    void crossTypeMergeFollowsRecipe() {
        board.set(0, 0, "wormwood_bundle");
        board.set(0, 1, "yarrow_bundle");

        assertEquals(DragResult.MERGED, board.resolveDrag(0, 0, 0, 1));
        assertEquals("ward_charm", board.get(0, 1));
        assertEquals(1, board.count("ward_charm"));
    }

    @Test
    void dragToEmptyMovesTile() {
        board.set(0, 0, "wormwood_sprig");

        assertEquals(DragResult.MOVED, board.resolveDrag(0, 0, 3, 3));
        assertNull(board.get(0, 0));
        assertEquals("wormwood_sprig", board.get(3, 3));
    }

    @Test
    void incompatiblePairIsNoOp() {
        board.set(0, 0, "wormwood_sprig");
        board.set(1, 0, "yarrow_sprig");

        assertEquals(DragResult.NONE, board.resolveDrag(0, 0, 1, 0));
        assertEquals("wormwood_sprig", board.get(0, 0));
        assertEquals("yarrow_sprig", board.get(1, 0));
    }

    @Test
    void draggingEmptyOrOntoSelfIsNoOp() {
        assertEquals(DragResult.NONE, board.resolveDrag(2, 2, 3, 3));
        board.set(0, 0, "wormwood_sprig");
        assertEquals(DragResult.NONE, board.resolveDrag(0, 0, 0, 0));
    }

    @Test
    void fullLadderToWardCharm() {
        board.set(0, 0, "wormwood_sprig");
        board.set(1, 0, "wormwood_sprig");
        board.set(0, 1, "yarrow_sprig");
        board.set(1, 1, "yarrow_sprig");

        board.resolveDrag(0, 0, 1, 0); // -> wormwood_bundle at (1,0)
        board.resolveDrag(0, 1, 1, 1); // -> yarrow_bundle at (1,1)
        board.resolveDrag(1, 0, 1, 1); // bundles -> ward_charm at (1,1)

        assertEquals(1, board.count("ward_charm"));
    }
}
