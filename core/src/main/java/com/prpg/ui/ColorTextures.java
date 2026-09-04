package com.prpg.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Generates and caches solid-color square textures for placeholder item/piece icons.
 * Swap for real atlas art later — callers only depend on {@link TextureRegion}.
 */
@Singleton
public class ColorTextures implements Disposable {

    private final Map<String, TextureRegion> cache = new HashMap<>();
    private final List<Texture> textures = new ArrayList<>();

    @Inject
    public ColorTextures() {}

    /** Returns a square TextureRegion of the given color (hex like "8B4513") at the given size. */
    public TextureRegion swatch(String hexColor, int size) {
        String key = hexColor + "@" + size;
        TextureRegion cached = cache.get(key);
        if (cached != null) return cached;

        Color color = parseColor(hexColor);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        // Thin darker border so adjacent swatches read as distinct tiles.
        pixmap.setColor(0f, 0f, 0f, 0.4f);
        pixmap.drawRectangle(0, 0, size, size);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        textures.add(texture);
        TextureRegion region = new TextureRegion(texture);
        cache.put(key, region);
        return region;
    }

    /** Returns a solid (borderless) rectangle TextureRegion of the given color and size. */
    public TextureRegion rect(String hexColor, int w, int h) {
        String key = "rect:" + hexColor + "@" + w + "x" + h;
        TextureRegion cached = cache.get(key);
        if (cached != null) return cached;

        Color color = parseColor(hexColor);
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        textures.add(texture);
        TextureRegion region = new TextureRegion(texture);
        cache.put(key, region);
        return region;
    }

    /** Returns a filled circle TextureRegion of the given color (hex) at the given diameter. */
    public TextureRegion circle(String hexColor, int size) {
        String key = "circle:" + hexColor + "@" + size;
        TextureRegion cached = cache.get(key);
        if (cached != null) return cached;

        Color color = parseColor(hexColor);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(color);
        int r = size / 2;
        pixmap.fillCircle(r, r, r - 1);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        textures.add(texture);
        TextureRegion region = new TextureRegion(texture);
        cache.put(key, region);
        return region;
    }

    private static Color parseColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            Log.debug("ColorTextures", "null/empty hex color, using magenta placeholder");
            return Color.MAGENTA.cpy();
        }
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return new Color(r / 255f, g / 255f, b / 255f, 1f);
        } catch (RuntimeException e) {
            Log.debug("ColorTextures", "unparseable hex color \"" + hex + "\", using magenta placeholder");
            return Color.MAGENTA.cpy();
        }
    }

    @Override
    public void dispose() {
        for (Texture t : textures) {
            t.dispose();
        }
        textures.clear();
        cache.clear();
    }
}
