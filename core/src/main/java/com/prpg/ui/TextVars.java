package com.prpg.ui;

import com.prpg.world.GameClock;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Substitutes {@code {token}} placeholders in player-facing text so narrative/quest text can
 * reference live game state. Unknown tokens are left intact. Applied at the display boundary (in
 * {@code NarrativeOverlay}), after localization resolution by {@link Strings}.
 *
 * <p>Only {@code {day}} is substituted out of the box. Add a game's own tokens here (player name,
 * a counter from {@code FlagStore}, and so on) in the one place text reaches the screen.
 */
@Singleton
public class TextVars {

    private final GameClock clock;

    @Inject
    public TextVars(GameClock clock) {
        this.clock = clock;
    }

    public String apply(String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        return text.replace("{day}", String.valueOf(clock.getDay()));
    }
}
