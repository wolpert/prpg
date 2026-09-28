package com.prpg.world;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.prpg.ecs.component.AnimationComponent;
import com.prpg.ecs.component.CollisionComponent;
import com.prpg.ecs.component.InteractableComponent;
import com.prpg.ecs.component.OrientationComponent;
import com.prpg.ecs.component.PlayerComponent;
import com.prpg.ecs.component.PortalComponent;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.ecs.component.StagedComponent;
import com.prpg.ecs.component.TextureComponent;
import com.prpg.ecs.component.TriggerComponent;
import com.prpg.items.ItemRegistry;
import com.prpg.ui.ColorTextures;
import com.prpg.util.Log;
import com.prpg.world.stage.StageDirector;
import com.prpg.world.stage.StagedPlacement;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Builds the player entity and spawns map content: the map's own portals plus whatever the current
 * act's Ink {@code stage()} places (actors, things, zones). A map holds only geography (terrain,
 * collision, spawns, portals, markers); everything the story controls is staged, so the same map can
 * be populated differently as the story moves on. The player's collision box (feet) is offset within
 * the larger sprite; these offsets are the single source of truth for spawn/save symmetry.
 */
@Singleton
public class WorldEntityFactory {

    public static final float COLLISION_W = 12f;
    public static final float COLLISION_H = 8f;
    public static final float COLLISION_OFFSET_X = 18f;
    public static final float COLLISION_OFFSET_Y = 2f;

    /** Swatch for a thing the story staged without a {@code define()} look. */
    private static final String DEFAULT_THING_COLOR = "6B4A2B";

    private final Engine engine;
    private final MapManager mapManager;
    private final ColorTextures colorTextures;
    private final ItemRegistry itemRegistry;
    private final PlayerSprites playerSprites;
    private final ActorSprites actorSprites;
    private final StageDirector stageDirector;

    /** Reused when registering blockers, so the per-refresh sweep allocates one rect, not N. */
    private final Rectangle playerBox = new Rectangle();

    @Inject
    public WorldEntityFactory(Engine engine, MapManager mapManager, ColorTextures colorTextures,
                              ItemRegistry itemRegistry, PlayerSprites playerSprites,
                              ActorSprites actorSprites, StageDirector stageDirector) {
        this.engine = engine;
        this.mapManager = mapManager;
        this.colorTextures = colorTextures;
        this.itemRegistry = itemRegistry;
        this.playerSprites = playerSprites;
        this.actorSprites = actorSprites;
        this.stageDirector = stageDirector;
    }

    /** Positions the player sprite directly at (spriteX, spriteY) facing the given direction. */
    public Entity createPlayer(float spriteX, float spriteY, OrientationComponent.Direction facing) {
        Entity player = engine.createEntity();

        PositionComponent pos = engine.createComponent(PositionComponent.class);
        pos.x = spriteX;
        pos.y = spriteY;
        pos.z = 1;
        player.add(pos);

        int dirIdx = PlayerSprites.directionIndex(facing);
        TextureComponent tex = engine.createComponent(TextureComponent.class);
        tex.region = playerSprites.idle(dirIdx);
        player.add(tex);

        AnimationComponent anim = engine.createComponent(AnimationComponent.class);
        anim.animation = playerSprites.animation(dirIdx, false);
        player.add(anim);

        OrientationComponent orient = engine.createComponent(OrientationComponent.class);
        orient.direction = facing;
        player.add(orient);

        CollisionComponent col = engine.createComponent(CollisionComponent.class);
        col.w = COLLISION_W;
        col.h = COLLISION_H;
        col.offsetX = COLLISION_OFFSET_X;
        col.offsetY = COLLISION_OFFSET_Y;
        player.add(col);

        player.add(engine.createComponent(PlayerComponent.class));
        engine.addEntity(player);
        return player;
    }

