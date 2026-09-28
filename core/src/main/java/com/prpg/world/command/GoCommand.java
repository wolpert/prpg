package com.prpg.world.command;

import com.prpg.narrative.StoryCommand;
import com.prpg.util.Log;
import com.prpg.world.WorldTravel;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@code >>> go <map> [<spawn>]}: moves the player to a named spawn on a map (the map's first spawn
 * when omitted) and waits until the new map is built. Pair it with {@code fade} for a clean cut.
 */
@Singleton
public class GoCommand implements StoryCommand {

    public static final String NAME = "go";

    private final WorldTravel travel;

    @Inject
    public GoCommand(WorldTravel travel) {
        this.travel = travel;
    }

    @Override
    public boolean start(List<String> args) {
        if (args.isEmpty()) {
            Log.error("GoCommand", "'>>> go' needs <map> [<spawn>]; skipped");
            return false;
        }
        travel.request(args.get(0), args.size() > 1 ? args.get(1) : null);
        return true;
    }

    @Override
    public boolean update(float delta) {
        return travel.isPending();
    }
}
