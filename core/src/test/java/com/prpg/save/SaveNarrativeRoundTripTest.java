package com.prpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

/**
 * Verifies the libGDX Json config round-trips the {@code narrative} and {@code staged} sections
 * without losing the nested collections; the element types are the fragile part, so this guards them.
 */
class SaveNarrativeRoundTripTest {

    @Test
    void narrativeAndStagingSectionsRoundTrip() {
        SaveData data = new SaveData();
        data.narrative.currentAct = "act2";
        data.narrative.unlockedActs.add("act1");
        data.narrative.unlockedActs.add("act2");
        SaveData.ActState act2State = new SaveData.ActState();
        act2State.actId = "act2";
        act2State.stateJson = "{\"flows\":{}}";
        data.narrative.inkActStates.add(act2State);

        data.stagedAct = "act2";
        SaveData.StagedOverride moved = new SaveData.StagedOverride();
        moved.id = "traveller";
        moved.mapId = "road";
        moved.marker = "traveller_spot";
        moved.solid = Boolean.FALSE;
        data.staged.add(moved);

        Json json = SaveManager.createJson();
        SaveData back = json.fromJson(SaveData.class, json.toJson(data));

        assertEquals("act2", back.narrative.currentAct);
        assertEquals(2, back.narrative.unlockedActs.size());
        assertTrue(back.narrative.unlockedActs.contains("act2"));
        assertEquals(1, back.narrative.inkActStates.size());
        assertEquals("act2", back.narrative.inkActStates.get(0).actId);
        assertEquals("{\"flows\":{}}", back.narrative.inkActStates.get(0).stateJson);

        assertEquals("act2", back.stagedAct);
        assertEquals(1, back.staged.size());
        assertEquals("road", back.staged.get(0).mapId);
        assertEquals(Boolean.FALSE, back.staged.get(0).solid);
    }

    @Test
    void saveWithoutNarrativeSectionDeserializesToDefaults() {
        String minimal = "{\"version\":1,\"mapId\":\"gatehouse_yard\",\"day\":2}";
        Json json = SaveManager.createJson();
        SaveData back = json.fromJson(SaveData.class, minimal);

        assertNull(back.narrative.currentAct, "the loader falls back to the catalog's first act");
        assertTrue(back.narrative.unlockedActs.isEmpty());
        assertTrue(back.staged.isEmpty());
    }
}
