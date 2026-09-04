package com.prpg.ecs.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.prpg.ecs.component.AnimationComponent;
import com.prpg.ecs.component.TextureComponent;
import org.junit.jupiter.api.Test;

class AnimationSystemTest {

    @Test
    void advancesElapsedAndPicksFrameForCurrentTime() {
        TextureRegion frame0 = mock(TextureRegion.class);
        TextureRegion frame1 = mock(TextureRegion.class);
        Animation<TextureRegion> animation = new Animation<>(
                0.1f,
                new Array<>(new TextureRegion[]{frame0, frame1}),
                Animation.PlayMode.LOOP);

        AnimationComponent anim = new AnimationComponent();
        anim.animation = animation;
        TextureComponent tex = new TextureComponent();
        Entity entity = new Entity();
        entity.add(anim);
        entity.add(tex);

        Engine engine = new PooledEngine();
        engine.addSystem(new AnimationSystem());
        engine.addEntity(entity);

        engine.update(0.05f);
        assertSame(frame0, tex.region, "before first frame boundary");
        assertEquals(0.05f, anim.elapsed, 1e-6);

        engine.update(0.10f); // total elapsed = 0.15 -> frame index 1
        assertSame(frame1, tex.region, "after first frame boundary");
        assertEquals(0.15f, anim.elapsed, 1e-6);
    }
}
