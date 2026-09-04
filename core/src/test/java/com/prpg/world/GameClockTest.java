package com.prpg.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GameClockTest {

    @Test
    void startsAtDayOneAndAdvances() {
        GameClock clock = new GameClock();
        assertEquals(1, clock.getDay());
        assertEquals(2, clock.advanceDay());
        assertEquals(2, clock.getDay());
    }

    @Test
    void resetReturnsToDayOne() {
        GameClock clock = new GameClock();
        clock.advanceDay();
        clock.setLastPlayedMillis(123L);
        clock.reset();
        assertEquals(1, clock.getDay());
        assertEquals(0L, clock.getLastPlayedMillis());
    }

    @Test
    void realDaysSinceLastPlayedComputesElapsed() {
        GameClock clock = new GameClock();
        long day = 24L * 60 * 60 * 1000;
        clock.setLastPlayedMillis(day); // "played" at t=1 day
        assertEquals(2, clock.realDaysSinceLastPlayed(day * 3)); // now t=3 days → 2 elapsed
        GameClock fresh = new GameClock();
        assertEquals(0, fresh.realDaysSinceLastPlayed(day)); // never saved
    }
}
