package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prpg.items.Inventory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Ink side of the game through the sample act: story variables written by Ink land in
 * {@link StoryVariables} and stored values steer the story, the inventory and act gate are readable
 * and writable from Ink, and the world functions reach Java only inside an evaluation. Drives real
 * compiled Ink through the runner so the binding (including lookahead flags) is exercised end to end.
 */
class StateBridgeTest {

    private StoryVariables vars;
    private Inventory inventory;
    private NarrativeState state;

    @BeforeEach
    void setUp() {
        vars = new StoryVariables();
        inventory = mock(Inventory.class);
        when(inventory.add(anyString(), anyInt())).thenReturn(true);
        state = new NarrativeState();
        state.setCurrentActId("act1");
        state.unlockAct("act1");
    }

    private NarrativeRunner runner(Entitlement entitlement, String... acts) {
        return InkTestSupport.runner(InkTestSupport.sourceFor(acts), state, vars, inventory, entitlement);
    }

    @Test
    void inkVariableWritesLandInTheStoreAndSpeakerTagIsRead() {
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "keeper_greeting", null);
        assertTrue(vars.isTrue("met_keeper"), "~ met_keeper = true reached the store");
        assertTrue(r.currentText().contains("new hand"));
        assertEquals("Keeper", r.currentSpeaker());
        assertEquals(2, r.choiceTexts().size());
    }

    @Test
    void storedValuesSteerTheConversation() {
        vars.set("strongbox_opened", true);
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "keeper_inside", null);
        assertTrue(r.currentText().contains("workbench"), "the stored value picked the later branch");
    }

    @Test
    void theDayIsAPlainStoryVariable() {
        vars.set("day", 3);
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "bench_rest", null);
        assertTrue(r.currentText().contains("day 3"), r.currentText());
        r.selectChoice(0); // Rest a while.
        assertEquals(4, vars.getInt("day"), "~ day++ ran exactly once (not during lookahead)");
        assertTrue(vars.isTrue("rested"));
        assertTrue(r.currentText().contains("day 4"), r.currentText());
    }

    @Test
    void hasItemGatesAndAdvanceActMovesTheSpine() {
        vars.set("strongbox_opened", true);
        when(inventory.has("sigil", 1)).thenReturn(true);
        // act2 is installed and everything is owned, so the gate reads ADVANCED and the act moves.
        NarrativeRunner r = runner(id -> true, "act1", "act2");
        r.start("act1", "keeper_inside", null);
        List<String> lines = InkTestSupport.drain(r);
        assertTrue(vars.isTrue("act1_complete"));
        assertTrue(state.isActUnlocked("act2"), "unlock_act landed on the act spine");
        assertTrue(lines.stream().anyMatch(l -> l.contains("gate is open")), lines.toString());
        assertEquals("act2", state.getCurrentActId(), "advance_act() moved the spine");
    }

    @Test
    void nextActGateExplainsARefusalWithoutAdvancing() {
        vars.set("strongbox_opened", true);
        when(inventory.has("sigil", 1)).thenReturn(true);
        // act2 is NOT in the source (not installed); the no-enforcement entitlement owns it anyway.
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "keeper_inside", null);
        List<String> lines = InkTestSupport.drain(r);
        assertTrue(lines.stream().anyMatch(l -> l.contains("isn't built yet")), lines.toString());
        assertEquals("act1", state.getCurrentActId(), "a refused gate leaves the act alone");

        // And when it is installed but not owned: the paywall line.
        NarrativeRunner paywalled = runner(id -> "act1".equals(id), "act1", "act2");
        paywalled.start("act1", "keeper_inside", null);
        List<String> paywall = InkTestSupport.drain(paywalled);
        assertTrue(paywall.stream().anyMatch(l -> l.contains("isn't yours yet")), paywall.toString());
        assertEquals("act1", state.getCurrentActId());
    }

    @Test
    void aWonPuzzleGivesItsRewardFromInk() {
        // The stand-in play command leaves activity_won at its world.ink default (true): a win.
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "strongbox", null);
        List<String> lines = InkTestSupport.drain(r);
        verify(inventory).add("brass_token", 1);
        assertTrue(vars.isTrue("strongbox_opened"));
        assertTrue(lines.stream().anyMatch(l -> l.contains("latch clicks")), lines.toString());
    }

    @Test
    void aLostPuzzleGivesNothing() {
        vars.set("activity_won", false);
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "strongbox", null);
        InkTestSupport.drain(r);
        verify(inventory, never()).add(anyString(), anyInt());
        assertFalse(vars.isTrue("strongbox_opened"));
    }

    @Test
    void worldFunctionsReachJavaOnlyInsideAnEvaluation() {
        NarrativeRunner r = runner(id -> true, "act1");
        List<StoryCall> calls = new ArrayList<>();
        assertTrue(r.evaluate("act1", "stage", calls::add));
        assertTrue(calls.stream().anyMatch(c -> c.function().equals("actor")
                        && c.args().equals(List.of("keeper", "gatehouse_yard", "keeper_post", "keeper_greeting"))),
                "a divert-target argument arrives as its knot name: " + calls);
        assertFalse(r.evaluate("act1", "no_such_function", calls::add));
    }
}
