package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.bladecoder.ink.compiler.Compiler;
import com.prpg.items.Inventory;
import com.prpg.narrative.content.ActContentSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How a {@code >>>} command line holds a conversation: the story stops on it, the overlay has nothing
 * to show, the command runs frame by frame, and the story resumes (seeing anything the command wrote
 * to story variables) the moment it finishes.
 */
class NarrativeRunnerCommandTest {

    private static final String STORY = """
            VAR activity_won = false
            -> DONE
            === play_then_branch ===
            Before.
            >>> slow 3
            { activity_won: Won. | Lost. }
            -> END
            === unknown_then_line ===
            >>> teleport_somewhere
            After.
            -> END
            === refused_then_line ===
            >>> refuse
            After.
            -> END
            === only_commands ===
            >>> slow 1
            -> END
            """;

    /** Finishes after {@code args[0]} updates and, when it finishes, reports a win. */
    private final class SlowCommand implements StoryCommand {
        int remaining;
        final List<List<String>> started = new ArrayList<>();

        @Override
        public boolean start(List<String> args) {
            started.add(args);
            remaining = Integer.parseInt(args.get(0));
            return true;
        }

        @Override
        public boolean update(float delta) {
            if (--remaining > 0) return true;
            vars.set("activity_won", true);
            return false;
        }
    }

    private StoryVariables vars;
    private SlowCommand slow;
    private NarrativeRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        vars = new StoryVariables();
        slow = new SlowCommand();
        Map<String, StoryCommand> commands = new LinkedHashMap<>();
        commands.put("slow", slow);
        commands.put("refuse", new StoryCommand() {
            @Override
            public boolean start(List<String> args) {
                return false;
            }

            @Override
            public boolean update(float delta) {
                return false;
            }
        });

        Compiler.Options options = new Compiler.Options();
        options.sourceFilename = "commands.ink";
        String json = new Compiler(STORY, options).compile().toJson();
        ActContentSource source = new ActContentSource() {
            @Override
            public boolean has(String actId) {
                return "t".equals(actId);
            }

            @Override
            public String read(String actId) {
                return json;
            }
        };
        runner = InkTestSupport.runner(source, new NarrativeState(), vars, mock(Inventory.class),
                id -> true, commands);
    }

    @Test
    void theStoryWaitsOnACommandAndResumesSeeingItsResult() {
        runner.start("t", "play_then_branch", null);
        assertEquals("Before.", runner.currentText());

        runner.advance();
        assertEquals(List.of(List.of("3")), slow.started, "the rest of the line is the args");
        assertTrue(runner.isActive(), "the conversation is still on");
        assertFalse(runner.isShowingLine(), "but there is nothing to show while it waits");
        assertEquals("", runner.currentText());

        runner.advance(); // the player can't skip a command
        runner.update(0.1f);
        runner.update(0.1f);
        assertFalse(runner.isShowingLine(), "still waiting after two of three frames");

        runner.update(0.1f);
        assertTrue(runner.isShowingLine());
        assertEquals("Won.", runner.currentText(), "the story read what the command wrote");
    }

    @Test
    void anUnknownCommandIsSkipped() {
        runner.start("t", "unknown_then_line", null);
        assertTrue(runner.isShowingLine());
        assertEquals("After.", runner.currentText());
    }

    @Test
    void aCommandThatCannotStartDoesNotHoldTheStory() {
        runner.start("t", "refused_then_line", null);
        assertEquals("After.", runner.currentText());
    }

    @Test
    void aConversationOfOnlyCommandsEndsWhenTheLastFinishes() {
        boolean[] ended = {false};
        runner.start("t", "only_commands", () -> ended[0] = true);
        assertTrue(runner.isActive());
        runner.update(0.1f);
        assertFalse(runner.isActive());
        assertTrue(ended[0], "the end callback ran");
    }

    @Test
    void commandLinesSplitIntoWords() {
        assertEquals(List.of("play", "match3", "hedge"), NarrativeRunner.parseCommand(">>>  play match3   hedge "));
        assertEquals(List.of(), NarrativeRunner.parseCommand(">>>"));
    }
}
