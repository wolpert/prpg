package com.prpg.narrative;

import com.bladecoder.ink.runtime.Story;
import com.prpg.items.Inventory;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Binds every Ink {@code EXTERNAL} declared in {@code ink/common/bridge.ink} (baseline pack) onto a
 * {@link Story}. Story state itself needs no bridge: it is plain Ink variables, synced by
 * {@link StoryVariables}. The bridge covers only what Ink can't hold: the inventory, the act spine,
 * and the calls a story makes to describe the world.
 *
 * <p><b>Three kinds of function.</b> Reads are bound lookahead-safe (Ink may evaluate them while
 * looking ahead for glue); writes are bound <em>not</em> lookahead-safe, so a side effect never
 * double-fires. World functions ({@code actor}, {@code thing}, {@code quest}, ...) are how a story's
 * {@code stage()}, {@code cast()} and {@code journal()} functions describe the world: the engine
 * evaluates one of those functions with {@link #collect} active and receives each call as a
 * {@link StoryCall}. Outside such an evaluation they do nothing but log.
 *
 * <p>The three lists below are the contract {@code InkContentValidationTest} checks against the
 * {@code EXTERNAL} declarations. To add a function: declare it (with an Inky fallback) in
 * {@code bridge.ink}, add its name here, and bind it.
 */
@Singleton
public class StateBridge {

    /** Reads bound lookahead-safe. Public so the validation test can assert the contract. */
    public static final List<String> READ_FUNCTIONS = List.of(
            "has_item", "item_count", "act_unlocked", "owns_act", "current_act", "next_act_gate");

    /** Writes bound NOT lookahead-safe. */
    public static final List<String> WRITE_FUNCTIONS = List.of(
            "give_item", "take_item", "unlock_act", "advance_act");

    /** World-description calls, meaningful only inside stage() / cast() / journal(). */
    public static final List<String> WORLD_FUNCTIONS = List.of(
            "actor", "thing", "zone", "lock", "define", "quest", "step");

    private final NarrativeState state;
    private final Inventory inventory;
    private final Entitlement entitlement;
    private final ActProgression progression;

    /** Receives world calls while the engine evaluates a world-description function; else null. */
    private Consumer<StoryCall> sink;

    @Inject
    public StateBridge(NarrativeState state, Inventory inventory, Entitlement entitlement,
                       ActProgression progression) {
        this.state = state;
        this.inventory = inventory;
        this.entitlement = entitlement;
        this.progression = progression;
    }

    /** Binds all external functions onto {@code story}. */
    public void install(Story story) {
        try {
            bindReads(story);
            bindWrites(story);
            bindWorld(story);
        } catch (Exception e) {
            throw new IllegalStateException("failed to install Ink state bridge", e);
        }
    }

    /**
     * Runs {@code evaluation} with world calls routed to {@code target}. Used by
     * {@link NarrativeRunner#evaluate}; not re-entrant (the engine evaluates one function at a time).
     */
    void collect(Consumer<StoryCall> target, Runnable evaluation) {
        Consumer<StoryCall> previous = sink;
        sink = target;
        try {
            evaluation.run();
        } finally {
            sink = previous;
        }
    }

    private void bindReads(Story story) throws Exception {
        read(story, "has_item", args -> bool(inventory.has(str(args, 0), 1)));
        read(story, "item_count", args -> inventory.count(str(args, 0)));
        read(story, "act_unlocked", args -> bool(state.isActUnlocked(str(args, 0))));
        read(story, "owns_act", args -> bool(entitlement.owns(str(args, 0))));
        read(story, "current_act", args -> nullToEmpty(state.getCurrentActId()));
        read(story, "next_act_gate", args -> progression.evaluateAdvance().name());
    }

    private void bindWrites(Story story) throws Exception {
        write(story, "give_item", args -> {
            if (!inventory.add(str(args, 0), 1)) {
                Log.error("StateBridge", "give_item(\"" + str(args, 0)
                        + "\") not granted (unknown item id or inventory full)");
            }
        });
        write(story, "take_item", args -> inventory.remove(str(args, 0), 1));
        write(story, "unlock_act", args -> state.unlockAct(str(args, 0)));
        write(story, "advance_act", args -> progression.requestAdvance());
    }

    private void bindWorld(Story story) throws Exception {
        for (String name : WORLD_FUNCTIONS) {
            write(story, name, args -> {
                if (sink == null) {
                    Log.error("StateBridge", name + "(...) called outside stage()/cast()/journal(); ignored."
                            + " World calls only describe the world while the engine evaluates those functions.");
                    return;
                }
                sink.accept(new StoryCall(name, strings(args)));
            });
        }
    }

    // --- binding helpers ----------------------------------------------------------------------

    /** A read returns a value (0/1 for booleans, an int, or a string) and is bound lookahead-safe. */
    private interface ReadFn {
        Object call(Object[] args);
    }

    /** A write performs a side effect and returns nothing; bound NOT lookahead-safe. */
    private interface WriteFn {
        void call(Object[] args);
    }

    private void read(Story story, String name, ReadFn fn) throws Exception {
        story.bindExternalFunction(name, (Story.ExternalFunction<Object>) fn::call, true);
    }

    private void write(Story story, String name, WriteFn fn) throws Exception {
        story.bindExternalFunction(name, (Story.ExternalFunction<Object>) args -> {
            fn.call(args);
            return null;
        }, false);
    }

    // --- arg / value coercion -----------------------------------------------------------------

    private static String str(Object[] args, int i) {
        if (args == null || i >= args.length) {
            // Wrong arg count from Ink: a bridge.ink/StateBridge contract mismatch, not normal flow.
            Log.error("StateBridge", "missing arg[" + i + "] for an Ink EXTERNAL; check the bridge.ink call site");
            return null;
        }
        return args[i] == null ? null : String.valueOf(args[i]);
    }

    /** Every arg as a string: a divert target's {@code toString} is its knot path. */
    private static List<String> strings(Object[] args) {
        List<String> out = new ArrayList<>();
        if (args == null) return out;
        for (Object a : args) out.add(a == null ? null : String.valueOf(a));
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Ink conditions treat any non-zero number as true. */
    private static Integer bool(boolean b) {
        return b ? 1 : 0;
    }
}
