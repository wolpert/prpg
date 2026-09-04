package com.prpg.world.scene;

import com.badlogic.gdx.math.MathUtils;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The sample cutscene: the screen dims, holds a beat, and lifts again while the player is frozen.
 * Useful on its own for "time passes" moments, and the smallest possible template for a real
 * scripted event: implement {@link ScriptedEvent}, drive the shared fade via the director, and
 * return {@code false} from {@link #update} when done. Register under a string id in
 * {@code WorldModule}; content then fires it with {@code event: fade_beat} (a trigger) or
 * {@code # event: fade_beat} (an Ink line). Pair it with a trigger's {@code set_flag} when the beat
 * should leave a mark on the story.
 */
@Singleton
public class FadeBeatEvent implements ScriptedEvent {

    public static final String ID = "fade_beat";

    private static final float FADE = 0.6f;
    private static final float HOLD = 1.2f;
    private static final float MAX_ALPHA = 0.85f;

    private enum Phase { FADE_IN, HOLD, FADE_OUT }

    private SceneDirector director;
    private Phase phase;
    private float timer;

    @Inject
    public FadeBeatEvent() {}

    @Override
    public void begin(SceneDirector director) {
        this.director = director;
        phase = Phase.FADE_IN;
        timer = FADE;
    }

    @Override
    public boolean update(float delta) {
        timer -= delta;
        float alpha;
        switch (phase) {
            case FADE_IN -> {
                alpha = MAX_ALPHA * (1f - Math.max(0f, timer) / FADE);
                if (timer <= 0f) {
                    phase = Phase.HOLD;
                    timer = HOLD;
                }
            }
            case HOLD -> {
                alpha = MAX_ALPHA;
                if (timer <= 0f) {
                    phase = Phase.FADE_OUT;
                    timer = FADE;
                }
            }
            case FADE_OUT -> {
                alpha = MAX_ALPHA * (Math.max(0f, timer) / FADE);
                if (timer <= 0f) {
                    director.setFade(0f);
                    return false;
                }
            }
            default -> alpha = 0f;
        }
        director.setFade(MathUtils.clamp(alpha, 0f, MAX_ALPHA));
        return true;
    }
}
