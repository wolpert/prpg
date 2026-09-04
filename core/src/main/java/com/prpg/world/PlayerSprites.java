package com.prpg.world;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.prpg.config.CharacterSpriteDef;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.ecs.component.OrientationComponent.Direction;
import java.io.IOException;
import java.io.Reader;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The player's idle/walk animations, loaded from the data-driven {@code sprites/player.sprite.yaml}
 * descriptor through the shared {@link CharacterSpriteLoader} (Aseprite art -> packed atlas). A thin,
 * player-specific facade over {@link CharacterSprites} so existing call sites ({@code WorldScreen},
 * {@code WorldEntityFactory}) are unchanged; future NPCs use {@link CharacterSpriteLoader} directly.
 */
@Singleton
public class PlayerSprites implements Disposable {

    private static final String DESCRIPTOR_PATH = "sprites/player.sprite.yaml";

    private final ContentResolver content;
    private final ConfigLoader configLoader;
    private final CharacterSpriteLoader loader;

    private CharacterSprites sprites;

    @Inject
    public PlayerSprites(ContentResolver content, ConfigLoader configLoader, CharacterSpriteLoader loader) {
        this.content = content;
        this.configLoader = configLoader;
        this.loader = loader;
    }

    public void load() {
        if (sprites != null) return;
        CharacterSpriteDef def;
        try (Reader reader = content.resolve(DESCRIPTOR_PATH).reader()) {
            def = configLoader.load(CharacterSpriteDef.class, reader);
        } catch (IOException e) {
            throw new GdxRuntimeException("failed to load " + DESCRIPTOR_PATH, e);
        }
        sprites = loader.load(def);
    }

    public Animation<TextureRegion> animation(int dirIndex, boolean moving) {
        return sprites.animation(dirIndex, moving);
    }

    public TextureRegion idle(int dirIndex) {
        return sprites.idle(dirIndex);
    }

    public int frameWidth() {
        return sprites.frameWidth();
    }

    public int frameHeight() {
        return sprites.frameHeight();
    }

    public static int directionIndex(Direction d) {
        return CharacterSprites.directionIndex(d);
    }

    @Override
    public void dispose() {
        // The atlas is owned by the shared loader; releasing it frees the player's (and any NPC's) pages.
        loader.dispose();
        sprites = null;
    }
}
