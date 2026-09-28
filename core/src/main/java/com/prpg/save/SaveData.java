package com.prpg.save;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialized game state. Story state is the Ink variables, stored by name ({@link #variables}); the
 * staged world and the journal are re-derived from them on load, so neither is stored.
 */
public class SaveData {
    /**
     * Bumped when the save schema changes. Version 2 replaced flags, trigger history and staging
     * overrides with story variables; older saves are discarded on load (see {@code SaveManager}).
     */
    public static final int CURRENT_VERSION = 2;

    public int version = CURRENT_VERSION;
    public long lastPlayedMillis;
    public String mapId;
    public float playerX;
    public float playerY;
    public String facing;

    /** Every story variable (Ink global) by name, plus engine profile state such as entitlement. */
    public List<Variable> variables = new ArrayList<>();
    public List<Entry> inventory = new ArrayList<>();

    /** The act spine plus best-effort Ink resume tokens. */
    public Narrative narrative = new Narrative();

    /** An item id and count. */
    public static class Entry {
        public String key;
        public int value;

        public Entry() {}

        public Entry(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * One story variable. {@code type} is {@code bool}, {@code int}, {@code float}, {@code string} or
     * {@code list}; {@code value} is its text form (a list's full item names, comma separated) and
     * {@code origins} a list's origin LIST names.
     */
    public static class Variable {
        public String name;
        public String type;
        public String value;
        public List<String> origins = new ArrayList<>();
    }

    /** The act spine, serialized independently of any Ink story. */
    public static class Narrative {
        /** Null means "no game in progress"; the loader falls back to the catalog's first act. */
        public String currentAct;
        public List<String> unlockedActs = new ArrayList<>();
        /** Best-effort Ink resume tokens (visit counts etc.), one per loaded act. */
        public List<ActState> inkActStates = new ArrayList<>();
    }

    public static class ActState {
        public String actId;
        public String stateJson;
    }
}
