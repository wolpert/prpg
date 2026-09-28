package com.prpg.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.prpg.items.Inventory;
import com.prpg.narrative.InkTestSupport;
import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.StoryVariables;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The journal is the current act's Ink {@code journal()} evaluated against story state. */
class QuestLogTest {

    private StoryVariables vars;
    private NarrativeState state;
    private QuestLog log;

    @BeforeEach
    void setUp() {
        vars = new StoryVariables();
        state = new NarrativeState();
        state.setCurrentActId("act1");
        NarrativeRunner runner = InkTestSupport.runner(InkTestSupport.sourceFor("act1", "act2"), state,
                vars, mock(Inventory.class), id -> true);
        log = new QuestLog(runner, state);
    }

    @Test
    void theActsJournalReadsFromStoryState() {
        List<QuestLog.Quest> quests = log.quests();
        assertEquals(1, quests.size());
        QuestLog.Quest errand = quests.get(0);
        assertEquals("@quest.first_errand.title", errand.title());
        assertEquals(5, errand.steps().size());
        assertEquals(0, errand.currentStepIndex());
        assertFalse(errand.isComplete());

        vars.set("met_keeper", true);
        vars.set("hedge_cleared", true);
        QuestLog.Quest later = log.quests().get(0);
        assertEquals(2, later.currentStepIndex(), "steps tick as the story's variables change");
        assertTrue(later.steps().get(0).done());
    }

    @Test
    void aQuestIsCompleteWhenEveryStepIsDone() {
        for (String v : List.of("met_keeper", "hedge_cleared", "strongbox_opened", "forged_sigil", "act1_complete")) {
            vars.set(v, true);
        }
        QuestLog.Quest errand = log.quests().get(0);
        assertTrue(errand.isComplete());
        assertEquals(5, errand.currentStepIndex());
    }

    @Test
    void theJournalFollowsTheCurrentAct() {
        state.setCurrentActId("act2");
        assertEquals("The road", log.quests().get(0).title());
        state.setCurrentActId("act9");
        assertTrue(log.quests().isEmpty(), "an act that isn't installed has no journal");
    }
}
