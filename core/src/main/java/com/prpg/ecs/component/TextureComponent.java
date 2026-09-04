package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool;

public class TextureComponent implements Component, Pool.Poolable {
    public TextureRegion region;
    /**
     * Tint applied by {@code RenderSystem} via {@code SpriteBatch.setColor}. Defaults to white
     * (no tint). Mutated by {@code com.prpg.render.TintFlash} for short hit-flash
     * effects, but any system may set it.
     */
    public final Color tint = new Color(Color.WHITE);

    @Override
    public void reset() {
        region = null;
        tint.set(Color.WHITE);
    }
}
