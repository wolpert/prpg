package com.prpg.ecs.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.ecs.component.TextureComponent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RenderSystemTest {

    @Test
    void wrapsEachUpdateInBeginEndAndDrawsByAscendingZ() {
        SpriteBatch batch = mock(SpriteBatch.class);
        TextureRegion bgRegion = mockRegion(32, 32);
        TextureRegion midRegion = mockRegion(32, 32);
        TextureRegion fgRegion = mockRegion(32, 32);

        Engine engine = new PooledEngine();
        engine.addSystem(new RenderSystem(batch));

        // Add entities out of z-order to prove the sort, not insertion order, drives draw order.
        engine.addEntity(makeEntity(fgRegion, 50f, 60f, /*z=*/ 2, /*angle=*/ 0f));
        engine.addEntity(makeEntity(bgRegion, 10f, 20f, /*z=*/ 0, /*angle=*/ 0f));
        engine.addEntity(makeEntity(midRegion, 30f, 40f, /*z=*/ 1, /*angle=*/ 45f));
        engine.addEntity(makeEntityWithoutTexture(70f, 80f)); // ignored by family

        engine.update(0.016f);

        // RenderSystem also calls setColor() to apply per-entity tints; assert begin/draw/end
        // ordering and let the tint plumbing be tint-system-agnostic (covered by TintFlashTest).
        InOrder order = inOrder(batch);
        order.verify(batch).begin();
        order.verify(batch).draw(bgRegion, 10f, 20f, 16f, 16f, 32f, 32f, 1f, 1f, 0f);
        order.verify(batch).draw(midRegion, 30f, 40f, 16f, 16f, 32f, 32f, 1f, 1f, 45f);
        order.verify(batch).draw(fgRegion, 50f, 60f, 16f, 16f, 32f, 32f, 1f, 1f, 0f);
        order.verify(batch).end();
        verify(batch, atLeastOnce()).setColor(any(Color.class));
    }

    private static TextureRegion mockRegion(int w, int h) {
        TextureRegion region = mock(TextureRegion.class);
        when(region.getRegionWidth()).thenReturn(w);
        when(region.getRegionHeight()).thenReturn(h);
        return region;
    }

    private static Entity makeEntity(TextureRegion region, float x, float y, int z, float angle) {
        Entity entity = new Entity();
        PositionComponent pos = new PositionComponent();
        pos.x = x;
        pos.y = y;
        pos.z = z;
        pos.angle = angle;
        TextureComponent tex = new TextureComponent();
        tex.region = region;
        entity.add(pos);
        entity.add(tex);
        return entity;
    }

    private static Entity makeEntityWithoutTexture(float x, float y) {
        Entity entity = new Entity();
        PositionComponent pos = new PositionComponent();
        pos.x = x;
        pos.y = y;
        entity.add(pos);
        return entity;
    }
}
