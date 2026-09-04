package com.prpg.world;

import com.prpg.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Story/progression flags: the canonical "what has happened" store. Names are namespaced by
 * convention ({@code act1.*} per act, {@code meta.*} persistent profile); flags under
 * {@link #META_PREFIX} survive a new game, everything else is wiped by {@link #clearRun()}.
 * Values are ints (a plain flag is 1), so counters live here too. Insertion order is preserved for
 * stable save output and HUD display.
 */
@Singleton
public class FlagStore {

    /** Prefix for durable profile flags that survive a new playthrough. */
    public static final String META_PREFIX = "meta.";

    private final Map<String, Integer> flags = new LinkedHashMap<>();

    /** Bumped on every mutation so per-frame consumers (the HUD, staging) can skip work when nothing changed. */
    private int version;

    @Inject
    public FlagStore() {}

    public void setFlag(String key) {
        setFlag(key, 1);
    }

    public void setFlag(String key, int value) {
        flags.put(key, value);
        version++;
        Log.debug("FlagStore", "set flag '" + key + "' = " + value);
    }

    /** Removes a flag entirely (so {@link #hasFlag} is false and {@link #getValue} is 0). */
    public void clearFlag(String key) {
        if (flags.remove(key) != null) {
            version++;
            Log.debug("FlagStore", "cleared flag '" + key + "'");
        }
    }

    public boolean hasFlag(String key) {
        return flags.getOrDefault(key, 0) > 0;
    }

    public int getValue(String key) {
        return flags.getOrDefault(key, 0);
    }

    /** Wipes everything, including persistent profile state. */
    public void clear() {
        flags.clear();
        version++;
    }

    /** Wipes per-run/per-act flags but keeps persistent {@link #META_PREFIX} profile state. */
    public void clearRun() {
        flags.keySet().removeIf(k -> !k.startsWith(META_PREFIX));
        version++;
    }

    public Map<String, Integer> getAll() {
        return flags;
    }

    public void loadAll(Map<String, Integer> source) {
        flags.clear();
        flags.putAll(source);
        version++;
    }

    /** Monotonic change counter; equal values across frames mean the flag set is unchanged. */
    public int getVersion() {
        return version;
    }
}
