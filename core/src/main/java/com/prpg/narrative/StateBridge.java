package com.prpg.narrative;

import com.bladecoder.ink.runtime.Story;
import com.prpg.items.Inventory;
import com.prpg.util.Log;
import com.prpg.world.FlagStore;
import com.prpg.world.GameClock;
import com.prpg.world.stage.StageDirector;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The only class that knows both Ink and the canonical Java model. {@link #install(Story)} binds every
 * Ink {@code EXTERNAL} declared in {@code ink/common/bridge.ink} (baseline pack), wiring an ephemeral
 * {@link Story} to the durable {@link NarrativeState} / {@link FlagStore} / {@link Inventory} /
 * {@link StageDirector}.
 *
 * <p><b>Lookahead safety:</b> read functions are bound lookahead-safe (Ink may evaluate them while
 * looking ahead for glue/choices); write functions are bound <em>not</em> lookahead-safe so a side
 * effect never double-fires during lookahead.
 *
 * <p>The three lists below are the contract {@code InkContentValidationTest} checks against the
 * {@code EXTERNAL} declarations. To add a function: declare it in {@code bridge.ink} (with an Inky
 * fallback), add its name here, and bind it in {@link #bindReads} or {@link #bindWrites}.
 */
@Singleton
public class StateBridge {

    /** Reads bound lookahead-safe. Public so the validation test can assert the contract. */
    public static final List<String> READ_FUNCTIONS = List.of(
            "has_flag", "flag_value", "has_item", "item_count",
            "act_unlocked", "owns_act", "current_act", "next_act_gate", "day", "staged_on");

    /** Writes bound NOT lookahead-safe. */
    public static final List<String> WRITE_FUNCTIONS = List.of(
            "set_flag", "set_flag_value", "clear_flag", "give_item", "take_item",
            "unlock_act", "advance_act", "advance_day",
            "place_actor", "remove_actor", "set_actor_dialogue", "set_solid");

    /**
     * Ink variables mirrored into the canonical model via observers. Empty by default; a game that
     * wants a "latch" (set once from Ink, remembered in Java) declares the VAR in {@code bridge.ink},
     * lists it here and handles it in {@link #observe}.
     */
    public static final List<String> OBSERVED_VARIABLES = List.of();

    private final NarrativeState state;
    private final FlagStore flags;
    private final Inventory inventory;
    private final GameClock clock;
    private final Entitlement entitlement;
    private final StageDirector stage;
    private final ActProgression progression;

    @Inject
    public StateBridge(NarrativeState state, FlagStore flags, Inventory inventory, GameClock clock,
                       Entitlement entitlement, StageDirector stage, ActProgression progression) {
        this.state = state;
        this.flags = flags;
        this.inventory = inventory;
        this.clock = clock;
        this.entitlement = entitlement;
        this.stage = stage;
        this.progression = progression;
    }

    /** Binds all external functions and variable observers onto {@code story}. */
    public void install(Story story) {
        try {
            bindReads(story);
            bindWrites(story);
            observe(story);
        } catch (Exception e) {
            throw new IllegalStateException("failed to install Ink state bridge", e);
        }
    }

    private void bindReads(Story story) throws Exception {
        read(story, "has_flag", args -> bool(flags.hasFlag(str(args, 0))));
        read(story, "flag_value", args -> flags.getValue(str(args, 0)));
        read(story, "has_item", args -> bool(inventory.has(str(args, 0), 1)));
        read(story, "item_count", args -> inventory.count(str(args, 0)));
        read(story, "act_unlocked", args -> bool(state.isActUnlocked(str(args, 0))));
        read(story, "owns_act", args -> bool(entitlement.owns(str(args, 0))));
        read(story, "current_act", args -> nullToEmpty(state.getCurrentActId()));
        read(story, "next_act_gate", args -> progression.evaluateAdvance().name());
        read(story, "day", args -> clock.getDay());
        read(story, "staged_on", args -> bool(stage.isOn(str(args, 0), str(args, 1))));
    }

    private void bindWrites(Story story) throws Exception {
        write(story, "set_flag", args -> flags.setFlag(str(args, 0)));
        write(story, "set_flag_value", args -> flags.setFlag(str(args, 0), toInt(arg(args, 1))));
        write(story, "clear_flag", args -> flags.clearFlag(str(args, 0)));
        write(story, "give_item", args -> inventory.add(str(args, 0), 1));
        write(story, "take_item", args -> inventory.remove(str(args, 0), 1));
        write(story, "unlock_act", args -> state.unlockAct(str(args, 0)));
        write(story, "advance_act", args -> progression.requestAdvance());
        write(story, "advance_day", args -> clock.advanceDay());
        // Staging writes: the escape hatch for moves the act's declarative when/unless rules can't
        // express. They land as overrides that beat the rules until the next act is staged.
        write(story, "place_actor", args -> stage.place(str(args, 0), str(args, 1), str(args, 2)));
        write(story, "remove_actor", args -> stage.remove(str(args, 0)));
        write(story, "set_actor_dialogue", args -> stage.setDialogue(str(args, 0), str(args, 1)));
        write(story, "set_solid", args -> stage.setSolid(str(args, 0), truthy(arg(args, 1))));
    }

    private void observe(Story story) throws Exception {
        // No observed variables by default. Example of the pattern:
        //   observeIfPresent(story, "some_latch", (name, value) -> { if (truthy(value)) state.latch(); });
        for (String var : OBSERVED_VARIABLES) {
            observeIfPresent(story, var, (name, value) ->
                    Log.debug("StateBridge", "observed var '" + name + "' = " + value + " (no handler)"));
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

    private void observeIfPresent(Story story, String var, Story.VariableObserver observer)
            throws Exception {
        // Observing a variable the story doesn't declare throws; guard so an act that omits a var
        // doesn't break install.
        if (story.getVariablesState().get(var) == null) {
            Log.debug("StateBridge", "skipping observer for absent var: " + var);
            return;
        }
        story.observeVariable(var, observer);
    }

    // --- arg / value coercion -----------------------------------------------------------------

    /** Raw arg at {@code i}, or null when Ink passed fewer. */
    private static Object arg(Object[] args, int i) {
        return args == null || i >= args.length ? null : args[i];
    }

    private static String str(Object[] args, int i) {
        if (args == null || i >= args.length) {
            // Wrong arg count from Ink: a bridge.ink/StateBridge contract mismatch, not normal flow.
            Log.error("StateBridge", "missing arg[" + i + "] for an Ink EXTERNAL; check the bridge.ink call site");
            return null;
        }
        return args[i] == null ? null : String.valueOf(args[i]);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Ink has no native boolean; conditions treat any non-zero number as true. */
    private static Integer bool(boolean b) {
        return b ? 1 : 0;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return value != null;
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof Boolean b) return b ? 1 : 0;
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
