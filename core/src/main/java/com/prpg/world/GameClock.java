package com.prpg.world;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The real-world clock: when the game was last played. An epilogue's daily-duty loop needs it for
 * offline progression and seasonal events. (The in-fiction day is story state: the Ink variable
 * {@code day} in {@code baseline/ink/common/world.ink}.) Persisted by {@code SaveManager}; reset on a
 * new game.
 */
@Singleton
public class GameClock {

    private long lastPlayedMillis;

    @Inject
    public GameClock() {}

    public long getLastPlayedMillis() {
        return lastPlayedMillis;
    }

    public void setLastPlayedMillis(long millis) {
        this.lastPlayedMillis = millis;
    }

    /** Real days elapsed since the last save (0 if never saved). Drives offline progression. */
    public long realDaysSinceLastPlayed(long nowMillis) {
        if (lastPlayedMillis <= 0) return 0;
        return Math.max(0, (nowMillis - lastPlayedMillis) / (24L * 60 * 60 * 1000));
    }

    public void reset() {
        lastPlayedMillis = 0;
    }
}
