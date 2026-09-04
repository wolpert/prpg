package com.prpg.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.prpg.content.ContentResolver;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Headless coverage of {@link MapManager} collision math. The TMX layer is plain data (no GL), so
 * we build a 5x5 collision layer directly and inject it via reflection — exercising the real
 * tile-mapping arithmetic without loading a map file. Guards the left/bottom walk-off fix (#17).
 */
class MapManagerTest {

    private static final int TILE = 16;

    private MapManager mapManager;

    @BeforeEach
    void setUp() throws Exception {
        // Collision math only — no map is loaded, so the resolver is never exercised.
        mapManager = new MapManager(mock(ContentResolver.class));

        TiledMapTileLayer layer = new TiledMapTileLayer(5, 5, TILE, TILE);
        layer.setCell(1, 1, new TiledMapTileLayer.Cell()); // tile (1,1) is solid

        set("collisionLayer", layer);
        set("tileWidth", TILE);
        set("tileHeight", TILE);
    }

    private void set(String field, Object value) throws Exception {
        Field f = MapManager.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(mapManager, value);
    }

    @Test
    void tileBoundsAndContents() {
        assertTrue(mapManager.isTileBlocked(1, 1), "the solid cell blocks");
        assertFalse(mapManager.isTileBlocked(0, 0), "an empty cell is open");
        assertTrue(mapManager.isTileBlocked(-1, 0), "negative tile blocks");
        assertTrue(mapManager.isTileBlocked(5, 0), "out-of-bounds tile blocks");
    }

    @Test
    void negativeCoordinatesAreBlocked() {
        // The #17 bug: (int)(-5 / 16) truncates toward zero to tile 0, which is open, so the player
        // could slip past the origin. The fix rejects x < 0 / y < 0 outright.
        assertTrue(mapManager.isAreaBlocked(-5f, 20f, 8f, 8f), "a box past the left edge is blocked");
        assertTrue(mapManager.isAreaBlocked(20f, -5f, 8f, 8f), "a box past the bottom edge is blocked");
    }

    @Test
    void areaOverlappingSolidTileIsBlocked() {
        // Tile (1,1) spans world [16,32) x [16,32).
        assertTrue(mapManager.isAreaBlocked(18f, 18f, 4f, 4f), "a box inside the solid tile is blocked");
    }

    @Test
    void areaOverOpenGroundIsClear() {
        assertFalse(mapManager.isAreaBlocked(0f, 0f, 4f, 4f), "open ground at the origin is clear");
    }

    // --- dynamic blockers ---------------------------------------------------------------------

    @Test
    void dynamicBlockerBlocksWithoutACollisionTile() {
        // Tile (2,2) is open in the collision layer — only the runtime blocker stops movement.
        assertFalse(mapManager.isAreaBlocked(34f, 34f, 8f, 8f), "open before the blocker exists");
        mapManager.addBlocker(new Rectangle(32f, 32f, 16f, 16f), null);
        assertTrue(mapManager.isAreaBlocked(34f, 34f, 8f, 8f), "the blocker now bars the way");

        // This is what a solved puzzle does: the obstacle stops being staged, so it stops blocking.
        mapManager.clearBlockers();
        assertFalse(mapManager.isAreaBlocked(34f, 34f, 8f, 8f), "clearing reopens the way");
    }

    @Test
    void aBlockerSpawnedOnThePlayerIsInertUntilTheyStepClear() {
        Rectangle playerBox = new Rectangle(34f, 34f, 8f, 8f);
        mapManager.addBlocker(new Rectangle(32f, 32f, 16f, 16f), playerBox);
        assertFalse(mapManager.isAreaBlocked(34f, 34f, 8f, 8f),
                "a blocker that appears on top of the player must not seal them inside it");

        // Still graced while they overlap it...
        mapManager.updateBlockers(34f, 34f, 8f, 8f);
        assertFalse(mapManager.isAreaBlocked(34f, 34f, 8f, 8f));

        // ...and live the moment they are clear.
        mapManager.updateBlockers(80f, 80f, 8f, 8f);
        assertTrue(mapManager.isAreaBlocked(34f, 34f, 8f, 8f), "it blocks once the player has left");
    }
}
