package com.prpg.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.prpg.config.GameConfig;
import com.prpg.ecs.component.CollisionComponent;
import com.prpg.ecs.component.OrientationComponent;
import com.prpg.ecs.component.OrientationComponent.Direction;
import com.prpg.ecs.component.PlayerComponent;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.input.InputBindings;
import com.prpg.input.PointerInput;
import com.prpg.world.MapManager;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PlayerMovementSystem extends IteratingSystem {

    static final int PRIORITY = -10;

    private static final ComponentMapper<PositionComponent> POSITIONS =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<CollisionComponent> COLLISIONS =
            ComponentMapper.getFor(CollisionComponent.class);
    private static final ComponentMapper<OrientationComponent> ORIENTATIONS =
            ComponentMapper.getFor(OrientationComponent.class);

    private final InputBindings bindings;
    private final PointerInput pointer;
    private final MapManager mapManager;
    private final float speed;

    private boolean frozen;
    private boolean moving;

    @Inject
    public PlayerMovementSystem(InputBindings bindings, PointerInput pointer, MapManager mapManager,
                                GameConfig config) {
        super(Family.all(
                PlayerComponent.class,
                PositionComponent.class,
                CollisionComponent.class,
                OrientationComponent.class
        ).get(), PRIORITY);
        this.bindings = bindings;
        this.pointer = pointer;
        this.mapManager = mapManager;
        this.speed = config.player.speed;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    /**
     * Whether the player moved on the last processed frame — the single signal the animation logic
     * reads instead of re-polling movement keys, so the two can't drift. False while frozen.
     */
    public boolean isMoving() {
        return moving;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (frozen) {
            moving = false;
            return;
        }

        PositionComponent pos = POSITIONS.get(entity);
        CollisionComponent col = COLLISIONS.get(entity);
        OrientationComponent orient = ORIENTATIONS.get(entity);

        float dx = 0f;
        float dy = 0f;

        if (bindings.up()) dy += 1f;
        if (bindings.down()) dy -= 1f;
        if (bindings.left()) dx -= 1f;
        if (bindings.right()) dx += 1f;

        // Fall back to pointer (touch / mouse) intent when no movement key is held — this is what lets
        // the game be played without a keyboard. WorldScreen sets a unit vector toward the pointer.
        if (dx == 0f && dy == 0f) {
            dx = pointer.dirX();
            dy = pointer.dirY();
        }

        if (dx == 0f && dy == 0f) {
            moving = false;
            return;
        }
        moving = true;

        // Normalize to a unit vector so keyboard diagonals (±1,±1) and the analog pointer vector
        // both move at the same speed. (The old ×1/√2 only suited the keyboard's ±1 inputs.)
        float mag = (float) Math.sqrt(dx * dx + dy * dy);
        if (mag > 1f) {
            dx /= mag;
            dy /= mag;
        }

        // Face the dominant axis (vertical wins ties). Keying off the larger component — not "dy != 0"
        // — is what lets the analog pointer face LEFT/RIGHT: a mostly-horizontal aim has a tiny but
        // non-zero dy, which the old vertical-first test always resolved to UP/DOWN.
        if (Math.abs(dx) > Math.abs(dy)) {
            orient.direction = dx > 0f ? Direction.RIGHT : Direction.LEFT;
        } else {
            orient.direction = dy > 0f ? Direction.UP : Direction.DOWN;
        }

        float moveX = dx * speed * deltaTime;
        float moveY = dy * speed * deltaTime;

        float boxX = pos.x + col.offsetX;
        float boxY = pos.y + col.offsetY;

        // Separate-axis collision: try X first, then Y
        if (moveX != 0f && !mapManager.isAreaBlocked(boxX + moveX, boxY, col.w, col.h)) {
            pos.x += moveX;
        }

        boxX = pos.x + col.offsetX;
        if (moveY != 0f && !mapManager.isAreaBlocked(boxX, boxY + moveY, col.w, col.h)) {
            pos.y += moveY;
        }
    }
}
