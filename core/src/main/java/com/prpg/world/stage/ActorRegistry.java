package com.prpg.world.stage;

import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.util.Log;
import com.prpg.world.stage.config.ActorDef;
import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Loads every {@code actors/*.yaml} across the mounted packs (pack ∪ bundled, via
 * {@link ContentResolver}) and answers "who is this actor". Identity only — an actor's position is
 * per-act staging data, never a property of the character.
 *
 * <p>Lazy: nothing is read until the first lookup, so constructing the Dagger graph doesn't touch
 * the filesystem. A malformed actor file is logged and skipped rather than failing the load, so one
 * bad DLC pack can't take the game down; {@code ContentValidationTest} is the guard rail that keeps
 * shipped content honest.
 */
@Singleton
public class ActorRegistry {

    private static final String ACTOR_PREFIX = "actors/";

    private final ConfigLoader configLoader;
    private final ContentResolver content;
    private final Map<String, ActorDef> actors = new LinkedHashMap<>();
    private boolean loaded;

    @Inject
    public ActorRegistry(ConfigLoader configLoader, ContentResolver content) {
        this.configLoader = configLoader;
        this.content = content;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true; // set first: a failure below must not retry-loop on every lookup
        for (String path : content.list(ACTOR_PREFIX)) {
            if (!path.endsWith(".yaml")) continue;
            try (Reader reader = content.resolve(path).reader()) {
                ActorDef def = configLoader.load(ActorDef.class, reader);
                if (def == null || def.id == null) {
                    Log.info("ActorRegistry", "skipping actor \"" + path + "\" — null definition or no id");
                    continue;
                }
                ActorDef previous = actors.put(def.id, def);
                if (previous != null) {
                    Log.info("ActorRegistry", "actor '" + def.id + "' redefined by \"" + path + "\"");
                }
            } catch (Exception e) {
                Log.error("ActorRegistry", "failed to load actor " + path, e);
            }
        }
        Log.debug("ActorRegistry", "loaded " + actors.size() + " actor definition(s)");
    }

    /** The actor with this id, or null if no mounted pack defines one. */
    public ActorDef get(String id) {
        ensureLoaded();
        return id == null ? null : actors.get(id);
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Collection<ActorDef> all() {
        ensureLoaded();
        return actors.values();
    }
}
