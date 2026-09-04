package com.prpg.quests.config;

import java.util.List;

public class QuestDefinition {
    public String id;
    public String title;
    /** Act this quest belongs to (for scoping the log to the current chapter). 0 = unscoped. */
    public int act;
    /** Flags that must all be set before the quest becomes available (else it's LOCKED/hidden). */
    public List<String> requires;
    /** Optional flag that marks the whole quest complete, independent of step flags. */
    public String complete_flag;
    /** Repeatable objectives (epilogue daily loop) re-arm after completion. Stub for now. */
    public boolean repeatable;
    public List<QuestStep> steps;

    public static class QuestStep {
        public String id;
        public String text;
        /** The flag whose presence marks this step complete. */
        public String flag;
    }
}
