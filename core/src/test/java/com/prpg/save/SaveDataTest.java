package com.prpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

/** Verifies the libGDX Json config round-trips SaveData without losing int values or types. */
class SaveDataTest {

    @Test
    void roundTripsAllFields() {
        SaveData data = new SaveData();
        data.mapId = "aunt_house_garden";
        data.playerX = 123.5f;
        data.playerY = 67.25f;
        data.facing = "LEFT";
        data.flags.add(new SaveData.Entry("read_will", 1));
        data.flags.add(new SaveData.Entry("hp", 5));
        data.inventory.add(new SaveData.Entry("wormwood_sprig", 4));
        data.inventory.add(new SaveData.Entry("ward_charm", 1));
        data.triggerHistory.add("margaret_sighting");

        Json json = SaveManager.createJson();
        String text = json.toJson(data);
        SaveData back = json.fromJson(SaveData.class, text);

        assertEquals("aunt_house_garden", back.mapId);
        assertEquals(123.5f, back.playerX);
        assertEquals(67.25f, back.playerY);
        assertEquals("LEFT", back.facing);

        assertEquals(2, back.flags.size());
        assertEquals("read_will", back.flags.get(0).key);
        assertEquals(1, back.flags.get(0).value);
        assertEquals("hp", back.flags.get(1).key);
        assertEquals(5, back.flags.get(1).value);

        assertEquals(2, back.inventory.size());
        assertEquals("wormwood_sprig", back.inventory.get(0).key);
        assertEquals(4, back.inventory.get(0).value);

        assertEquals(1, back.triggerHistory.size());
        assertEquals("margaret_sighting", back.triggerHistory.get(0));
    }

    @Test
    void versionRoundTrips() {
        SaveData data = new SaveData();
        data.mapId = "elder_hearth";
        Json json = SaveManager.createJson();
        SaveData back = json.fromJson(SaveData.class, json.toJson(data));
        assertEquals(SaveData.CURRENT_VERSION, back.version);
    }

    @Test
    void malformedJsonThrows() {
        // Documents the contract SaveManager.load() guards with try/catch: bad input throws here.
        Json json = SaveManager.createJson();
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> json.fromJson(SaveData.class, "{ this is not valid json "));
    }
}
