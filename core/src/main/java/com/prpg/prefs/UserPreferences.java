package com.prpg.prefs;

import com.badlogic.gdx.Preferences;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Typed wrapper over libGDX {@link Preferences}. All setters flush immediately so values
 * survive a forced kill — adequate for a handful of toggles, but if write volume grows
 * (per-frame settings, telemetry) the flush should move to a debounced/explicit save.
 */
@Singleton
public class UserPreferences {

    private static final String KEY_SOUND = "audio.sound";
    private static final String KEY_MUSIC = "audio.music";
    private static final String KEY_DEBUG_OVERLAY = "debug.overlay";

    private final Preferences prefs;

    @Inject
    public UserPreferences(Preferences prefs) {
        this.prefs = prefs;
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.putBoolean(KEY_SOUND, enabled);
        prefs.flush();
    }

    public boolean isMusicEnabled() {
        return prefs.getBoolean(KEY_MUSIC, true);
    }

    public void setMusicEnabled(boolean enabled) {
        prefs.putBoolean(KEY_MUSIC, enabled);
        prefs.flush();
    }

    public boolean isDebugOverlayEnabled() {
        return prefs.getBoolean(KEY_DEBUG_OVERLAY, false);
    }

    public void setDebugOverlayEnabled(boolean enabled) {
        prefs.putBoolean(KEY_DEBUG_OVERLAY, enabled);
        prefs.flush();
    }
}
