package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.prpg.items.Inventory;
import com.prpg.world.FlagStore;
import com.prpg.world.GameClock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the sample act through the Ink runner: output text, speaker tags, choices, and the
 * resulting canonical state. Also the golden path: every knot content refers to runs to an end.
 */
class NarrativeRunnerSampleActTest {

    private FlagStore flags;
    private NarrativeRunner runner;

    @BeforeEach
    void setUp() {
        flags = new FlagStore();
        NarrativeState state = new NarrativeState();
        state.setCurrentActId("act1");
        state.unlockAct("act1");
        runner = InkTestSupport.runner(InkTestSupport.sourceFor("act1", "act2"), state, flags,
                mock(Inventory.class), new GameClock(), actId -> true);
    }

    @Test
    void keeperGreetingChoicesAndEnd() {
        runner.start("act1", "keeper_greeting", null);
        assertEquals(List.of("How do I clear it?", "On my way."), runner.choiceTexts());
        runner.selectChoice(0);
        assertTrue(runner.currentText().contains("three of a kind"));
        runner.advance();
        assertFalse(runner.isActive(), "conversation ended");
    }

    @Test
    void everyKnotRunsToEndGoldenPath() {
        String[] knots = {
                "keeper_greeting", "keeper_inside", "hedge_cleared", "strongbox_opened", "strongbox_empty",
                "sigil_forged", "bench_rest", "notice_board"
        };
        for (String knot : knots) {
            assertTrue(runner.start("act1", knot, null), "knot exists: " + knot);
            List<String> lines = InkTestSupport.drivePickingFirst(runner);
            assertFalse(lines.isEmpty(), knot + " produced at least one line");
            assertFalse(runner.isActive(), knot + " ran to an end");
        }
    }

    @Test
    void missingKnotIsRefusedGracefully() {
        assertFalse(runner.start("act1", "no_such_knot", null));
        assertFalse(runner.isActive());
    }
}
