package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bladecoder.ink.compiler.Compiler;
import com.bladecoder.ink.runtime.InkList;
import com.bladecoder.ink.runtime.Story;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The story-state store: values, truthiness, change tracking, new-game reset, and syncing with a Story. */
class StoryVariablesTest {

    private static Story compile(String source) throws Exception {
        Compiler.Options options = new Compiler.Options();
        options.sourceFilename = "vars.ink";
        return new Story(new Compiler(source, options).compile().toJson());
    }

    @Test
    void truthinessFollowsInk() {
        StoryVariables vars = new StoryVariables();
        vars.set("b", true);
        vars.set("zero", 0);
        vars.set("n", 2);
        vars.set("empty", "");
        vars.set("s", "x");
        assertTrue(vars.isTrue("b"));
        assertFalse(vars.isTrue("zero"));
        assertTrue(vars.isTrue("n"));
        assertFalse(vars.isTrue("empty"));
        assertTrue(vars.isTrue("s"));
        assertFalse(vars.isTrue("never_set"));
        assertEquals(2, vars.getInt("n"));
        assertEquals(1, vars.getInt("b"));
    }

    @Test
    void versionMovesOnlyOnARealChange() {
        StoryVariables vars = new StoryVariables();
        int v0 = vars.version();
        vars.set("a", true);
        int v1 = vars.version();
        vars.set("a", true);
        assertEquals(v1, vars.version(), "setting the same value is not a change");
        vars.set("a", false);
        assertTrue(vars.version() > v1 && v1 > v0);
    }

    @Test
    void clearRunKeepsMetaProfileState() {
        StoryVariables vars = new StoryVariables();
        vars.set("met_keeper", true);
        vars.set("meta_owns_act2", true);
        vars.clearRun();
        assertNull(vars.get("met_keeper"));
        assertTrue(vars.isTrue("meta_owns_act2"));
        vars.clear();
        assertNull(vars.get("meta_owns_act2"));
    }

    @Test
    void storedValuesWinOverAStorysDefaultsAndChangesComeBack() throws Exception {
        Story story = compile("""
                VAR met = false
                VAR day = 1
                VAR only_here = "x"
                -> go
                === go ===
                ~ day = day + 1
                Day {day}.
                -> END
                """);
        StoryVariables vars = new StoryVariables();
        vars.set("met", true);
        vars.set("day", 5);
        vars.set("engine_only", 9); // declared by no story: never pushed, never lost

        vars.pushInto(story);
        assertEquals(true, story.getVariablesState().get("met"));
        assertEquals("Day 6.", story.Continue().trim());

        vars.captureFrom(story);
        assertEquals(6, vars.getInt("day"));
        assertEquals("x", vars.get("only_here"), "the story's own defaults are captured too");
        assertEquals(9, vars.getInt("engine_only"));
    }

    @Test
    void listValuesSurviveTheRoundTrip() throws Exception {
        Story story = compile("""
                LIST mood = calm, wary, angry
                VAR keeper_mood = wary
                Hi.
                """);
        StoryVariables vars = new StoryVariables();
        vars.captureFrom(story);
        assertEquals(new StoryVariables.ListValue(List.of("mood"), List.of("mood.wary")), vars.get("keeper_mood"));

        vars.set("keeper_mood", new StoryVariables.ListValue(List.of("mood"), List.of("mood.angry")));
        vars.pushInto(story);
        InkList back = (InkList) story.getVariablesState().get("keeper_mood");
        assertTrue(back.containsItemNamed("angry"));
        assertFalse(back.containsItemNamed("wary"));
    }
}
