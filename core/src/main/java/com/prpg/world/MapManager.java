package com.prpg.world;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.prpg.content.ContentResolver;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Owns the active TMX map and the single source of truth for which map is current
 * ({@link #currentMapId}). Which map a playthrough starts on is the current act's business (its
 * pack declares an entry map), so this class carries no default map at all.
 *
 * <p>Collision is pure grid math over a {@code collision} tile layer (any non-empty cell blocks),
 * plus runtime {@link Blocker}s that staged content registers. There is no physics engine.
 */
@Singleton
public class MapManager {

    private static final String MAP_DIR = "maps/";

    // Resolve maps (and their relatively-referenced external .tsx tilesets) through the content
    // seam rather than the default internal resolver, so a mounted pack's maps load the same way.
    private final TmxMapLoader loader;
    // Force Nearest filtering on tileset textures so tiles stay crisp under the stretched viewport.
    private final TmxMapLoader.Parameters loaderParams = nearestParams();
    private final Map<String, Vector2> savedPositions = new HashMap<>();
    /**
     * Runtime blockers contributed by staged content (an obstacle barring a doorway, a character who
     * bars a path). The static {@code collision} layer can't express these: a solved activity has to
     * <em>stop</em> blocking, and painted tiles can never be un-painted at runtime.
     */
    private final List<Blocker> blockers = new ArrayList<>();

    /** Reused for overlap tests so the per-frame blocker sweep allocates nothing. */
    private final Rectangle scratch = new Rectangle();

    private TiledMap currentMap;
    private String currentMapId;
    private TiledMapTileLayer collisionLayer;
    private int tileWidth;
    private int tileHeight;
    private int mapWidthPx;
    private int mapHeightPx;

    @Inject
    public MapManager(ContentResolver content) {
        this.loader = new TmxMapLoader(content.fileHandleResolver());
    }

    private static TmxMapLoader.Parameters nearestParams() {
        TmxMapLoader.Parameters p = new TmxMapLoader.Parameters();
        p.textureMinFilter = Texture.TextureFilter.Nearest;
        p.textureMagFilter = Texture.TextureFilter.Nearest;
        return p;
    }

    /**
     * A dynamic blocker. A blocker spawned on top of the player starts <em>graced</em> (inert) and
     * only starts blocking once they step clear, so making something solid mid-story can never seal
     * the player inside it.
     */
    private static final class Blocker {
        final Rectangle rect;
        boolean graced;

        Blocker(Rectangle rect, boolean graced) {
            this.rect = rect;
            this.graced = graced;
        }
    }

    /** Sets the current map id without loading it (loading needs GL; happens in {@link #loadMap}). */
    public void setCurrentMapId(String mapId) {
        this.currentMapId = mapId;
    }

    public void loadMap(String mapId) {
        if (currentMap != null) {
            currentMap.dispose();
        }
        currentMapId = mapId;
        blockers.clear(); // a new map's staged blockers are registered as its content spawns
        currentMap = loader.load(MAP_DIR + mapId + ".tmx", loaderParams);

        collisionLayer = (TiledMapTileLayer) currentMap.getLayers().get("collision");
        if (collisionLayer == null) {
            Log.info("MapManager", "map '" + mapId + "' has no 'collision' layer; nothing will block movement");
        }

        TiledMapTileLayer ground = (TiledMapTileLayer) currentMap.getLayers().get("ground");
        if (ground != null) {
            tileWidth = (int) ground.getTileWidth();
            tileHeight = (int) ground.getTileHeight();
            mapWidthPx = ground.getWidth() * tileWidth;
            mapHeightPx = ground.getHeight() * tileHeight;
        } else {
            Log.error("MapManager", "map '" + mapId + "' has no 'ground' layer; tile size and map bounds unset");
        }
    }

    public Vector2 getSpawnPoint() {
        if (savedPositions.containsKey(currentMapId)) {
            return savedPositions.get(currentMapId).cpy();
        }
        MapLayer objects = currentMap.getLayers().get("spawn");
        if (objects != null) {
            for (MapObject obj : objects.getObjects()) {
                if (obj instanceof RectangleMapObject rmo) {
                    Rectangle r = rmo.getRectangle();
                    return new Vector2(r.x, r.y);
                }
            }
        }
        Log.info("MapManager", "no spawn object on '" + currentMapId + "' (using default tile 3,3)");
        return new Vector2(tileWidth * 3, tileHeight * 3);
    }

    /** Finds a named object in the "spawn" layer; null if absent. Used by portals and act entry. */
    public Vector2 getNamedSpawn(String name) {
        MapLayer objects = currentMap.getLayers().get("spawn");
        if (objects != null) {
            for (MapObject obj : objects.getObjects()) {
                if (name.equals(obj.getName()) && obj instanceof RectangleMapObject rmo) {
                    Rectangle r = rmo.getRectangle();
                    return new Vector2(r.x, r.y);
                }
            }
        }
        Log.info("MapManager", "requested spawn '" + name + "' not found on '" + currentMapId + "'");
        return null;
    }

    public void savePlayerPosition(String mapId, float x, float y) {
        savedPositions.computeIfAbsent(mapId, k -> new Vector2()).set(x, y);
    }

    /** Forgets remembered per-map positions (a new game or an act change). */
    public void clearSavedPositions() {
        savedPositions.clear();
    }

    /**
     * The rectangle of a named object in the {@code markers} layer, or null if absent. Markers are
     * pure geography, named anchor points a map owns, so act staging can place content by name and
     * survive the map being re-laid-out. A marker's own width/height is the placed content's footprint.
     */
    public Rectangle getMarker(String name) {
        if (name == null || currentMap == null) return null;
        MapLayer layer = currentMap.getLayers().get("markers");
        if (layer == null) return null;
        for (MapObject obj : layer.getObjects()) {
            if (name.equals(obj.getName()) && obj instanceof RectangleMapObject rmo) {
                return rmo.getRectangle();
            }
        }
        return null;
    }

    /**
     * Registers a runtime blocker. {@code playerBox} (nullable) is the player's current collision
     * box: a blocker overlapping it is registered graced, so it can't trap them where they stand.
     */
    public void addBlocker(Rectangle rect, Rectangle playerBox) {
        if (rect == null) return;
        boolean graced = playerBox != null && playerBox.overlaps(rect);
        if (graced) {
            Log.debug("MapManager", "blocker at " + rect + " spawned on the player; inert until they step clear");
        }
        blockers.add(new Blocker(new Rectangle(rect), graced));
    }

    public void clearBlockers() {
        blockers.clear();
    }

    /** Promotes any graced blocker the player no longer overlaps into a real one. Once per frame. */
    public void updateBlockers(float x, float y, float w, float h) {
        if (blockers.isEmpty()) return;
        scratch.set(x, y, w, h);
        for (Blocker b : blockers) {
            if (b.graced && !b.rect.overlaps(scratch)) {
                b.graced = false;
            }
        }
    }

    public MapObjects getObjectLayer(String layerName) {
        MapLayer layer = currentMap.getLayers().get(layerName);
        return layer != null ? layer.getObjects() : null;
    }

    public boolean isTileBlocked(int tileX, int tileY) {
        if (collisionLayer == null) return false;
        if (tileX < 0 || tileY < 0) return true;
        if (tileX >= collisionLayer.getWidth() || tileY >= collisionLayer.getHeight()) return true;
        return collisionLayer.getCell(tileX, tileY) != null;
    }

    public boolean isAreaBlocked(float x, float y, float w, float h) {
        // Reject anything past the origin outright: (int) truncates toward zero, so a box at
        // x = -5 would otherwise map to tile 0 and slip past the left/bottom edge.
        if (x < 0 || y < 0) return true;
        int startX = MathUtils.floor(x / tileWidth);
        int endX = MathUtils.floor((x + w - 0.01f) / tileWidth);
        int startY = MathUtils.floor(y / tileHeight);
        int endY = MathUtils.floor((y + h - 0.01f) / tileHeight);
        for (int tx = startX; tx <= endX; tx++) {
            for (int ty = startY; ty <= endY; ty++) {
                if (isTileBlocked(tx, ty)) return true;
            }
        }
        return isBlockerBlocked(x, y, w, h);
    }

    /** Whether any active (non-graced) dynamic blocker overlaps the given box. */
    private boolean isBlockerBlocked(float x, float y, float w, float h) {
        if (blockers.isEmpty()) return false;
        scratch.set(x, y, w, h);
        for (Blocker b : blockers) {
            if (!b.graced && b.rect.overlaps(scratch)) return true;
        }
        return false;
    }

    public TiledMap getMap() { return currentMap; }
    public String getCurrentMapId() { return currentMapId; }
    public TiledMapTileLayer getCollisionLayer() { return collisionLayer; }
    public int getTileWidth() { return tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public int getMapWidthPx() { return mapWidthPx; }
    public int getMapHeightPx() { return mapHeightPx; }

    public void dispose() {
        if (currentMap != null) {
            currentMap.dispose();
            currentMap = null;
        }
    }
}
