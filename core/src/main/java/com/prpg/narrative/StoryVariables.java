package com.prpg.narrative;

import com.bladecoder.ink.runtime.InkList;
import com.bladecoder.ink.runtime.InkListItem;
import com.bladecoder.ink.runtime.Story;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The game's story state: every Ink global variable, kept by name. Ink declares the variables
 * ({@code VAR met_keeper = false}) and changes them; this class only remembers and shares them.
 *
 * <p><b>Why keep them outside the Story.</b> Each act is its own compiled story, and a DLC act may be
 * installed long after the base game. Values kept by name survive both: when {@link NarrativeRunner}
 * activates a story it {@link #pushInto pushes} every stored value the story declares, and after the
 * story runs it {@link #captureFrom captures} them back. Two acts share a variable by declaring it in
 * one shared file ({@code baseline/ink/common/world.ink}) that both INCLUDE; the content validators
 * make sure no two files declare the same name, so sharing is always deliberate.
 *
 * <p>Values are {@code Boolean}, {@code Integer}, {@code Float}, {@code String} or {@link ListValue}
 * (an Ink LIST). Names starting {@link #META_PREFIX} survive a new game (profile state such as
 * entitlement); everything else is wiped by {@link #clearRun()}. Engine-only state that no story
 * declares (for example {@code meta_owns_act2}) lives here too and is simply never pushed anywhere.
 */
@Singleton
public class StoryVariables {

    /** Prefix for profile state that survives a new playthrough. */
    public static final String META_PREFIX = "meta_";

    /** An Ink LIST value by its full item names ({@code mood.calm}) plus its origin list names. */
    public record ListValue(List<String> origins, List<String> items) {
        public ListValue {
            origins = List.copyOf(origins);
            items = List.copyOf(items);
        }
    }

    private final Map<String, Object> values = new LinkedHashMap<>();

    /** Bumped on every real change so per-frame consumers (staging, the HUD) can skip work. */
    private int version;

    @Inject
    public StoryVariables() {}

    public Object get(String name) {
        return name == null ? null : values.get(name);
    }

    public boolean has(String name) {
        return name != null && values.containsKey(name);
    }

    /** Ink truthiness: true, a non-zero number, a non-empty string or a non-empty list. */
    public boolean isTrue(String name) {
        return truthy(get(name));
    }

    public int getInt(String name) {
        Object v = get(name);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        return 0;
    }

    /** Stores {@code value} (see the class doc for the accepted types); a no-op when unchanged. */
    public void set(String name, Object value) {
        if (name == null) return;
        Object normalized = normalize(name, value);
        if (normalized == null) return;
        if (Objects.equals(values.get(name), normalized)) return;
        values.put(name, normalized);
        version++;
        Log.debug("StoryVariables", "'" + name + "' = " + normalized);
    }

    public void remove(String name) {
        if (name != null && values.remove(name) != null) version++;
    }

    /** Every stored variable, in first-set order. Read-only. */
    public Map<String, Object> all() {
        return Collections.unmodifiableMap(values);
    }

    /** Replaces everything (a save load). */
    public void loadAll(Map<String, Object> source) {
        values.clear();
        if (source != null) {
            for (Map.Entry<String, Object> e : source.entrySet()) {
                Object v = normalize(e.getKey(), e.getValue());
                if (v != null) values.put(e.getKey(), v);
            }
        }
        version++;
    }

    /** Wipes everything, including profile state. */
    public void clear() {
        values.clear();
        version++;
    }

    /** Wipes the playthrough but keeps {@link #META_PREFIX} profile state. */
    public void clearRun() {
        values.keySet().removeIf(k -> !k.startsWith(META_PREFIX));
        version++;
    }

    /** Monotonic change counter; equal across frames means nothing changed. */
    public int version() {
        return version;
    }

    // --- syncing with a Story ------------------------------------------------------------------

    /** Writes every stored value that {@code story} declares into it (stored state wins). */
    public void pushInto(Story story) {
        var state = story.getVariablesState();
        for (String name : declaredNames(story)) {
            Object stored = values.get(name);
            if (stored == null) continue;
            try {
                Object current = state.get(name);
                Object inkValue = toInk(stored, story);
                if (inkValue == null || Objects.equals(normalize(name, current), stored)) continue;
                state.set(name, inkValue);
            } catch (Exception e) {
                Log.error("StoryVariables", "could not push '" + name + "' = " + stored
                        + " into the story (type changed?); the story keeps its own value", e);
            }
        }
    }

    /** Reads every global {@code story} declares back into the store. */
    public void captureFrom(Story story) {
        var state = story.getVariablesState();
        for (String name : declaredNames(story)) {
            set(name, state.get(name));
        }
    }

    private static List<String> declaredNames(Story story) {
        List<String> names = new ArrayList<>();
        story.getVariablesState().forEach(names::add);
        return names;
    }

    // --- value conversion ----------------------------------------------------------------------

    private static Object normalize(String name, Object value) {
        if (value == null) return null;
        if (value instanceof Boolean || value instanceof Integer || value instanceof Float
                || value instanceof String || value instanceof ListValue) {
            return value;
        }
        if (value instanceof Double d) return d.floatValue();
        if (value instanceof Long l) return l.intValue();
        if (value instanceof InkList list) return fromInkList(list);
        // Divert targets and anything exotic can't be persisted by name; the story keeps its own.
        Log.debug("StoryVariables", "not storing '" + name + "' (unsupported type "
                + value.getClass().getSimpleName() + ")");
        return null;
    }

    private static ListValue fromInkList(InkList list) {
        List<String> items = new ArrayList<>();
        for (InkListItem item : list.keySet()) items.add(item.getFullName());
        Collections.sort(items);
        List<String> origins = list.getOriginNames() != null
                ? new ArrayList<>(list.getOriginNames()) : new ArrayList<>();
        Collections.sort(origins);
        return new ListValue(origins, items);
    }

    private static Object toInk(Object stored, Story story) throws Exception {
        if (!(stored instanceof ListValue lv)) return stored;
        InkList list = new InkList();
        list.setInitialOriginNames(lv.origins());
        for (String item : lv.items()) list.addItem(item, story);
        return list;
    }

    static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.floatValue() != 0f;
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof ListValue l) return !l.items().isEmpty();
        return false;
    }
}
