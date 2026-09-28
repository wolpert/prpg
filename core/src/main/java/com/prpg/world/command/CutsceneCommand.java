package com.prpg.world.command;

import com.prpg.narrative.StoryCommand;
import com.prpg.util.Log;
import com.prpg.world.scene.SceneDirector;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@code >>> cutscene <id>}: runs a Java {@code ScriptedEvent} registered under {@code id} and waits
 * for it to finish. For effects the story's own commands ({@code fade}, {@code wait}, {@code go})
 * can't express.
 */
@Singleton
public class CutsceneCommand implements StoryCommand {

    public static final String NAME = "cutscene";

    private final SceneDirector director;

    @Inject
    public CutsceneCommand(SceneDirector director) {
        this.director = director;
    }

    @Override
    public boolean start(List<String> args) {
        if (args.isEmpty()) {
            Log.error("CutsceneCommand", "'>>> cutscene' needs an event id; skipped");
            return false;
        }
        return director.trigger(args.get(0));
    }

    @Override
    public boolean update(float delta) {
        return director.isActive();
    }
}
