package com.prpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.badlogic.gdx.utils.Json;
import com.prpg.narrative.StoryVariables;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the libGDX Json config round-trips SaveData, and story variables keep their types. */
class SaveDataTest {

    @Test
    void roundTripsAllFields() {
        SaveData data = new SaveData();
        data.mapId = "gatehouse_hall";
        data.playerX = 123.5f;
        data.playerY = 67.25f;
        data.facing = "LEFT";
        data.variables.add(SaveManager.toSaved("met_keeper", true));
        data.variables.add(SaveManager.toSaved("day", 5));
        data.variables.add(SaveManager.toSaved("keeper_mood",
                new StoryVariables.ListValue(List.of("mood"), List.of("mood.calm", "mood.wary"))));
        data.inventory.add(new SaveData.Entry("brass_token", 4));

        Json json = SaveManager.createJson();
        SaveData back = json.fromJson(SaveData.class, json.toJson(data));

        assertEquals("gatehouse_hall", back.mapId);
        assertEquals(123.5f, back.playerX);
        assertEquals(67.25f, back.playerY);
        assertEquals("LEFT", back.facing);

        assertEquals(3, back.variables.size());
        assertEquals(true, SaveManager.fromSaved(back.variables.get(0)));
        assertEquals(5, SaveManager.fromSaved(back.variables.get(1)));
        assertEquals(new StoryVariables.ListValue(List.of("mood"), List.of("mood.calm", "mood.wary")),
                SaveManager.fromSaved(back.variables.get(2)));

        assertEquals(1, back.inventory.size());
        assertEquals("brass_token", back.inventory.get(0).key);
        assertEquals(4, back.inventory.get(0).value);
    }

    @Test
    void everyVariableTypeSurvivesItsTextForm() {
        for (Object value : List.of(true, 0, -3, 2.5f, "", "a, b",
                new StoryVariables.ListValue(List.of("mood"), List.of()))) {
            assertEquals(value, SaveManager.fromSaved(SaveManager.toSaved("v", value)), String.valueOf(value));
        }
    }

    @Test
    void unreadableVariablesAreDroppedNotFatal() {
        SaveData.Variable bad = new SaveData.Variable();
        bad.name = "n";
        bad.type = "int";
        bad.value = "not a number";
        assertNull(SaveManager.fromSaved(bad));
        bad.type = "martian";
        assertNull(SaveManager.fromSaved(bad));
    }

    @Test
    void versionRoundTrips() {
        SaveData data = new SaveData();
        data.mapId = "gatehouse_yard";
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
