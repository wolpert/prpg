package com.prpg.narrative;

import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import com.prpg.narrative.content.ActContentSource;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The Ink analog of {@code DialogueRunner}: drives an act's compiled story through
 * {@code Continue()}/choices and exposes the current line, speaker, choices, and any fired event tag
 * to the overlay. Plays no part in owning durable state — all side effects flow through
 * {@link StateBridge} into the canonical model.
 *
 * <p><b>One {@link Story} per act, cached.</b> A conversation is a divert into a knot on the act's
 * persistent story, so Ink's stateful features (visit counts, sequences, the once-only Act IV reveal)
 * carry across conversations within an act — and the per-act state can be snapshotted for save/resume.
 * Caching does not pin durable state: the canonical model is separate and authoritative.
 *
 * <p>Headless-safe: depends only on an {@link ActContentSource} and the bridge, so tests drive it with
 * an in-memory source (including the DLC-seam test, which supplies only a DLC act).
 */
@Singleton
public class NarrativeRunner {

    private final ActContentSource source;
    private final StateBridge bridge;

    /** Persistent per-act stories, keyed by act id. */
    private final Map<String, Story> stories = new LinkedHashMap<>();

    private Story activeStory;
    private String activeActId;
    private Runnable onEnd;

    private String currentText = "";
    private String currentSpeaker = "";
    private final List<String> currentChoices = new ArrayList<>();
    private String pendingEvent;

    /** Bumped whenever the displayed line/choices change, so the overlay rebuilds only on change. */
    private int turn;

    @Inject
    public NarrativeRunner(ActContentSource source, StateBridge bridge) {
        this.source = source;
        this.bridge = bridge;
    }

    // --- lifecycle ----------------------------------------------------------------------------

    /**
     * Starts the knot {@code knot} in act {@code actId}, diverting the (cached) act story to it and
     * advancing to the first line. Returns {@code false} (and starts nothing) if the act's content
     * isn't installed or the knot can't be reached — the base game gates gracefully on missing DLC.
     */
    public boolean start(String actId, String knot, Runnable onEnd) {
        Story story = storyFor(actId);
        if (story == null) {
            Log.info("NarrativeRunner",
                    "cannot start knot '" + knot + "' — act '" + actId + "' content not installed (DLC not mounted?)");
            return false;
        }
        try {
            story.choosePathString(knot);
        } catch (Exception e) {
            Log.error("NarrativeRunner", "no such knot '" + knot + "' in " + actId, e);
            return false;
        }
        this.activeStory = story;
        this.activeActId = actId;
        this.onEnd = onEnd;
        this.pendingEvent = null;
        pump();
        return true;
    }

    public boolean isActive() {
        return activeStory != null;
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

    /** A one-shot scripted-event id surfaced from an {@code # event: ...} tag, consumed once. */
    public String consumePendingEvent() {
        String e = pendingEvent;
        pendingEvent = null;
        return e;
    }

    /** Advance past the current line (the "space to continue" path). No-op while choices are pending. */
    public void advance() {
        if (activeStory == null || hasChoices()) return;
        pump();
    }

    public void selectChoice(int index) {
        if (activeStory == null || index < 0 || index >= currentChoices.size()) return;
        try {
            activeStory.chooseChoiceIndex(index);
        } catch (Exception e) {
            throw new IllegalStateException("failed to choose Ink choice " + index, e);
        }
        pump();
    }

    // --- pumping ------------------------------------------------------------------------------

    /** Advances to the next non-blank line, or ends if nothing more flows and there are no choices. */
    private void pump() {
        if (activeStory == null) return;
        currentSpeaker = "";
        String text = "";
        while (activeStory.canContinue()) {
            text = safeContinue();
            captureTags();
            text = text == null ? "" : text.trim();
            if (!text.isEmpty()) break;
        }
        currentText = text;
        refreshChoices();
        turn++;
        if (currentText.isEmpty() && currentChoices.isEmpty()) {
            end();
        }
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
                } else if ("event".equals(key)) {
                    pendingEvent = value;
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

    // --- story loading + save/resume ----------------------------------------------------------

    private Story storyFor(String actId) {
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

    /** Snapshots each loaded act's Ink state as a best-effort resume token (see save/load §7). */
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
     * blob is version-incompatible, it is dropped silently — the canonical model is authoritative, so
     * the act simply re-enters from its start. Returns whether the state was applied.
     */
    public boolean restoreActState(String actId, String stateJson) {
        if (stateJson == null) return false;
        Story story = storyFor(actId);
        if (story == null) {
            Log.debug("NarrativeRunner",
                    "no Ink story for act '" + actId + "'; dropping saved resume token (canonical state authoritative)");
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
        currentText = "";
        currentSpeaker = "";
        currentChoices.clear();
        pendingEvent = null;
    }
}
