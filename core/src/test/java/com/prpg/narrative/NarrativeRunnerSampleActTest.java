package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.prpg.items.Inventory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the sample act through the Ink runner: output text, speaker tags, choices, command lines,
 * and the resulting story state. Also the golden path: every knot the act stages runs to an end.
 */
class NarrativeRunnerSampleActTest {

    private StoryVariables vars;
    private final List<List<String>> commands = new ArrayList<>();
    private NarrativeRunner runner;

    @BeforeEach
    void setUp() {
        vars = new StoryVariables();
        NarrativeState state = new NarrativeState();
        state.setCurrentActId("act1");
        state.unlockAct("act1");
        runner = InkTestSupport.runner(InkTestSupport.sourceFor("act1", "act2"), state, vars,
                mock(Inventory.class), actId -> true, InkTestSupport.instantCommands(commands::add));
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
                "act_start", "keeper_greeting", "keeper_inside", "hedge", "strongbox", "workbench",
                "take_ration", "bench_rest", "notice_board"
        };
        for (String knot : knots) {
            assertTrue(runner.start("act1", knot, null), "knot exists: " + knot);
            List<String> lines = InkTestSupport.drivePickingFirst(runner);
            assertFalse(lines.isEmpty(), knot + " produced at least one line");
            assertFalse(runner.isActive(), knot + " ran to an end");
        }
    }

    @Test
    void commandLinesGoToTheEngineNotThePlayer() {
        runner.start("act1", "hedge", null);
        List<String> lines = InkTestSupport.drain(runner);
        assertEquals(List.of(List.of("play", "match3", "hedge")), commands);
        assertTrue(lines.stream().noneMatch(l -> l.startsWith(">>>")), lines.toString());
        assertTrue(vars.isTrue("hedge_cleared"));
    }

    @Test
    void aKnotOfOnlyCommandsRunsAndEndsWithoutShowingAnything() {
        vars.set("met_keeper", true);
        assertTrue(runner.start("act1", "gate_arch", null));
        assertFalse(runner.isActive(), "nothing to show, so the conversation is already over");
        assertEquals(List.of(List.of("cutscene", "fade_beat")), commands);
        assertTrue(vars.isTrue("crossed_gate"));
    }

    @Test
    void missingKnotIsRefusedGracefully() {
        assertFalse(runner.start("act1", "no_such_knot", null));
        assertFalse(runner.isActive());
    }

    @Test
    void knotLookupReportsPresence() {
        assertTrue(runner.hasKnot("act1", "act_start"));
        assertFalse(runner.hasKnot("act2", "act_start"));
        assertFalse(runner.hasKnot("act9", "act_start"), "an uninstalled act has no knots");
    }
}
