package com.prpg.quests;

import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.util.Log;
import com.prpg.quests.config.QuestDefinition;
import com.prpg.quests.config.QuestDefinition.QuestStep;
import com.prpg.world.FlagStore;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Loads every {@code quests/*.yaml} (discovered via {@link ContentResolver}) and reports per-step
 * completion derived from {@link FlagStore}. A step is complete when its flag is set; the current
 * step is the first incomplete one.
 */
@Singleton
public class QuestLog {

    private static final String QUEST_PREFIX = "quests/";

    private final ConfigLoader configLoader;
    private final ContentResolver content;
    private final FlagStore flagStore;
    private final List<QuestDefinition> quests = new ArrayList<>();
    private boolean loaded;

    @Inject
    public QuestLog(ConfigLoader configLoader, ContentResolver content, FlagStore flagStore) {
        this.configLoader = configLoader;
        this.content = content;
        this.flagStore = flagStore;
    }

    private void ensureLoaded() {
        if (loaded) return;
        // Sorted (by ContentResolver.list) for stable display order across runs.
        for (String path : content.list(QUEST_PREFIX)) {
            if (!path.endsWith(".yaml")) continue;
            try (Reader reader = content.resolve(path).reader()) {
                QuestDefinition def = configLoader.load(QuestDefinition.class, reader);
                if (def != null && def.steps != null) {
                    quests.add(def);
                } else {
                    Log.info("QuestLog", "skipping quest \"" + path + "\" — null definition or no steps");
                }
            } catch (Exception e) {
                Log.error("QuestLog", "failed to load quest " + path, e);
                throw new RuntimeException("failed to load quest " + path, e);
            }
        }
        loaded = true;
    }

    /** A quest's lifecycle state, derived from prerequisite and step flags. */
    public enum State { LOCKED, ACTIVE, COMPLETE }

    public List<QuestDefinition> getQuests() {
        ensureLoaded();
        return quests;
    }

    /** Quests the player should see in the journal — everything except still-locked entries. */
    public List<QuestDefinition> getVisibleQuests() {
        List<QuestDefinition> visible = new ArrayList<>();
        for (QuestDefinition q : getQuests()) {
            if (state(q) != State.LOCKED) visible.add(q);
        }
        return visible;
    }

    public boolean isStepComplete(QuestStep step) {
        return step.flag != null && flagStore.hasFlag(step.flag);
    }

    /** Index of the first incomplete step, or steps.size() if the quest is fully done. */
    public int currentStepIndex(QuestDefinition quest) {
        for (int i = 0; i < quest.steps.size(); i++) {
            if (!isStepComplete(quest.steps.get(i))) return i;
        }
        return quest.steps.size();
    }

    /** True once all prerequisite flags are set (an unscoped quest with no prerequisites is open). */
    public boolean prerequisitesMet(QuestDefinition quest) {
        if (quest.requires == null) return true;
        for (String flag : quest.requires) {
            if (!flagStore.hasFlag(flag)) return false;
        }
        return true;
    }

    public boolean isComplete(QuestDefinition quest) {
        if (quest.complete_flag != null && flagStore.hasFlag(quest.complete_flag)) return true;
        return currentStepIndex(quest) >= quest.steps.size();
    }

    public State state(QuestDefinition quest) {
        if (!prerequisitesMet(quest)) return State.LOCKED;
        if (isComplete(quest)) return State.COMPLETE;
        return State.ACTIVE;
    }
}
