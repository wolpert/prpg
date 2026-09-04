package com.prpg.save;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialized game state. Quest progress and activity completion are derived from {@link #flags}, and
 * declarative staging is re-derived from them on load, so neither is stored.
 */
public class SaveData {
    /** Bumped when the save schema changes; {@code SaveManager.migrate} upgrades older saves on load. */
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public int day = 1;
    public long lastPlayedMillis;
    public String mapId;
    public float playerX;
    public float playerY;
    public String facing;
    public List<Entry> flags = new ArrayList<>();
    public List<Entry> inventory = new ArrayList<>();
    public List<String> triggerHistory = new ArrayList<>();

    /** Canonical narrative state (durable) plus best-effort Ink resume tokens. */
    public Narrative narrative = new Narrative();

    /** The act whose staging is applied (normally equals {@code narrative.currentAct}). */
    public String stagedAct;

    /**
     * Imperative staging overrides only. Declarative placements are a pure function of {@link #flags}
     * and re-derived on load, so editing an act's staging file fixes existing saves.
     */
    public List<StagedOverride> staged = new ArrayList<>();

    public static class Entry {
        public String key;
        public int value;

        public Entry() {}

        public Entry(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    /** The durable narrative model, serialized independently of any Ink story. */
    public static class Narrative {
        /** Null means "no game in progress"; the loader falls back to the catalog's first act. */
        public String currentAct;
        public List<String> unlockedActs = new ArrayList<>();
        /** Best-effort Ink resume tokens, one per loaded act. Dropped silently if incompatible. */
        public List<ActState> inkActStates = new ArrayList<>();
    }

    public static class ActState {
        public String actId;
        public String stateJson;
    }

    /** One staged thing Ink moved, removed, re-voiced, or re-solidified. Mirrors StageDirector.StagedOverride. */
    public static class StagedOverride {
        public String id;
        public boolean removed;
        public String mapId;
        public String marker;
        public String dialogue;
        /** Boxed: null means "solidity was never overridden", which differs from "made passable". */
        public Boolean solid;
    }
}
