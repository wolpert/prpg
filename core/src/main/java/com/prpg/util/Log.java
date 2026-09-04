package com.prpg.util;

import com.badlogic.gdx.Gdx;

/**
 * Thin wrapper over {@code Gdx.app} logging that is null-safe for headless tests (where
 * {@code Gdx.app == null}) and centralizes the project's level conventions (see
 * {@code docs/logging.md}). Tag is the caller's class simple name as a literal; messages should name
 * the offending id/path (and, for recoverable warnings, the fallback taken).
 *
 * <p>libGDX has only three levels — error / log(info) / debug. A <em>warning</em> is an
 * {@link #error} call that does not throw and whose message names the degradation, so it still
 * surfaces at the default {@code LOG_INFO} level (a {@code debug} warning would vanish in release).
 *
 * <p>Funnel all logging through here rather than raw {@code Gdx.app.*}, which reintroduces the
 * null-guard footgun and bypasses the level convention. The two {@code Gdx.app != null} checks in
 * {@code ContentRoot} and {@code SaveManager} are platform detection, not logging — leave those.
 */
public final class Log {

    private Log() {}

    /** Swallowed errors, missing required content, and recoverable warnings (name the fallback). */
    public static void error(String tag, String msg) {
        if (Gdx.app != null) Gdx.app.error(tag, msg);
    }

    public static void error(String tag, String msg, Throwable t) {
        if (Gdx.app != null) Gdx.app.error(tag, msg, t);
    }

    /** Significant, normal lifecycle milestones (act unlock/advance, save written, area transition). */
    public static void info(String tag, String msg) {
        if (Gdx.app != null) Gdx.app.log(tag, msg);
    }

    /** Dev-only / high-frequency diagnostics. Compiled in, gated by the app log level. */
    public static void debug(String tag, String msg) {
        if (Gdx.app != null) Gdx.app.debug(tag, msg);
    }
}
