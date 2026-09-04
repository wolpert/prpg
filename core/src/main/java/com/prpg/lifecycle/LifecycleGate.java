package com.prpg.lifecycle;

/**
 * App-level lifecycle gate, mirroring the {@code InputGate} seam pattern. Returns whether the
 * application is currently active (foreground). When backgrounded — Android activity {@code onPause},
 * desktop window minimize — consumers should treat the next frame's effective delta as zero so
 * physics and gameplay don't catch up by ticking many simulated frames on resume.
 */
public interface LifecycleGate {
    /** True if the app is in the foreground and gameplay should advance this frame. */
    boolean isAppActive();
}
