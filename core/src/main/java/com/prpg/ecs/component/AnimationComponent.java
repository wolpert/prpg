package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool;

public class AnimationComponent implements Component, Pool.Poolable {
    public Animation<TextureRegion> animation;
    public float elapsed;

    @Override
    public void reset() {
        animation = null;
        elapsed = 0f;
    }
}
