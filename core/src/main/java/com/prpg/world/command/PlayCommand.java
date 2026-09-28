package com.prpg.world.command;

import com.prpg.activities.ActivityLauncher;
import com.prpg.narrative.StoryCommand;
import com.prpg.narrative.StoryVariables;
import com.prpg.util.Log;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@code >>> play <type> <id>}: plays an activity and waits for the player to finish, then sets the
 * story variable {@value #RESULT_VARIABLE} to whether they won, so the next lines can branch on it:
 *
 * <pre>
 * >>> play match3 hedge
 * { activity_won:
 *     ~ hedge_cleared = true
 *     The last of the bramble comes away.
 * }
 * </pre>
 *
 * {@code type} is an activity type bound in {@code WorldModule}; {@code id} names
 * {@code activities/<type>/<id>.yaml}. If the activity can't start, the result is false and the
 * story carries straight on.
 */
@Singleton
public class PlayCommand implements StoryCommand {

    public static final String NAME = "play";

    /** Declared in {@code baseline/ink/common/world.ink}; written here after every play. */
    public static final String RESULT_VARIABLE = "activity_won";

    private final ActivityLauncher launcher;
    private final StoryVariables variables;

    @Inject
    public PlayCommand(ActivityLauncher launcher, StoryVariables variables) {
        this.launcher = launcher;
        this.variables = variables;
    }

    @Override
    public boolean start(List<String> args) {
        variables.set(RESULT_VARIABLE, false);
        if (args.size() < 2) {
            Log.error("PlayCommand", "'>>> play' needs <type> <id>, got " + args + "; skipped");
            return false;
        }
        try {
            return launcher.launch(args.get(0), args.get(1));
        } catch (RuntimeException e) {
            Log.error("PlayCommand", "activity " + args.get(0) + "/" + args.get(1)
                    + " failed to launch; the story continues as a loss", e);
            return false;
        }
    }

    @Override
    public boolean update(float delta) {
        if (launcher.isRunning()) return true;
        variables.set(RESULT_VARIABLE, launcher.wasWon());
        return false;
    }
}
