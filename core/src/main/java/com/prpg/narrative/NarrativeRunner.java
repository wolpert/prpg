package com.prpg.narrative;

import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import com.prpg.narrative.content.ActContentSource;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Drives an act's compiled Ink story: conversations (a divert into a knot, advanced line by line),
 * command lines ({@code >>> play match3 hedge}, handed to a registered {@link StoryCommand} while the
 * story waits), and function evaluation ({@code stage()}, {@code cast()}, {@code journal()}), which is
 * how the engine asks the story what the world looks like right now.
 *
 * <p><b>One {@link Story} per act, cached.</b> Ink's own bookkeeping (visit counts, sequences) carries
 * across conversations within an act and is snapshotted into the save as a best-effort resume token.
 * Story <em>variables</em> are synced with {@link StoryVariables} on every activation and after every
 * step, so they survive content updates and are shared between acts by name.
 *
 * <p>Headless-safe: depends only on an {@link ActContentSource}, the bridge and the command map, so
 * tests drive it with an in-memory source (including the DLC-seam test, which supplies only a DLC act).
 */
@Singleton
public class NarrativeRunner {

    /** Prefix that marks a line as a command for the engine rather than text for the player. */
    public static final String COMMAND_PREFIX = ">>>";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final ActContentSource source;
    private final StateBridge bridge;
    private final StoryVariables variables;
    private final Map<String, StoryCommand> commands;

    /** Persistent per-act stories, keyed by act id. */
    private final Map<String, Story> stories = new LinkedHashMap<>();

    private Story activeStory;
    private String activeActId;
    private Runnable onEnd;
    private StoryCommand runningCommand;

    private String currentText = "";
    private String currentSpeaker = "";
    private final List<String> currentChoices = new ArrayList<>();

    /** Bumped whenever the displayed line/choices change, so the overlay rebuilds only on change. */
    private int turn;

    @Inject
    public NarrativeRunner(ActContentSource source, StateBridge bridge, StoryVariables variables,
                           Map<String, StoryCommand> commands) {
        this.source = source;
        this.bridge = bridge;
        this.variables = variables;
        this.commands = commands;
    }

    // --- conversations ------------------------------------------------------------------------

    /**
     * Starts the knot {@code knot} in act {@code actId}, diverting the (cached) act story to it and
     * advancing to the first line. Returns {@code false} (and starts nothing) if the act's content
     * isn't installed or the knot can't be reached: the base game gates gracefully on missing DLC.
     */
    public boolean start(String actId, String knot, Runnable onEnd) {
        Story story = storyFor(actId);
        if (story == null) {
            Log.info("NarrativeRunner",
                    "cannot start knot '" + knot + "': act '" + actId + "' content not installed (DLC not mounted?)");
            return false;
        }
        variables.pushInto(story);
        try {
            story.choosePathString(knot);
        } catch (Exception e) {
            Log.error("NarrativeRunner", "no such knot '" + knot + "' in " + actId, e);
            return false;
        }
        this.activeStory = story;
        this.activeActId = actId;
        this.onEnd = onEnd;
        this.runningCommand = null;
        pump();
        return true;
    }

    /** True from {@link #start} until the conversation ends, including while a command runs. */
    public boolean isActive() {
        return activeStory != null;
    }

    /** True while a line (or choices) is on screen: active and not waiting on a command. */
    public boolean isShowingLine() {
        return activeStory != null && runningCommand == null;
    }

    /** Monotonic counter that changes each time the displayed line/choices change. */
    public int turn() {
        return turn;
    }

    public String currentText() {
        return currentText;
    }

    public String currentSpeaker() {
        return currentSpeaker;
    }

    public List<String> choiceTexts() {
        return currentChoices;
    }

    public boolean hasChoices() {
        return !currentChoices.isEmpty();
    }

    /** Advance past the current line (the "space to continue" path). No-op while choices or a command wait. */
    public void advance() {
        if (!isShowingLine() || hasChoices()) return;
        pump();
    }

