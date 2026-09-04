package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.prpg.items.Inventory;
import com.prpg.world.FlagStore;
import com.prpg.world.GameClock;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The DLC boundary: a DLC act runs <b>standalone</b> with base-game canonical state injected and the
 * base story absent. Cross-act continuity flows through the Java model (flags), not Ink, so DLC acts
 * can be authored, compiled, and delivered independently of the base game.
 */
class DlcSeamTest {

    @Test
    void dlcActReadsInjectedBaseStateWithNoBaseStoryPresent() {
        NarrativeState base = new NarrativeState();
        base.setCurrentActId("act2");
        base.unlockAct("act2");
        FlagStore flags = new FlagStore();
        flags.setFlag("act1.act_complete"); // produced by the base game

        // The content source supplies ONLY the DLC act; no act1 story exists here.
        NarrativeRunner runner = InkTestSupport.runner(
                InkTestSupport.sourceFor("act2"), base, flags, mock(Inventory.class), new GameClock(),
                actId -> true);

        assertTrue(runner.start("act2", "traveller", null), "DLC act runs in isolation");
        List<String> lines = InkTestSupport.drivePickingFirst(runner);

        assertTrue(lines.stream().anyMatch(l -> l.contains("came through the gatehouse")), lines.toString());
        assertTrue(flags.hasFlag("act2.met_traveller"), "the act's own write landed in the shared flag model");
    }

    @Test
    void uninstalledActIsRefusedGracefully() {
        NarrativeRunner runner = InkTestSupport.runner(
                InkTestSupport.sourceFor("act1"), new NarrativeState(), new FlagStore(),
                mock(Inventory.class), new GameClock(), actId -> true);

        assertFalse(runner.start("act2", "traveller", null));
        assertFalse(runner.isActive());
    }
}
