package com.prpg.world;

import com.prpg.config.CharacterSpriteDef;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.util.Log;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Resolves an actor's {@code sprite:} id (from its {@code actors/<id>.yaml}) to ready-to-use
 * {@link CharacterSprites}, by loading {@code sprites/<sprite>.sprite.yaml} through the shared
 * {@link CharacterSpriteLoader}. The NPC counterpart of {@link PlayerSprites}, which is the
 * player-specific facade over the same loader.
 *
 * <p>Results are cached per sprite id, and so is <em>failure</em>: an actor whose descriptor is
 * missing or whose atlas lacks the regions falls back to its colour swatch and is not retried, so a
 * bad descriptor costs one log line rather than an exception per spawned frame.
 */
@Singleton
public class ActorSprites {

    private final ContentResolver content;
    private final ConfigLoader configLoader;
    private final CharacterSpriteLoader loader;

    /** Sprite id -> sprites, or an explicit null entry meaning "tried and failed; use the swatch". */
    private final Map<String, CharacterSprites> cache = new HashMap<>();

    @Inject
    public ActorSprites(ContentResolver content, ConfigLoader configLoader, CharacterSpriteLoader loader) {
        this.content = content;
        this.configLoader = configLoader;
        this.loader = loader;
    }

    /**
     * The sprites for {@code spriteId}, or null when there is no usable art — callers fall back to
     * the placeholder swatch, which is what lets content be authored before art exists.
     */
    public CharacterSprites get(String spriteId) {
        if (spriteId == null) return null;
        if (cache.containsKey(spriteId)) return cache.get(spriteId);

        CharacterSprites sprites = null;
        String path = "sprites/" + spriteId + ".sprite.yaml";
        if (!content.exists(path)) {
            Log.info("ActorSprites", "no descriptor " + path + "; falling back to the colour swatch");
        } else {
            try (Reader reader = content.resolve(path).reader()) {
                CharacterSpriteDef def = configLoader.load(CharacterSpriteDef.class, reader);
                sprites = def == null ? null : loader.load(def);
            } catch (Exception e) {
                // A missing atlas region throws from the loader; degrade rather than break the map.
                Log.error("ActorSprites", "failed to load " + path + "; falling back to the colour swatch", e);
            }
        }
        cache.put(spriteId, sprites);
        return sprites;
    }
}
