package com.prpg.world.command;

import com.prpg.narrative.StoryCommand;
import com.prpg.util.Log;
import com.prpg.world.scene.SceneDirector;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@code >>> fade out [<seconds>]} darkens the screen to black; {@code >>> fade in [<seconds>]} lifts
 * it again. The story waits for the fade to finish. If a conversation ends while still faded out, the
 * world lifts the veil itself, so a missing {@code fade in} can't leave the player in the dark.
 */
@Singleton
public class FadeCommand implements StoryCommand {

    public static final String NAME = "fade";

    static final float DEFAULT_SECONDS = 0.5f;

    private final SceneDirector director;

    private float from;
    private float to;
    private float duration;
    private float elapsed;

    @Inject
    public FadeCommand(SceneDirector director) {
        this.director = director;
    }

    @Override
    public boolean start(List<String> args) {
        String direction = args.isEmpty() ? "" : args.get(0);
        if (!"out".equals(direction) && !"in".equals(direction)) {
            Log.error("FadeCommand", "'>>> fade' needs 'out' or 'in', got " + args + "; skipped");
            return false;
        }
        from = director.fadeAlpha();
        to = "out".equals(direction) ? 1f : 0f;
        duration = args.size() > 1 ? Commands.seconds(args.get(1), DEFAULT_SECONDS) : DEFAULT_SECONDS;
        elapsed = 0f;
        if (duration <= 0f) {
            director.setFade(to);
            return false;
        }
        return true;
    }

    @Override
    public boolean update(float delta) {
        elapsed += delta;
        float t = Math.min(1f, elapsed / duration);
        director.setFade(from + (to - from) * t);
        return t < 1f;
    }
}
