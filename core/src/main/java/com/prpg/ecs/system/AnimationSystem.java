package com.prpg.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.prpg.ecs.component.AnimationComponent;
import com.prpg.ecs.component.TextureComponent;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AnimationSystem extends IteratingSystem {

    static final int PRIORITY = 0;

    private final ComponentMapper<AnimationComponent> animations = ComponentMapper.getFor(AnimationComponent.class);
    private final ComponentMapper<TextureComponent> textures = ComponentMapper.getFor(TextureComponent.class);

    @Inject
    public AnimationSystem() {
        super(Family.all(AnimationComponent.class, TextureComponent.class).get(), PRIORITY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AnimationComponent anim = animations.get(entity);
        anim.elapsed += deltaTime;
        // Use the playMode-respecting overload — LOOP animations loop, NORMAL animations stop on
        // the last frame. The two-arg overload would override the Animation's intrinsic playMode.
        textures.get(entity).region = anim.animation.getKeyFrame(anim.elapsed);
    }
}
