package com.prpg.render;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.prpg.ecs.component.TextureComponent;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Generic tint-flash system. Call {@link #flash(Entity, Color, float)} to set an entity's
 * {@link TextureComponent#tint} to a color (e.g. red on hit) for a short duration; this system
 * lerps the tint back to white over that duration and clears its bookkeeping when done.
 *
 * <p>Lives in scaffold so any game can flash any sprite. The flash registry is per-entity and
 * keyed by identity — entities removed from the engine while flashing have their entry GC'd on
 * the next tick when the entity isn't found in the family view.
 */
@Singleton
public class TintFlash extends IteratingSystem {

    /** Runs early so tint is settled before {@code RenderSystem} reads it. */
    static final int PRIORITY = 8;

    private final ComponentMapper<TextureComponent> textures = ComponentMapper.getFor(TextureComponent.class);
    private final Map<Entity, Flash> active = new IdentityHashMap<>();
    private final Color scratch = new Color();

    @Inject
    public TintFlash() {
        super(Family.all(TextureComponent.class).get(), PRIORITY);
    }

    /**
     * Begin a flash on {@code entity}. The entity must carry a {@link TextureComponent}; the
     * tint will lerp from {@code color} back to white over {@code durationSec}.
     */
    public void flash(Entity entity, Color color, float durationSec) {
        if (entity == null || color == null || durationSec <= 0f) return;
        Flash f = active.computeIfAbsent(entity, k -> new Flash());
        f.color.set(color);
        f.remaining = durationSec;
        f.total = durationSec;
        // Apply immediately so the very first frame sees the flash colour, even before the
        // family iteration on this tick.
        TextureComponent tex = textures.get(entity);
        if (tex != null) tex.tint.set(color);
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        // Drop stale entries (entity has no TextureComponent or has fully decayed). Iterating with
        // the explicit Iterator so we can remove without ConcurrentModification.
        Iterator<Map.Entry<Entity, Flash>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Flash> e = it.next();
            if (e.getValue().remaining <= 0f) {
                TextureComponent tex = textures.get(e.getKey());
                if (tex != null) tex.tint.set(Color.WHITE);
                it.remove();
            }
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Flash f = active.get(entity);
        if (f == null) return;
        f.remaining -= deltaTime;
        TextureComponent tex = textures.get(entity);
        if (tex == null) return;
        if (f.remaining <= 0f) {
            tex.tint.set(Color.WHITE);
            return;
        }
        // Linear lerp from the flash color back to white as the timer drains toward zero.
        float t = 1f - (f.remaining / f.total); // 0 at start → 1 at end
        scratch.set(f.color).lerp(Color.WHITE, t);
        tex.tint.set(scratch);
    }

    /** Per-entity flash bookkeeping. Pulled out so the inner-class is reusable on the same entity. */
    private static final class Flash {
        final Color color = new Color();
        float remaining;
        float total;
    }
}
