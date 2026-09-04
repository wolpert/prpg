package com.prpg.world;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The in-fiction clock. Tracks the current day and the wall-clock time of the last save, which the
 * epilogue's daily-duty loop will need for offline-progression and seasonal events. Persisted by
 * {@code SaveManager}; reset on a new game.
 */
@Singleton
public class GameClock {

    private int day = 1;
    private long lastPlayedMillis;

    @Inject
    public GameClock() {}

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = Math.max(1, day);
    }

    /** Advances to the next day; returns the new day number. */
    public int advanceDay() {
        return ++day;
    }

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
        day = 1;
        lastPlayedMillis = 0;
    }
}
