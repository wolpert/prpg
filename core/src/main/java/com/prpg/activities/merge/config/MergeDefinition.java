package com.prpg.activities.merge.config;

import com.prpg.activities.config.OnCompleteConfig;
import java.util.List;
import java.util.Map;

/** A merge-ladder definition ({@code activities/merge/<id>.yaml}). */
public class MergeDefinition {
    public String id;
    /** Header shown above the board. */
    public String title = "Combine";
    /** Footer hint explaining the ladder to the player. */
    public String hint = "Drag two matching tiles together to combine them.";
    /** Board size; 4x4 by default. */
    public int width = 4;
    public int height = 4;
    public List<LadderEntry> ladder;
    public Map<String, Integer> starting_inventory;
    public WinConfig win;
    public OnCompleteConfig on_complete;

    public static class LadderEntry {
        public String id;
        /** Hex placeholder color for the tile. */
        public String color;
        /** Rank number shown on the tile. */
        public int tier;
        /** Two ids that combine into this entry; null for base (un-craftable) tiers. */
        public List<String> from;
    }

    public static class WinConfig {
        public String produce;
        public int count;
    }
}
