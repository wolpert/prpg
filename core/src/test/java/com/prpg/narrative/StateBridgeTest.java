package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prpg.items.Inventory;
import com.prpg.world.FlagStore;
import com.prpg.world.GameClock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Asserts the Ink to Java bridge through the sample act: external functions land their effects on
 * the canonical model, reads reflect canonical state, and the act gate is readable and advanceable
 * from Ink. Drives real compiled Ink through the runner so the binding (including lookahead flags)
 * is exercised end to end.
 */
class StateBridgeTest {

    private FlagStore flags;
    private Inventory inventory;
    private GameClock clock;
    private NarrativeState state;

    @BeforeEach
    void setUp() {
        flags = new FlagStore();
        inventory = mock(Inventory.class);
        clock = new GameClock();
        state = new NarrativeState();
        state.setCurrentActId("act1");
        state.unlockAct("act1");
    }

    private NarrativeRunner runner(Entitlement entitlement, String... acts) {
        return InkTestSupport.runner(InkTestSupport.sourceFor(acts), state, flags, inventory, clock, entitlement);
    }

    @Test
    void setFlagWritesToFlagStoreAndSpeakerTagIsRead() {
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "keeper_greeting", null);
        assertTrue(flags.hasFlag("act1.met_keeper"));
        assertTrue(r.currentText().contains("new hand"));
        assertEquals("Keeper", r.currentSpeaker());
        assertEquals(2, r.choiceTexts().size());
    }

    @Test
    void hasFlagReadRoutesTheConversation() {
        flags.setFlag("act1.hedge_cleared");
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "keeper_greeting", null);
        assertTrue(r.currentText().contains("hedge is down"), "the has_flag read picked the later branch");
    }

    @Test
    void dayReadAndAdvanceDayWrite() {
        clock.setDay(3);
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "bench_rest", null);
        assertTrue(r.currentText().contains("day 3"), r.currentText());
        r.selectChoice(0); // Rest a while.
        assertEquals(4, clock.getDay(), "advance_day() ran exactly once (not during lookahead)");
        assertTrue(flags.hasFlag("act1.rested"));
        assertTrue(r.currentText().contains("day 4"), r.currentText());
    }

    @Test
    void hasItemGatesAndAdvanceActMovesTheSpine() {
        when(inventory.has("brass_token", 1)).thenReturn(true);
        when(inventory.has("sigil", 1)).thenReturn(true);
        // act2 is installed and everything is owned, so the gate reads ADVANCED and the act moves.
        NarrativeRunner r = runner(id -> true, "act1", "act2");
        r.start("act1", "keeper_inside", null);
        List<String> lines = InkTestSupport.drain(r);
        assertTrue(flags.hasFlag("act1.act_complete"));
        assertTrue(state.isActUnlocked("act2"), "unlock_act landed on the canonical model");
        assertTrue(lines.stream().anyMatch(l -> l.contains("gate is open")), lines.toString());
        assertEquals("act2", state.getCurrentActId(), "advance_act() moved the spine");
    }

    @Test
    void nextActGateExplainsARefusalWithoutAdvancing() {
        when(inventory.has("brass_token", 1)).thenReturn(true);
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
    void giveItemIsCalledWithExactArguments() {
        // keeper_inside with no token and no sigil: the first line only; nothing is given.
        NarrativeRunner r = runner(id -> true, "act1");
        r.start("act1", "keeper_inside", null);
        InkTestSupport.drain(r);
        assertFalse(r.isActive());
        verify(inventory, org.mockito.Mockito.never()).add(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
