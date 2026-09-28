package com.prpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

/**
 * Verifies the libGDX Json config round-trips the {@code narrative} section without losing the
 * nested collections; the element types are the fragile part, so this guards them.
 */
class SaveNarrativeRoundTripTest {

    @Test
    void narrativeSectionRoundTrips() {
        SaveData data = new SaveData();
        data.narrative.currentAct = "act2";
        data.narrative.unlockedActs.add("act1");
        data.narrative.unlockedActs.add("act2");
        SaveData.ActState act2State = new SaveData.ActState();
        act2State.actId = "act2";
        act2State.stateJson = "{\"flows\":{}}";
        data.narrative.inkActStates.add(act2State);

        Json json = SaveManager.createJson();
        SaveData back = json.fromJson(SaveData.class, json.toJson(data));

        assertEquals("act2", back.narrative.currentAct);
        assertEquals(2, back.narrative.unlockedActs.size());
        assertTrue(back.narrative.unlockedActs.contains("act2"));
        assertEquals(1, back.narrative.inkActStates.size());
        assertEquals("act2", back.narrative.inkActStates.get(0).actId);
        assertEquals("{\"flows\":{}}", back.narrative.inkActStates.get(0).stateJson);
    }

    @Test
    void saveWithoutNarrativeSectionDeserializesToDefaults() {
        String minimal = "{\"version\":2,\"mapId\":\"gatehouse_yard\"}";
        Json json = SaveManager.createJson();
        SaveData back = json.fromJson(SaveData.class, minimal);

        assertNull(back.narrative.currentAct, "the loader falls back to the catalog's first act");
        assertTrue(back.narrative.unlockedActs.isEmpty());
        assertTrue(back.variables.isEmpty());
    }

    @Test
    void aVersionOneSaveStillParsesSoTheLoaderCanDiscardIt() {
        // v1 saves carried flags/triggerHistory/staged; the loader must be able to read the version
        // (to discard the save cleanly) rather than crash on the unknown fields.
        String v1 = "{\"version\":1,\"day\":2,\"flags\":[{\"key\":\"act1.met_keeper\",\"value\":1}],"
                + "\"triggerHistory\":[\"gate_arch\"],\"stagedAct\":\"act1\",\"staged\":[]}";
        assertEquals(1, SaveManager.createJson().fromJson(SaveData.class, v1).version);
    }
}
