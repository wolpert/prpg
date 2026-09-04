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
import com.prpg.ecs.component.NpcComponent;
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
 * Builds the player entity and spawns map content: the map's own object layers (props in
 * {@code npcs}, {@code items}, {@code activities}, {@code triggers}, {@code portals}) plus the act's
 * staged content. The player's collision box (feet) is offset within the larger sprite; these
 * offsets are the single source of truth for spawn/save symmetry.
 */
@Singleton
public class WorldEntityFactory {

    public static final float COLLISION_W = 12f;
    public static final float COLLISION_H = 8f;
    public static final float COLLISION_OFFSET_X = 18f;
    public static final float COLLISION_OFFSET_Y = 2f;

    /** Interactable types the world screen handles itself (anything else is an activity type). */
    public static final String TYPE_NPC = "npc";
    public static final String TYPE_ITEM = "item";

    private final Engine engine;
    private final MapManager mapManager;
    private final ColorTextures colorTextures;
    private final ItemRegistry itemRegistry;
    private final FlagStore flagStore;
    private final PlayerSprites playerSprites;
    private final ActorSprites actorSprites;
    private final StageDirector stageDirector;

    /** Reused when registering blockers, so the per-refresh sweep allocates one rect, not N. */
    private final Rectangle playerBox = new Rectangle();

