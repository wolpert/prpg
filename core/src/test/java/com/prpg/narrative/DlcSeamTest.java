package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prpg.items.Inventory;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The DLC boundary: a DLC act runs <b>standalone</b> with base-game state handed in and the base
 * story absent. Cross-act continuity flows through shared story variables (declared once in
 * {@code baseline/ink/common/world.ink}) kept by name in {@link StoryVariables}, so DLC acts can be
 * authored, compiled, and delivered independently of the base game.
 */
class DlcSeamTest {

    @Test
    void dlcActReadsInjectedBaseStateWithNoBaseStoryPresent() {
        NarrativeState base = new NarrativeState();
        base.setCurrentActId("act2");
        base.unlockAct("act2");
        StoryVariables vars = new StoryVariables();
        vars.set("act1_complete", true); // produced by the base game

        // The content source supplies ONLY the DLC act; no act1 story exists here.
        NarrativeRunner runner = InkTestSupport.runner(
                InkTestSupport.sourceFor("act2"), base, vars, mock(Inventory.class), actId -> true);

        assertTrue(runner.start("act2", "traveller", null), "DLC act runs in isolation");
        List<String> lines = InkTestSupport.drivePickingFirst(runner);

        assertTrue(lines.stream().anyMatch(l -> l.contains("came through the gatehouse")), lines.toString());
        assertTrue(vars.isTrue("met_traveller"), "the act's own write landed in the shared store");
    }

    @Test
    void aSharedVariableSetInOneActIsReadInTheNext() {
        NarrativeState state = new NarrativeState();
        state.setCurrentActId("act1");
        state.unlockAct("act1");
        StoryVariables vars = new StoryVariables();
        vars.set("strongbox_opened", true);
        Inventory inventory = mock(Inventory.class);
        when(inventory.has("sigil", 1)).thenReturn(true);
        when(inventory.add(anyString(), anyInt())).thenReturn(true);
        NarrativeRunner runner = InkTestSupport.runner(
                InkTestSupport.sourceFor("act1", "act2"), state, vars, inventory, actId -> true);

        runner.start("act1", "keeper_inside", null);
        InkTestSupport.drain(runner); // sets act1_complete and advances to act2

        runner.start("act2", "traveller", null);
        List<String> lines = InkTestSupport.drivePickingFirst(runner);
        assertTrue(lines.stream().anyMatch(l -> l.contains("came through the gatehouse")), lines.toString());
    }

    @Test
    void uninstalledActIsRefusedGracefully() {
        NarrativeRunner runner = InkTestSupport.runner(
                InkTestSupport.sourceFor("act1"), new NarrativeState(), new StoryVariables(),
                mock(Inventory.class), actId -> true);

        assertFalse(runner.start("act2", "traveller", null));
        assertFalse(runner.isActive());
    }
}
