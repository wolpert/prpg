package com.prpg.world.command;

import com.prpg.narrative.StoryCommand;
import com.prpg.util.Log;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** {@code >>> wait <seconds>}: a beat of silence before the story continues. */
@Singleton
public class WaitCommand implements StoryCommand {

    public static final String NAME = "wait";

    private float remaining;

    @Inject
    public WaitCommand() {}

    @Override
    public boolean start(List<String> args) {
        remaining = args.isEmpty() ? 0f : Commands.seconds(args.get(0), -1f);
        if (remaining < 0f) {
            Log.error("WaitCommand", "'>>> wait' needs a number of seconds, got " + args + "; skipped");
            return false;
        }
        return remaining > 0f;
    }

    @Override
    public boolean update(float delta) {
        remaining -= delta;
        return remaining > 0f;
    }
}