    @Inject
    public WorldEntityFactory(Engine engine, MapManager mapManager, ColorTextures colorTextures,
                              ItemRegistry itemRegistry, FlagStore flagStore,
                              PlayerSprites playerSprites, ActorSprites actorSprites,
                              StageDirector stageDirector) {
        this.engine = engine;
        this.mapManager = mapManager;
        this.colorTextures = colorTextures;
        this.itemRegistry = itemRegistry;
        this.flagStore = flagStore;
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
     * Spawns everything on the current map: the map's own static content (props, portals) plus the
     * act's staged content. {@code player} may be null (nothing is standing anywhere yet).
     */
    public void spawnMapContent(Entity player) {
        spawnNpcs();
        spawnItems();
        spawnActivityLaunchers();
        spawnTriggers();
        spawnPortals();
        spawnStaged(player);
    }

    /**
     * Rebuilds only the act-staged entities, leaving the player, props and portals untouched. This is
     * the path taken when a flag changes or Ink moves someone mid-play: placement <em>and</em>
     * solidity are re-derived, so a path can open or close without reloading the map.
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

    /** Builds this act's staged content for the current map (see {@link StageDirector}). */
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
                case ACTOR -> spawnStagedActor(p, at, box);
                case OBSTACLE -> spawnStagedObstacle(p, at, box);
                case ITEM -> spawnStagedItem(p, at);
                case TRIGGER -> spawnStagedTrigger(p, at);
                case PROP -> { /* props are map-owned; staging only overrides their dialogue */ }
            }
        }
    }

    private void spawnStagedActor(StagedPlacement p, Rectangle at, Rectangle box) {
        Entity actor = engine.createEntity();
        addPosition(actor, at.x, at.y, 1);

        int size = (int) Math.max(at.width, 16);
        TextureComponent tex = engine.createComponent(TextureComponent.class);
        CollisionComponent col = engine.createComponent(CollisionComponent.class);

        // Real art when the actor declares a sprite, the colour swatch otherwise, so content can be
        // authored and played before its art exists, and gains the art with no content edit.
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

        if (p.dialogue() != null) {
            NpcComponent npc = engine.createComponent(NpcComponent.class);
            npc.dialogueId = p.dialogue();
            actor.add(npc);
            addInteractable(actor, TYPE_NPC, p.dialogue());
        }
        addStaged(actor, p.id());
        engine.addEntity(actor);
        if (p.solid()) mapManager.addBlocker(at, box);
    }

    private void spawnStagedObstacle(StagedPlacement p, Rectangle at, Rectangle box) {
        Entity obstacle = engine.createEntity();
        addPosition(obstacle, at.x, at.y, 0);

        int size = (int) Math.max(at.width, 16);
        TextureComponent tex = engine.createComponent(TextureComponent.class);
        tex.region = colorTextures.swatch(p.color() != null ? p.color() : "4E6B3A", size);
        obstacle.add(tex);

        CollisionComponent col = engine.createComponent(CollisionComponent.class);
        col.w = at.width;
        col.h = at.height;
        obstacle.add(col);

        // An obstacle interacts as its activity when it has one, else as plain dialogue.
        if (p.hasActivity()) {
            addInteractable(obstacle, p.activityType(), p.activityId());
        } else if (p.dialogue() != null) {
            addInteractable(obstacle, TYPE_NPC, p.dialogue());
        }
        addStaged(obstacle, p.id());
        engine.addEntity(obstacle);
        if (p.solid()) mapManager.addBlocker(at, box);
    }

    private void spawnStagedItem(StagedPlacement p, Rectangle at) {
        if (!itemRegistry.exists(p.itemId())) {
            Log.info("WorldEntityFactory", "staged item references unknown itemId '" + p.itemId() + "'; not spawned");
            return;
        }
        Entity item = engine.createEntity();
        addPosition(item, at.x, at.y, 0);

        TextureComponent tex = engine.createComponent(TextureComponent.class);
        tex.region = itemRegistry.icon(p.itemId());
        item.add(tex);

        CollisionComponent col = engine.createComponent(CollisionComponent.class);
        col.w = 16f;
        col.h = 16f;
        item.add(col);

        addInteractable(item, TYPE_ITEM, p.itemId());
        addStaged(item, p.id());
        engine.addEntity(item);
    }

    private void spawnStagedTrigger(StagedPlacement p, Rectangle at) {
        Entity zone = engine.createEntity();
        addPosition(zone, at.x, at.y, 0);

        CollisionComponent col = engine.createComponent(CollisionComponent.class);
        col.w = at.width;
        col.h = at.height;
        zone.add(col);

        TriggerComponent trigger = engine.createComponent(TriggerComponent.class);
        trigger.id = p.id();
        trigger.setFlag = p.setFlag();
        trigger.requireFlag = p.requireFlag();
        trigger.fireOnce = p.fireOnce();
        trigger.event = p.event();
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

    private void addInteractable(Entity entity, String type, String id) {
        InteractableComponent inter = engine.createComponent(InteractableComponent.class);
        inter.type = type;
        inter.id = id;
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

    // --- map-owned object layers (the legacy / prop path) ------------------------------------------

    private void spawnNpcs() {
        MapObjects npcs = mapManager.getObjectLayer("npcs");
        if (npcs == null) return;
        for (MapObject obj : npcs) {
            if (!(obj instanceof RectangleMapObject rmo)) continue;
            Rectangle r = rmo.getRectangle();
            String dialogueId = obj.getProperties().get("dialogueId", String.class);
            // A prop's knot lives in one act's story, but the prop itself is map-owned and outlives
            // that act. Let the current act re-voice (or silence) it.
            StagedPlacement override = stageDirector.propOverride(obj.getName());
            if (override != null) {
                dialogueId = override.dialogue();
            }
            if (dialogueId == null) {
                Log.debug("WorldEntityFactory",
                        "skipping NPC object '" + obj.getName() + "' on '" + mapManager.getCurrentMapId() + "': no dialogueId");
                continue;
            }

            Entity npc = engine.createEntity();
            addPosition(npc, r.x, r.y, 1);

            String color = obj.getProperties().get("color", String.class);
            TextureComponent tex = engine.createComponent(TextureComponent.class);
            CollisionComponent col = engine.createComponent(CollisionComponent.class);
            if (color != null) {
                int size = (int) Math.max(r.width, 16);
                tex.region = colorTextures.swatch(color, size);
                col.w = size;
                col.h = size;
            } else {
                tex.region = playerSprites.idle(0);
                col.w = 16f;
                col.h = 16f;
                col.offsetX = 16f;
            }
            npc.add(tex);
            npc.add(col);

            NpcComponent npcComp = engine.createComponent(NpcComponent.class);
            npcComp.dialogueId = dialogueId;
            npc.add(npcComp);

            addInteractable(npc, TYPE_NPC, dialogueId);
            engine.addEntity(npc);
        }
    }

    private void spawnItems() {
        MapObjects items = mapManager.getObjectLayer("items");
        if (items == null) return;
        for (MapObject obj : items) {
            if (!(obj instanceof RectangleMapObject rmo)) continue;
            Rectangle r = rmo.getRectangle();
            String itemId = obj.getProperties().get("itemId", String.class);
            if (itemId == null || !itemRegistry.exists(itemId)) {
                if (itemId != null) {
                    Log.info("WorldEntityFactory", "item object on '" + mapManager.getCurrentMapId()
                            + "' references unknown itemId '" + itemId + "'; not spawned");
                }
                continue;
            }

            Entity item = engine.createEntity();
            addPosition(item, r.x, r.y, 0);

            TextureComponent tex = engine.createComponent(TextureComponent.class);
            tex.region = itemRegistry.icon(itemId);
            item.add(tex);

            CollisionComponent col = engine.createComponent(CollisionComponent.class);
            col.w = 16f;
            col.h = 16f;
            item.add(col);

            addInteractable(item, TYPE_ITEM, itemId);
            engine.addEntity(item);
        }
    }

    /**
     * Map-owned activity launchers ({@code activities} object layer: {@code type}, {@code activityId},
     * optional {@code clearedFlag}). A staged obstacle is the preferred way to place one, because only
     * staging can block movement; this layer remains for simple "walk up and play" spots.
     */
    private void spawnActivityLaunchers() {
        MapObjects launchers = mapManager.getObjectLayer("activities");
        if (launchers == null) return;
        for (MapObject obj : launchers) {
            if (!(obj instanceof RectangleMapObject rmo)) continue;
            String type = obj.getProperties().get("type", String.class);
            String activityId = obj.getProperties().get("activityId", String.class);
            if (type == null || activityId == null) {
                Log.info("WorldEntityFactory",
                        "activity object on '" + mapManager.getCurrentMapId() + "' missing type/activityId; not spawned");
                continue;
            }

            // Skip already-cleared content so it stays gone after solving.
            String clearedFlag = obj.getProperties().get("clearedFlag", String.class);
            if (clearedFlag != null && flagStore.hasFlag(clearedFlag)) continue;

            Rectangle r = rmo.getRectangle();
            Entity marker = engine.createEntity();
            addPosition(marker, r.x, r.y, 0);

            int size = (int) Math.max(r.width, 16);
            String color = obj.getProperties().get("color", String.class);
            TextureComponent tex = engine.createComponent(TextureComponent.class);
            tex.region = colorTextures.swatch(color != null ? color : "6B4A2B", size);
            marker.add(tex);

            CollisionComponent col = engine.createComponent(CollisionComponent.class);
            col.w = size;
            col.h = size;
            marker.add(col);

            addInteractable(marker, type, activityId);
            engine.addEntity(marker);
        }
    }

    private void spawnTriggers() {
        MapObjects triggers = mapManager.getObjectLayer("triggers");
        if (triggers == null) return;
        for (MapObject obj : triggers) {
            if (!(obj instanceof RectangleMapObject rmo)) continue;
            Rectangle r = rmo.getRectangle();

            Entity zone = engine.createEntity();
            addPosition(zone, r.x, r.y, 0);

            CollisionComponent col = engine.createComponent(CollisionComponent.class);
            col.w = r.width;
            col.h = r.height;
            zone.add(col);

            TriggerComponent trigger = engine.createComponent(TriggerComponent.class);
            trigger.id = obj.getName();
            trigger.setFlag = obj.getProperties().get("set_flag", String.class);
            trigger.requireFlag = obj.getProperties().get("require_flag", String.class);
            Boolean once = obj.getProperties().get("fire_once", Boolean.class);
            trigger.fireOnce = once != null && once;
            trigger.event = obj.getProperties().get("event", String.class);
            zone.add(trigger);

            zone.add(debugZoneTexture((int) Math.max(r.width, 8)));
            engine.addEntity(zone);
        }
    }

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
            portal.targetMap = obj.getProperties().get("target_map", String.class);
            portal.targetSpawn = obj.getProperties().get("target_spawn", String.class);
            portal.requireFlag = obj.getProperties().get("require_flag", String.class);
            zone.add(portal);

            TextureComponent tex = engine.createComponent(TextureComponent.class);
            tex.region = colorTextures.swatch("7AA8FF", (int) Math.max(r.width, 8));
            tex.tint.set(0.5f, 0.6f, 1f, 0.3f);
            zone.add(tex);

            engine.addEntity(zone);
        }
    }
}
