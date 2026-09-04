package com.prpg.items;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.items.config.ItemCatalog;
import com.prpg.items.config.ItemDefinition;
import com.prpg.ui.ColorTextures;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ItemRegistry {

    private static final String ITEMS_PATH = "items/items.yaml";
    private static final int ICON_SIZE = 24;

    private final ConfigLoader configLoader;
    private final ContentResolver content;
    private final ColorTextures colorTextures;
    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();
    private boolean loaded;

    @Inject
    public ItemRegistry(ConfigLoader configLoader, ContentResolver content, ColorTextures colorTextures) {
        this.configLoader = configLoader;
        this.content = content;
        this.colorTextures = colorTextures;
    }

    private void ensureLoaded() {
        if (loaded) return;
        try (Reader reader = content.resolve(ITEMS_PATH).reader()) {
            ItemCatalog catalog = configLoader.load(ItemCatalog.class, reader);
            if (catalog.items != null) {
                for (ItemDefinition def : catalog.items) {
                    items.put(def.id, def);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to load " + ITEMS_PATH, e);
        }
        loaded = true;
    }

    public ItemDefinition get(String itemId) {
        ensureLoaded();
        ItemDefinition def = items.get(itemId);
        if (def == null) {
            throw new IllegalArgumentException("unknown item id: " + itemId);
        }
        return def;
    }

    public boolean exists(String itemId) {
        ensureLoaded();
        return items.containsKey(itemId);
    }

    /** Placeholder colored swatch for the item, used by world pickups and the inventory grid. */
    public TextureRegion icon(String itemId) {
        ItemDefinition def = get(itemId);
        return colorTextures.swatch(def.color != null ? def.color : "AAAAAA", ICON_SIZE);
    }
}
