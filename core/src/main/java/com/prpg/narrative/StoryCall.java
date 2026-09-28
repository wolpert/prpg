package com.prpg.narrative;

import java.util.List;

/**
 * One world-description call a story made while the engine evaluated one of its functions:
 * {@code ~ actor("keeper", "gatehouse_yard", "keeper_post", -> keeper_greeting)} inside
 * {@code stage()} arrives as {@code StoryCall("actor", ["keeper", "gatehouse_yard", "keeper_post",
 * "keeper_greeting"])}. Arguments are normalized to strings: a divert target becomes its knot path, a
 * boolean {@code "true"}/{@code "false"}.
 */
public record StoryCall(String function, List<String> args) {

    public StoryCall {
        args = List.copyOf(args);
    }

    /** Argument {@code i}, or null when the story passed fewer (or an empty string). */
    public String arg(int i) {
        if (i >= args.size()) return null;
        String a = args.get(i);
        return a == null || a.isEmpty() ? null : a;
    }

    /** Argument {@code i} as a boolean: {@code true}, or any non-zero number. */
    public boolean flag(int i) {
        String a = arg(i);
        if (a == null) return false;
        if ("true".equalsIgnoreCase(a)) return true;
        try {
            return Float.parseFloat(a) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