    public void selectChoice(int index) {
        if (!isShowingLine() || index < 0 || index >= currentChoices.size()) return;
        try {
            activeStory.chooseChoiceIndex(index);
        } catch (Exception e) {
            throw new IllegalStateException("failed to choose Ink choice " + index, e);
        }
        pump();
    }

    /** Per-frame drive for a running command; resumes the story once it finishes. */
    public void update(float delta) {
        if (runningCommand == null) return;
        boolean stillRunning;
        try {
            stillRunning = runningCommand.update(delta);
        } catch (RuntimeException e) {
            Log.error("NarrativeRunner", "command failed in " + activeActId + "; continuing the story", e);
            stillRunning = false;
        }
        if (stillRunning) return;
        runningCommand = null;
        if (activeStory != null) {
            variables.pushInto(activeStory); // a command may have written a result (activity_won)
            pump();
        }
    }

    // --- pumping ------------------------------------------------------------------------------

    /**
     * Advances to the next line the player should see, running any command lines on the way. Ends the
     * conversation when nothing more flows and there are no choices.
     */
    private void pump() {
        if (activeStory == null) return;
        currentSpeaker = "";
        String text = "";
        while (activeStory.canContinue()) {
            text = safeContinue();
            text = text == null ? "" : text.trim();
            if (text.startsWith(COMMAND_PREFIX)) {
                variables.captureFrom(activeStory);
                boolean waiting = startCommand(text);
                text = "";
                if (waiting) {
                    currentChoices.clear();
                    currentText = "";
                    turn++;
                    return; // update() resumes the story once the command finishes
                }
                continue;
            }
            captureTags();
            if (!text.isEmpty()) break;
        }
        currentText = text;
        refreshChoices();
        variables.captureFrom(activeStory);
        turn++;
        if (currentText.isEmpty() && currentChoices.isEmpty()) {
            end();
        }
    }

    /** Starts the command on {@code line}; returns true if the story must now wait for it. */
    private boolean startCommand(String line) {
        List<String> words = parseCommand(line);
        if (words.isEmpty()) {
            Log.error("NarrativeRunner", "empty command line '" + line + "' in " + activeActId + "; skipped");
            return false;
        }
        String name = words.get(0);
        StoryCommand command = commands.get(name);
        if (command == null) {
            Log.error("NarrativeRunner", "unknown command '" + name + "' in " + activeActId
                    + " (known: " + commands.keySet() + "); skipped");
            return false;
        }
        boolean started;
        try {
            started = command.start(words.subList(1, words.size()));
        } catch (RuntimeException e) {
            Log.error("NarrativeRunner", "command '" + line + "' failed to start; skipped", e);
            started = false;
        }
        if (started) runningCommand = command;
        return started;
    }

    /** {@code ">>> play match3 hedge"} to {@code [play, match3, hedge]}. */
    public static List<String> parseCommand(String line) {
        String body = line.trim();
        if (body.startsWith(COMMAND_PREFIX)) body = body.substring(COMMAND_PREFIX.length()).trim();
        if (body.isEmpty()) return List.of();
        return Arrays.asList(WHITESPACE.split(body));
    }

    private String safeContinue() {
        try {
            return activeStory.Continue();
        } catch (Exception e) {
            throw new IllegalStateException("Ink Continue() failed in " + activeActId, e);
        }
    }

    private void captureTags() {
        try {
            List<String> tags = activeStory.getCurrentTags();
            if (tags == null) return;
            for (String tag : tags) {
                int colon = tag.indexOf(':');
                if (colon < 0) continue;
                String key = tag.substring(0, colon).trim();
                String value = tag.substring(colon + 1).trim();
                if ("speaker".equals(key)) {
                    currentSpeaker = value;
                }
                // portrait / mood and other tags are reserved for the art pass; ignored for now.
            }
        } catch (Exception e) {
            Log.error("NarrativeRunner", "failed to read tags", e);
        }
    }

