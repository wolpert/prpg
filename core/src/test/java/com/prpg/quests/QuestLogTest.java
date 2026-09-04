package com.prpg.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.quests.config.QuestDefinition;
import com.prpg.quests.config.QuestDefinition.QuestStep;
import com.prpg.world.FlagStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestLogTest {

    private FlagStore flags;
    private QuestLog log;

    @BeforeEach
    void setUp() {
        flags = new FlagStore();
        // state()/prerequisitesMet() operate on passed-in definitions, so loaders can be mocks.
        log = new QuestLog(mock(ConfigLoader.class), mock(ContentResolver.class), flags);
    }

    private static QuestDefinition quest(String... stepFlags) {
        QuestDefinition q = new QuestDefinition();
        q.id = "q";
        q.steps = new java.util.ArrayList<>();
        for (String f : stepFlags) {
            QuestStep s = new QuestStep();
            s.flag = f;
            q.steps.add(s);
        }
        return q;
    }

    @Test
    void lockedUntilPrerequisitesMet() {
        QuestDefinition q = quest("a2.step_one");
        q.requires = List.of("a1.saw_margaret");

        assertEquals(QuestLog.State.LOCKED, log.state(q));
        flags.setFlag("a1.saw_margaret");
        assertEquals(QuestLog.State.ACTIVE, log.state(q));
    }

    @Test
    void activeThenCompleteAsStepsFlip() {
        QuestDefinition q = quest("a1.read_will", "a1.met_elder");
        assertEquals(QuestLog.State.ACTIVE, log.state(q));
        assertEquals(0, log.currentStepIndex(q));

        flags.setFlag("a1.read_will");
        assertEquals(1, log.currentStepIndex(q));
        assertEquals(QuestLog.State.ACTIVE, log.state(q));

        flags.setFlag("a1.met_elder");
        assertEquals(QuestLog.State.COMPLETE, log.state(q));
    }

    @Test
    void completeFlagShortCircuitsSteps() {
        QuestDefinition q = quest("a1.read_will", "a1.met_elder");
        q.complete_flag = "a1.saw_margaret";
        flags.setFlag("a1.saw_margaret");
        assertEquals(QuestLog.State.COMPLETE, log.state(q));
    }
}
