package com.prpg.quests;

import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.StoryCall;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The journal, derived from the current act's story. The act's Ink declares a {@code journal()}
 * function; the engine evaluates it whenever the journal is opened:
 *
 * <pre>
 * === function journal() ===
 * ~ quest("@quest.first_errand.title")          // a heading (an i18n key or a literal)
 * ~ step("Find the keeper in the yard.", met_keeper)   // a line and whether it is done
 * ~ step("Clear the hedge.", hedge_cleared)
 * { act1_complete:                               // a quest shows only when the story says so
 *     ~ quest("The road")
 *     ~ step("Find out where it leads.", false)
 * }
 * </pre>
 *
 * <p>A quest is complete when every step is done; its current step is the first one that isn't.
 * Nothing is stored: the journal is re-read from story state every time.
 */
@Singleton
public class QuestLog {

    public static final String JOURNAL_FUNCTION = "journal";

    /** One journal line. */
    public record Step(String text, boolean done) {}

    /** A heading and its steps, in the order the story listed them. */
    public record Quest(String title, List<Step> steps) {
        public boolean isComplete() {
            for (Step s : steps) {
                if (!s.done()) return false;
            }
            return true;
        }

        /** Index of the first step not done, or {@code steps.size()} when all are. */
        public int currentStepIndex() {
            for (int i = 0; i < steps.size(); i++) {
                if (!steps.get(i).done()) return i;
            }
            return steps.size();
        }
    }

    private final NarrativeRunner runner;
    private final NarrativeState state;

    @Inject
    public QuestLog(NarrativeRunner runner, NarrativeState state) {
        this.runner = runner;
        this.state = state;
    }

    /** The current act's journal, freshly evaluated. Empty when the act declares no journal(). */
    public List<Quest> quests() {
        List<String> titles = new ArrayList<>();
        List<List<Step>> steps = new ArrayList<>();
        String act = state.getCurrentActId();
        runner.evaluate(act, JOURNAL_FUNCTION, call -> collect(act, call, titles, steps));

        List<Quest> out = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            out.add(new Quest(titles.get(i), List.copyOf(steps.get(i))));
        }
        return out;
    }

    private static void collect(String act, StoryCall call, List<String> titles, List<List<Step>> steps) {
        switch (call.function()) {
            case "quest" -> {
                titles.add(call.arg(0) != null ? call.arg(0) : "");
                steps.add(new ArrayList<>());
            }
            case "step" -> {
                if (titles.isEmpty()) {
                    Log.info("QuestLog", act + " journal(): step(\"" + call.arg(0)
                            + "\") before any quest(); ignored");
                    return;
                }
                steps.get(steps.size() - 1).add(new Step(call.arg(0) != null ? call.arg(0) : "", call.flag(1)));
            }
            default -> Log.info("QuestLog", act + " journal() called " + call.function()
                    + "(...); only quest(title) and step(text, done) belong there");
        }
    }
}
