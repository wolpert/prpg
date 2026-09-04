package com.prpg.ecs.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.prpg.ecs.component.CollisionComponent;
import com.prpg.ecs.component.PlayerComponent;
import com.prpg.ecs.component.PortalComponent;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.ecs.component.TriggerComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Headless coverage of {@link TriggerSystem} edge-triggering and the per-rebuild {@code reset()}
 * re-seeding that prevents the portal ping-pong. No GL needed — components are plain data.
 */
class TriggerSystemTest {

    private PooledEngine engine;
    private TriggerSystem system;
    private List<Entity> fired;
    private PositionComponent playerPos;

    @BeforeEach
    void setUp() {
        engine = new PooledEngine();
        system = new TriggerSystem();
        engine.addSystem(system);
        fired = new ArrayList<>();
        system.addListener(fired::add);
        playerPos = addPlayer(0f, 0f, 10f, 10f);
    }

    private PositionComponent addPlayer(float x, float y, float w, float h) {
        Entity e = engine.createEntity();
        PositionComponent p = new PositionComponent();
        p.x = x;
        p.y = y;
        e.add(p);
        CollisionComponent c = new CollisionComponent();
        c.w = w;
        c.h = h;
        e.add(c);
        e.add(new PlayerComponent());
        engine.addEntity(e);
        return p;
    }

    private Entity addTrigger(float x, float y, float w, float h) {
        Entity e = engine.createEntity();
        PositionComponent p = new PositionComponent();
        p.x = x;
        p.y = y;
        e.add(p);
        CollisionComponent c = new CollisionComponent();
        c.w = w;
        c.h = h;
        e.add(c);
        e.add(new TriggerComponent());
        engine.addEntity(e);
        return e;
    }

    private Entity addPortal(float x, float y, float w, float h) {
        Entity e = engine.createEntity();
        PositionComponent p = new PositionComponent();
        p.x = x;
        p.y = y;
        e.add(p);
        CollisionComponent c = new CollisionComponent();
        c.w = w;
        c.h = h;
        e.add(c);
        e.add(new PortalComponent());
        engine.addEntity(e);
        return e;
    }

    @Test
    void firesOnceOnEntryAndAgainAfterLeavingAndReentering() {
        addTrigger(100f, 100f, 10f, 10f);

        engine.update(0.1f); // far away
        assertEquals(0, fired.size());

        playerPos.x = 100f;
        playerPos.y = 100f;
        engine.update(0.1f); // entered
        assertEquals(1, fired.size());

        engine.update(0.1f); // still inside — edge-triggered, no re-fire
        assertEquals(1, fired.size());

        playerPos.x = 0f;
        playerPos.y = 0f;
        engine.update(0.1f); // left
        assertEquals(1, fired.size());

        playerPos.x = 100f;
        playerPos.y = 100f;
        engine.update(0.1f); // re-entered
        assertEquals(2, fired.size());
    }

    @Test
    void resetSeedsOverlappingZonesSoTheyDoNotFireNextFrame() {
        addPortal(0f, 0f, 10f, 10f); // overlaps the player at spawn

        // Without seeding, the overlapping portal would fire on the next update (the ping-pong).
        system.reset();
        engine.update(0.1f);
        assertEquals(0, fired.size(), "a zone overlapping the spawn must not fire after reset");

        // Leaving then returning still fires normally.
        playerPos.x = 100f;
        engine.update(0.1f);
        playerPos.x = 0f;
        engine.update(0.1f);
        assertEquals(1, fired.size());
    }

    @Test
    void resetClearsStaleInsideStateFromPreviousMap() {
        Entity trigger = addTrigger(0f, 0f, 10f, 10f); // overlaps player
        engine.update(0.1f);
        assertEquals(1, fired.size()); // fired on entry

        // Simulate a world rebuild: same overlap, but reset should re-seed (not re-fire).
        system.reset();
        engine.update(0.1f);
        assertEquals(1, fired.size(), "reset re-seeds the still-overlapping zone instead of re-firing");
    }
}
