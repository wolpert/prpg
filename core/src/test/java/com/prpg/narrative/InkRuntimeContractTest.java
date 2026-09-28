package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bladecoder.ink.compiler.Compiler;
import com.bladecoder.ink.runtime.Story;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the blade-ink runtime behaviours the Ink-first engine depends on, so a library upgrade that
 * changes one of them fails here with a clear name rather than somewhere deep in the world screen.
 */
class InkRuntimeContractTest {

    private static Story compile(String source) throws Exception {
        Compiler.Options options = new Compiler.Options();
        options.sourceFilename = "contract.ink";
        return new Story(new Compiler(source, options).compile().toJson());
    }

    @Test
    void commandLineStopsContinueAndLaterLinesSeeVariablesSetMeanwhile() throws Exception {
        Story story = compile("""
                VAR activity_won = false
                -> go
                === go ===
                Before.
                >>> play match3 hedge
                {activity_won: Won.|Lost.}
                -> END
                """);
        assertEquals("Before.", story.Continue().trim());
        assertEquals(">>> play match3 hedge", story.Continue().trim());
        // The engine sets the result while the story is held on the command line.
        story.getVariablesState().set("activity_won", true);
        assertEquals("Won.", story.Continue().trim(), "lookahead past the command did not stick");
    }

    @Test
    void functionsCanBeEvaluatedMidConversationWithoutDisturbingIt() throws Exception {
        Story story = compile("""
                VAR door_open = false
                EXTERNAL place(id, knot)
                -> talk
                === function stage() ===
                { door_open:
                    ~ place("keeper", -> inside)
                - else:
                    ~ place("keeper", -> outside)
                }
                === function place(id, knot) ===
                ~ return
                === talk ===
                One.
                ~ door_open = true
                Two.
                -> END
                === inside ===
                In.
                -> END
                === outside ===
                Out.
                -> END
                """);
        List<String> placed = new ArrayList<>();
        story.bindExternalFunction("place",
                (Story.ExternalFunction<Object>) args -> {
                    placed.add(args[0] + "@" + args[1]);
                    return null;
                }, true);

        assertEquals("One.", story.Continue().trim());
        story.evaluateFunction("stage");
        assertEquals(List.of("keeper@outside"), placed, "a divert-target arg arrives as its knot path");

        assertEquals("Two.", story.Continue().trim(), "the conversation resumed where it was");
        placed.clear();
        story.evaluateFunction("stage");
        assertEquals(List.of("keeper@inside"), placed, "stage() sees state the conversation set");
    }

    @Test
    void globalTagsAfterIncludesAndVarsAreReadable() throws Exception {
        Story story = compile("""
                VAR x = 1
                # entry: gatehouse_yard start
                -> menu
                === menu ===
                Hi.
                -> END
                """);
        assertEquals(List.of("entry: gatehouse_yard start"), story.getGlobalTags());
    }

    @Test
    void globalVariablesAreEnumerableAndWritable() throws Exception {
        Story story = compile("""
                VAR met = false
                VAR count = 2
                VAR name = "x"
                Hi.
                """);
        List<String> names = new ArrayList<>();
        story.getVariablesState().forEach(names::add);
        assertTrue(names.containsAll(List.of("met", "count", "name")), names.toString());
        story.getVariablesState().set("met", true);
        assertEquals(true, story.getVariablesState().get("met"));
        assertEquals(2, story.getVariablesState().get("count"));
    }

    @Test
    void functionsEvaluateOnAFreshStoryAndAfterAConversationEnded() throws Exception {
        Story story = compile("""
                VAR n = 3
                -> menu
                === function twice() ===
                ~ return n * 2
                === menu ===
                Hi.
                -> END
                === talk ===
                ~ n = 5
                Bye.
                -> END
                """);
        assertEquals(6, story.evaluateFunction("twice"), "before anything has run");
        story.choosePathString("talk");
        while (story.canContinue()) story.Continue();
        assertEquals(10, story.evaluateFunction("twice"), "after the conversation reached END");
    }

    @Test
    void listValuesRoundTripThroughTheirFullItemNames() throws Exception {
        Story story = compile("""
                LIST mood = calm, (wary), angry
                VAR keeper_mood = wary
                Hi.
                """);
        Object value = story.getVariablesState().get("keeper_mood");
        assertTrue(value instanceof com.bladecoder.ink.runtime.InkList);
        com.bladecoder.ink.runtime.InkList list = (com.bladecoder.ink.runtime.InkList) value;
        assertEquals("mood.wary", list.keySet().iterator().next().getFullName());

        com.bladecoder.ink.runtime.InkList restored = new com.bladecoder.ink.runtime.InkList();
        restored.setInitialOriginNames(List.of("mood"));
        restored.addItem("mood.angry", story);
        story.getVariablesState().set("keeper_mood", restored);
        com.bladecoder.ink.runtime.InkList back =
                (com.bladecoder.ink.runtime.InkList) story.getVariablesState().get("keeper_mood");
        assertTrue(back.containsItemNamed("angry"));
    }

    @Test
    void hasFunctionAndKnotLookupDistinguishPresence() throws Exception {
        Story story = compile("""
                Hi.
                === function stage() ===
                ~ return
                === act_start ===
                Go.
                -> END
                """);
        assertTrue(story.hasFunction("stage"));
        assertFalse(story.hasFunction("journal"));
        assertTrue(story.getMainContentContainer().getNamedContent().containsKey("act_start"));
        assertFalse(story.getMainContentContainer().getNamedContent().containsKey("nope"));
    }
}
