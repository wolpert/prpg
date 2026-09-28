package com.prpg.narrative;

import java.util.List;

/**
 * Something a story asks the engine to do and waits for, written as a command line in Ink:
 * {@code >>> play match3 hedge}. The story stops on that line (Ink's {@code Continue()} returns one
 * line at a time), the runner starts the command, and the story carries on once
 * {@link #update(float)} reports it finished. A plain Ink function call can't do this: Ink would run
 * straight past it to the next line before the engine had a chance to act.
 *
 * <p>Commands are registered like activities: {@code @Provides @IntoMap @StringKey("<name>")
 * StoryCommand} in {@code WorldModule}. Adding one is one class plus one binding; the name is the
 * first word after {@code >>>}, the rest of the line is {@code args}.
 */
public interface StoryCommand {

    /**
     * Starts the command. Returns false when it cannot run (bad arguments, unknown id); the story then
     * continues immediately, and the command should log why.
     */
    boolean start(List<String> args);

    /** Advances one frame. Returns true while the story must keep waiting. */
    boolean update(float delta);
}