    private void refreshChoices() {
        currentChoices.clear();
        if (activeStory == null) return;
        for (Choice c : activeStory.getCurrentChoices()) {
            currentChoices.add(c.getText());
        }
    }

    private void end() {
        // The story stays cached (visit counts / sequences persist); only the active reference clears.
        activeStory = null;
        activeActId = null;
        runningCommand = null;
        currentText = "";
        currentSpeaker = "";
        currentChoices.clear();
        turn++;
        if (onEnd != null) {
            Runnable cb = onEnd;
            onEnd = null;
            cb.run();
        }
    }

    // --- asking the story about the world -----------------------------------------------------

    /**
     * Evaluates the Ink function {@code function} (e.g. {@code stage}) in act {@code actId}, handing
     * every world call it makes to {@code sink}. Safe mid-conversation: evaluation does not move the
     * conversation. Returns false when the act or the function doesn't exist (a story need not
     * declare every function).
     */
    public boolean evaluate(String actId, String function, Consumer<StoryCall> sink) {
        Story story = storyFor(actId);
        if (story == null || !story.hasFunction(function)) return false;
        variables.pushInto(story); // stored state wins, even over the active conversation's copy
        boolean[] ok = {true};
        bridge.collect(sink, () -> {
            try {
                story.evaluateFunction(function);
            } catch (Exception e) {
                Log.error("NarrativeRunner", "evaluating " + function + "() in " + actId + " failed", e);
                ok[0] = false;
            }
        });
        return ok[0];
    }

    /** Whether act {@code actId}'s story has a knot named {@code knot}. */
    public boolean hasKnot(String actId, String knot) {
        Story story = storyFor(actId);
        return story != null && knot != null
                && story.getMainContentContainer().getNamedContent().containsKey(knot);
    }

    // --- story loading + save/resume ----------------------------------------------------------

    private Story storyFor(String actId) {
        if (actId == null) return null;
        Story cached = stories.get(actId);
        if (cached != null) return cached;
        if (!source.has(actId)) {
            Log.debug("NarrativeRunner", "no content source for act '" + actId + "'");
            return null;
        }
        Story story;
        try {
            story = new Story(source.read(actId));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load Ink act '" + actId + "'", e);
        }
        bridge.install(story);
        stories.put(actId, story);
        return story;
    }

    /** Snapshots each loaded act's Ink state as a best-effort resume token (visit counts etc.). */
    public Map<String, String> captureActStates() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Story> e : stories.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().getState().toJson());
            } catch (Exception ex) {
                Log.error("NarrativeRunner", "failed to snapshot " + e.getKey(), ex);
            }
        }
        return out;
    }

    /**
     * Restores a previously snapshotted Ink act state. Best-effort: if the act isn't installed or the
     * blob is version-incompatible, it is dropped (story variables are restored separately, by name,
     * from {@link StoryVariables}, so only Ink's own bookkeeping is lost). Returns whether it applied.
     */
    public boolean restoreActState(String actId, String stateJson) {
        if (stateJson == null) return false;
        Story story = storyFor(actId);
        if (story == null) {
            Log.debug("NarrativeRunner",
                    "no Ink story for act '" + actId + "'; dropping saved resume token");
            return false;
        }
        try {
            story.getState().loadJson(stateJson);
            return true;
        } catch (Exception e) {
            Log.error("NarrativeRunner", "dropping incompatible Ink state for " + actId, e);
            return false;
        }
    }

    /** Clears all loaded stories (e.g. on a new game) so no stale Ink state leaks across playthroughs. */
    public void clear() {
        stories.clear();
        activeStory = null;
        activeActId = null;
        onEnd = null;
        runningCommand = null;
        currentText = "";
        currentSpeaker = "";
        currentChoices.clear();
    }
}
