package com.prpg.world.command;

import java.util.List;

/**
 * The story command vocabulary in one place: every name bound in {@code WorldModule}, with its
 * argument count, so the content validators can check {@code >>>} lines without the Dagger graph.
 */
public final class Commands {

    private Commands() {}

    /** A command's name and how many arguments it accepts. */
    public record Spec(String name, int minArgs, int maxArgs, String usage) {}

    public static final List<Spec> ALL = List.of(
            new Spec(PlayCommand.NAME, 2, 2, "play <type> <id>"),
            new Spec(GoCommand.NAME, 1, 2, "go <map> [<spawn>]"),
            new Spec(WaitCommand.NAME, 1, 1, "wait <seconds>"),
            new Spec(FadeCommand.NAME, 1, 2, "fade out|in [<seconds>]"),
            new Spec(CutsceneCommand.NAME, 1, 1, "cutscene <id>"));

    /** Parses a seconds argument, or {@code fallback} when it isn't a number. */
    static float seconds(String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
