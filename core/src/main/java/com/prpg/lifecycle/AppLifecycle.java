package com.prpg.lifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Mutable singleton holder backing {@link LifecycleGate}. {@code TheGame} flips the active flag
 * from libGDX's {@code Game.pause()}/{@code resume()} hooks so consumers see
 * {@code isAppActive() == false} while the activity is backgrounded or the window is minimized.
 *
 * <p>Default state is active — the app starts foregrounded and lifecycle callbacks only fire on
 * background/resume transitions, so any consumer running before the first transition sees a live
 * gate.
 */
@Singleton
public class AppLifecycle implements LifecycleGate {

    private boolean active = true;

    @Inject
    public AppLifecycle() {}

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean isAppActive() {
        return active;
    }
}
