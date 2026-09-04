package com.prpg.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.prpg.ecs.component.CollisionComponent;
import com.prpg.ecs.component.PlayerComponent;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.ecs.component.PortalComponent;
import com.prpg.ecs.component.TriggerComponent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fires "zone entered" events when the player's collision box first overlaps a trigger or portal
 * entity (edge-triggered, so standing inside doesn't re-fire each frame). Listeners receive the
 * zone entity and decide what to do (set flags, change maps, run an event). Runs after movement so
 * it sees the player's updated position.
 */
@Singleton
public class TriggerSystem extends EntitySystem {

    static final int PRIORITY = -7;

    private static final ComponentMapper<PositionComponent> POSITIONS =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<CollisionComponent> COLLISIONS =
            ComponentMapper.getFor(CollisionComponent.class);

    private final List<Consumer<Entity>> listeners = new ArrayList<>();
    private final Set<Entity> inside = new HashSet<>();

    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> triggers;
    private ImmutableArray<Entity> portals;

    @Inject
    public TriggerSystem() {
        super(PRIORITY);
    }

    /** Listener receives a zone entity the moment the player enters it. */
    public void addListener(Consumer<Entity> listener) {
        listeners.add(listener);
    }

    /**
     * Resets edge-tracking after a world rebuild. Clears stale zone references from the previous
     * map, then seeds {@code inside} with every zone already overlapping the player's spawn so those
     * don't fire on the first frame — otherwise spawning onto (or beside) a return portal bounces
     * the player straight back (the "portal ping-pong"). Call after {@code buildWorld} populates the
     * new map's entities and positions the player.
     */
    public void reset() {
        inside.clear();
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PositionComponent pPos = POSITIONS.get(player);
        CollisionComponent pCol = COLLISIONS.get(player);
        float px = pPos.x + pCol.offsetX;
        float py = pPos.y + pCol.offsetY;
        seedOverlapping(triggers, px, py, pCol.w, pCol.h);
        seedOverlapping(portals, px, py, pCol.w, pCol.h);
    }

    private void seedOverlapping(ImmutableArray<Entity> zones, float px, float py, float pw, float ph) {
        if (zones == null) return;
        for (int i = 0; i < zones.size(); i++) {
            Entity zone = zones.get(i);
            PositionComponent zPos = POSITIONS.get(zone);
            CollisionComponent zCol = COLLISIONS.get(zone);
            if (overlaps(px, py, pw, ph, zPos.x + zCol.offsetX, zPos.y + zCol.offsetY, zCol.w, zCol.h)) {
                inside.add(zone);
            }
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
                PlayerComponent.class, PositionComponent.class, CollisionComponent.class).get());
        triggers = engine.getEntitiesFor(Family.all(
                TriggerComponent.class, PositionComponent.class, CollisionComponent.class).get());
        portals = engine.getEntitiesFor(Family.all(
                PortalComponent.class, PositionComponent.class, CollisionComponent.class).get());
        inside.clear();
    }

    @Override
    public void update(float deltaTime) {
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PositionComponent pPos = POSITIONS.get(player);
        CollisionComponent pCol = COLLISIONS.get(player);
        float px = pPos.x + pCol.offsetX;
        float py = pPos.y + pCol.offsetY;

        checkZones(triggers, px, py, pCol.w, pCol.h);
        checkZones(portals, px, py, pCol.w, pCol.h);
    }

    private void checkZones(ImmutableArray<Entity> zones, float px, float py, float pw, float ph) {
        for (int i = 0; i < zones.size(); i++) {
            Entity zone = zones.get(i);
            PositionComponent zPos = POSITIONS.get(zone);
            CollisionComponent zCol = COLLISIONS.get(zone);
            float zx = zPos.x + zCol.offsetX;
            float zy = zPos.y + zCol.offsetY;

            boolean overlapping = overlaps(px, py, pw, ph, zx, zy, zCol.w, zCol.h);
            if (overlapping && !inside.contains(zone)) {
                inside.add(zone);
                fire(zone);
            } else if (!overlapping) {
                inside.remove(zone);
            }
        }
    }

    private void fire(Entity zone) {
        for (Consumer<Entity> listener : listeners) {
            listener.accept(zone);
        }
    }

    private static boolean overlaps(float ax, float ay, float aw, float ah,
                                    float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
}
