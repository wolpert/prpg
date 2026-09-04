package com.prpg.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Input;
import com.prpg.ecs.component.CollisionComponent;
import com.prpg.ecs.component.InteractableComponent;
import com.prpg.ecs.component.OrientationComponent;
import com.prpg.ecs.component.OrientationComponent.Direction;
import com.prpg.ecs.component.PlayerComponent;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.input.PointerInput;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class InteractionSystem extends EntitySystem {

    static final int PRIORITY = -9;
    private static final float PROBE_SIZE = 16f;

    private static final ComponentMapper<PositionComponent> POSITIONS =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<CollisionComponent> COLLISIONS =
            ComponentMapper.getFor(CollisionComponent.class);
    private static final ComponentMapper<OrientationComponent> ORIENTATIONS =
            ComponentMapper.getFor(OrientationComponent.class);

    private final Input input;
    private final PointerInput pointer;
    private final List<Consumer<Entity>> listeners = new ArrayList<>();
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> targets;

    @Inject
    public InteractionSystem(Input input, PointerInput pointer) {
        super(PRIORITY);
        this.input = input;
        this.pointer = pointer;
    }

    /** Listener receives the interactable target entity; pull its components as needed. */
    public void addListener(Consumer<Entity> listener) {
        listeners.add(listener);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
                PlayerComponent.class, PositionComponent.class,
                CollisionComponent.class, OrientationComponent.class
        ).get());
        targets = engine.getEntitiesFor(Family.all(
                InteractableComponent.class, PositionComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        boolean pressed = input.isKeyJustPressed(Input.Keys.E)
                || input.isKeyJustPressed(Input.Keys.SPACE)
                || pointer.consumeInteract(); // a tap on the world (touch / mouse)
        if (!pressed) return;

        for (int i = 0; i < players.size(); i++) {
            Entity player = players.get(i);
            PositionComponent pPos = POSITIONS.get(player);
            CollisionComponent pCol = COLLISIONS.get(player);
            OrientationComponent pOrient = ORIENTATIONS.get(player);

            float probeX = pPos.x + pCol.offsetX + pCol.w / 2f - PROBE_SIZE / 2f;
            float probeY = pPos.y + pCol.offsetY + pCol.h / 2f - PROBE_SIZE / 2f;

            Direction dir = pOrient.direction;
            if (dir == Direction.UP) probeY += pCol.h;
            else if (dir == Direction.DOWN) probeY -= PROBE_SIZE;
            else if (dir == Direction.RIGHT) probeX += pCol.w;
            else if (dir == Direction.LEFT) probeX -= PROBE_SIZE;

            for (int j = 0; j < targets.size(); j++) {
                Entity target = targets.get(j);
                PositionComponent tPos = POSITIONS.get(target);
                CollisionComponent tCol = COLLISIONS.has(target) ? COLLISIONS.get(target) : null;

                float tx = tPos.x + (tCol != null ? tCol.offsetX : 0f);
                float ty = tPos.y + (tCol != null ? tCol.offsetY : 0f);
                float tw = tCol != null ? tCol.w : 16f;
                float th = tCol != null ? tCol.h : 16f;

                if (overlaps(probeX, probeY, PROBE_SIZE, PROBE_SIZE, tx, ty, tw, th)) {
                    for (Consumer<Entity> listener : listeners) {
                        listener.accept(target);
                    }
                    return;
                }
            }
        }
    }

    private static boolean overlaps(float ax, float ay, float aw, float ah,
                                    float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
}
