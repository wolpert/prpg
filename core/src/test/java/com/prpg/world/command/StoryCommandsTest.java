package com.prpg.world.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prpg.activities.ActivityLauncher;
import com.prpg.narrative.StoryVariables;
import com.prpg.world.WorldTravel;
import com.prpg.world.scene.ScriptedEvent;
import com.prpg.world.scene.SceneDirector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Each story command: what it starts, how long it holds the story, and what it leaves behind. */
class StoryCommandsTest {

    @Test
    void playWaitsForTheActivityThenReportsTheResult() {
        ActivityLauncher launcher = mock(ActivityLauncher.class);
        StoryVariables vars = new StoryVariables();
        when(launcher.launch("match3", "hedge")).thenReturn(true);
        when(launcher.isRunning()).thenReturn(true, true, false);
        when(launcher.wasWon()).thenReturn(true);
        PlayCommand play = new PlayCommand(launcher, vars);

        assertTrue(play.start(List.of("match3", "hedge")));
        assertFalse(vars.isTrue(PlayCommand.RESULT_VARIABLE), "no stale win while playing");
        assertTrue(play.update(0.1f));
        assertTrue(play.update(0.1f));
        assertFalse(play.update(0.1f));
        assertTrue(vars.isTrue(PlayCommand.RESULT_VARIABLE));
    }

    @Test
    void playThatCannotLaunchIsALossAndDoesNotHold() {
        ActivityLauncher launcher = mock(ActivityLauncher.class);
        StoryVariables vars = new StoryVariables();
        vars.set(PlayCommand.RESULT_VARIABLE, true);
        when(launcher.launch("match3", "missing")).thenThrow(new RuntimeException("no such file"));
        PlayCommand play = new PlayCommand(launcher, vars);

        assertFalse(play.start(List.of("match3", "missing")));
        assertFalse(vars.isTrue(PlayCommand.RESULT_VARIABLE));
        assertFalse(play.start(List.of("match3")), "missing id");
    }

    @Test
    void goRequestsTravelAndWaitsUntilTheWorldHasMoved() {
        WorldTravel travel = new WorldTravel();
        GoCommand go = new GoCommand(travel);
        assertTrue(go.start(List.of("gatehouse_hall", "from_yard")));
        assertEquals("gatehouse_hall", travel.map());
        assertEquals("from_yard", travel.spawn());
        assertTrue(go.update(0.1f));
        travel.clear(); // WorldScreen built the new map
        assertFalse(go.update(0.1f));
        assertFalse(go.start(List.of()));
    }

    @Test
    void waitCountsDownRealTime() {
        WaitCommand wait = new WaitCommand();
        assertTrue(wait.start(List.of("0.5")));
        assertTrue(wait.update(0.3f));
        assertFalse(wait.update(0.3f));
        assertFalse(wait.start(List.of("soon")), "not a number");
        assertFalse(wait.start(List.of("0")), "nothing to wait for");
    }

    @Test
    void fadeMovesTheVeilOverTimeAndZeroIsInstant() {
        SceneDirector director = new SceneDirector(Map.of());
        FadeCommand fade = new FadeCommand(director);

        assertTrue(fade.start(List.of("out", "1")));
        assertTrue(fade.update(0.5f));
        assertEquals(0.5f, director.fadeAlpha(), 1e-4);
        assertFalse(fade.update(0.5f));
        assertEquals(1f, director.fadeAlpha(), 1e-4);

        assertFalse(fade.start(List.of("in", "0")), "a zero-length fade doesn't hold the story");
        assertEquals(0f, director.fadeAlpha(), 1e-4);
        assertFalse(fade.start(List.of("sideways")));
    }

    @Test
    void cutsceneRunsARegisteredEventToTheEnd() {
        ScriptedEvent twoFrames = new ScriptedEvent() {
            int frames;

            @Override
            public void begin(SceneDirector director) {
                frames = 2;
            }

            @Override
            public boolean update(float delta) {
                return --frames > 0;
            }
        };
        SceneDirector director = new SceneDirector(Map.of("beat", twoFrames));
        CutsceneCommand cutscene = new CutsceneCommand(director);

        assertTrue(cutscene.start(List.of("beat")));
        director.update(0.1f);
        assertTrue(cutscene.update(0.1f));
        director.update(0.1f);
        assertFalse(cutscene.update(0.1f));
        assertFalse(cutscene.start(List.of("unregistered")));
    }

    @Test
    void theVocabularyListsEveryCommandOnce() {
        assertEquals(List.of("play", "go", "wait", "fade", "cutscene"),
                Commands.ALL.stream().map(Commands.Spec::name).toList());
    }
}