    /**
     * Spawns everything on the current map: its portals plus the story's staged content.
     * {@code player} may be null (nothing is standing anywhere yet).
     */
    public void spawnMapContent(Entity player) {
        spawnPortals();
        spawnStaged(player);
    }

    /**
     * Rebuilds only the staged entities, leaving the player and portals untouched. This is the path
     * taken whenever the story moves on: placement <em>and</em> solidity are re-derived, so a path can
     * open or close without reloading the map.
     */
    public void refreshStaged(Entity player) {
        List<Entity> stale = new ArrayList<>();
        for (Entity e : engine.getEntitiesFor(Family.all(StagedComponent.class).get())) {
            stale.add(e);
        }
        for (Entity e : stale) {
            engine.removeEntity(e);
        }
        mapManager.clearBlockers();
        spawnStaged(player);
    }

    /** Builds the story's staged content for the current map (see {@link StageDirector}). */
    private void spawnStaged(Entity player) {
        Rectangle box = playerCollisionBox(player);
        for (StagedPlacement p : stageDirector.stagedFor(mapManager.getCurrentMapId())) {
            Rectangle at = mapManager.getMarker(p.marker());
            if (at == null) {
                Log.info("WorldEntityFactory", "staged '" + p.id() + "' names marker '" + p.marker()
                        + "' which does not exist on '" + mapManager.getCurrentMapId() + "'; not spawned");
                continue;
            }
            switch (p.kind()) {
                case ACTOR -> spawnActor(p, at);
                case THING -> spawnThing(p, at, box);
                case ZONE -> spawnZone(p, at);
            }
        }
    }

    private void spawnActor(StagedPlacement p, Rectangle at) {
        Entity actor = engine.createEntity();
        addPosition(actor, at.x, at.y, 1);

        int size = (int) Math.max(at.width, 16);
        TextureComponent tex = engine.createComponent(TextureComponent.class);
        CollisionComponent col = engine.createComponent(CollisionComponent.class);

        // Real art when cast() gives a sprite, the colour swatch otherwise, so a story can be played
        // before its art exists, and gains the art with no story edit.
        CharacterSprites sprites = actorSprites.get(p.sprite());
        if (sprites != null) {
            int dir = CharacterSprites.directionIndex(OrientationComponent.Direction.DOWN);
            tex.region = sprites.idle(dir);
            AnimationComponent anim = engine.createComponent(AnimationComponent.class);
            anim.animation = sprites.animation(dir, false);
            actor.add(anim);
            // The sprite frame is larger than the character; keep the interaction box at their feet,
            // centred in the frame, so talking range doesn't balloon with the art.
            col.w = 16f;
            col.h = 16f;
            col.offsetX = (sprites.frameWidth() - 16f) / 2f;
        } else if (p.color() != null) {
            tex.region = colorTextures.swatch(p.color(), size);
            col.w = size;
            col.h = size;
        } else {
            tex.region = playerSprites.idle(0);
            col.w = 16f;
            col.h = 16f;
            col.offsetX = 16f;
        }
        actor.add(tex);
        actor.add(col);

        if (p.knot() != null) addInteractable(actor, p.id(), p.knot());
        addStaged(actor, p.id());
        engine.addEntity(actor);
    }

    private void spawnThing(StagedPlacement p, Rectangle at, Rectangle box) {
        Entity thing = engine.createEntity();
        addPosition(thing, at.x, at.y, 0);

        int size = (int) Math.max(at.width, 16);
        TextureComponent tex = engine.createComponent(TextureComponent.class);
        CharacterSprites sprites = actorSprites.get(p.sprite());
        if (sprites != null) {
            tex.region = sprites.idle(CharacterSprites.directionIndex(OrientationComponent.Direction.DOWN));
        } else if (p.color() != null) {
            tex.region = colorTextures.swatch(p.color(), size);
        } else if (itemRegistry.exists(p.id())) {
            // A thing staged under an item id with no look of its own shows that item's icon.
            tex.region = itemRegistry.icon(p.id());
        } else {
            tex.region = colorTextures.swatch(DEFAULT_THING_COLOR, size);
        }
        thing.add(tex);

        CollisionComponent col = engine.createComponent(CollisionComponent.class);
        col.w = at.width;
        col.h = at.height;
        thing.add(col);

        if (p.knot() != null) addInteractable(thing, p.id(), p.knot());
        addStaged(thing, p.id());
        engine.addEntity(thing);
        if (p.solid()) mapManager.addBlocker(at, box);
    }

