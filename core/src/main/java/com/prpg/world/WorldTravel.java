package com.prpg.world;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A pending "move the player to this map and spawn" request. Portals and the story's
 * {@code >>> go <map> <spawn>} command both write here; {@code WorldScreen} carries the move out at
 * the top of its next frame (outside the ECS update) and clears it. A single slot, so the latest
 * request wins.
 */
@Singleton
public class WorldTravel {

    private String map;
    private String spawn;

    @Inject
    public WorldTravel() {}

    public void request(String mapId, String spawnName) {
        this.map = mapId;
        this.spawn = spawnName;
    }

    public boolean isPending() {
        return map != null;
    }

    public String map() {
        return map;
    }

    public String spawn() {
        return spawn;
    }

    public void clear() {
        map = null;
        spawn = null;
    }
}