    private void spawnZone(StagedPlacement p, Rectangle at) {
        if (p.knot() == null) {
            Log.info("WorldEntityFactory", "zone '" + p.id() + "' has no knot; a zone only runs a knot, so not spawned");
            return;
        }
        Entity zone = engine.createEntity();
        addPosition(zone, at.x, at.y, 0);

        CollisionComponent col = engine.createComponent(CollisionComponent.class);
        col.w = at.width;
        col.h = at.height;
        zone.add(col);

        TriggerComponent trigger = engine.createComponent(TriggerComponent.class);
        trigger.id = p.id();
        trigger.knot = p.knot();
        zone.add(trigger);

        zone.add(debugZoneTexture((int) Math.max(at.width, 8)));
        addStaged(zone, p.id());
        engine.addEntity(zone);
    }

    /** The player's collision box in world space, or null when there is no player yet. */
    private Rectangle playerCollisionBox(Entity player) {
        if (player == null) return null;
        PositionComponent pos = player.getComponent(PositionComponent.class);
        CollisionComponent col = player.getComponent(CollisionComponent.class);
        if (pos == null || col == null) return null;
        return playerBox.set(pos.x + col.offsetX, pos.y + col.offsetY, col.w, col.h);
    }

    private void addPosition(Entity entity, float x, float y, int z) {
        PositionComponent pos = engine.createComponent(PositionComponent.class);
        pos.x = x;
        pos.y = y;
        pos.z = z;
        entity.add(pos);
    }

    private void addInteractable(Entity entity, String id, String knot) {
        InteractableComponent inter = engine.createComponent(InteractableComponent.class);
        inter.id = id;
        inter.knot = knot;
        entity.add(inter);
    }

    private void addStaged(Entity entity, String id) {
        StagedComponent staged = engine.createComponent(StagedComponent.class);
        staged.id = id;
        entity.add(staged);
    }

    private TextureComponent debugZoneTexture(int size) {
        TextureComponent tex = engine.createComponent(TextureComponent.class);
        tex.region = colorTextures.swatch("3AA0C0", size);
        tex.tint.set(0.3f, 0.7f, 0.9f, 0.25f);
        return tex;
    }

    // --- map-owned portals --------------------------------------------------------------------

    private void spawnPortals() {
        MapObjects portals = mapManager.getObjectLayer("portals");
        if (portals == null) return;
        for (MapObject obj : portals) {
            if (!(obj instanceof RectangleMapObject rmo)) continue;
            Rectangle r = rmo.getRectangle();

            Entity zone = engine.createEntity();
            addPosition(zone, r.x, r.y, 0);

            CollisionComponent col = engine.createComponent(CollisionComponent.class);
            col.w = r.width;
            col.h = r.height;
            zone.add(col);

            PortalComponent portal = engine.createComponent(PortalComponent.class);
            portal.name = obj.getName();
            portal.targetMap = obj.getProperties().get("target_map", String.class);
            portal.targetSpawn = obj.getProperties().get("target_spawn", String.class);
            zone.add(portal);

            TextureComponent tex = engine.createComponent(TextureComponent.class);
            tex.region = colorTextures.swatch("7AA8FF", (int) Math.max(r.width, 8));
            tex.tint.set(0.5f, 0.6f, 1f, 0.3f);
            zone.add(tex);

            engine.addEntity(zone);
        }
    }
}
