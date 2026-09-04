package com.prpg.save;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.prpg.content.ContentRoot;
import com.prpg.items.Inventory;
import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.util.Log;
import com.prpg.world.FlagStore;
import com.prpg.world.GameClock;
import com.prpg.world.TriggerHistory;
import com.prpg.world.stage.StageDirector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * One-slot save/load for {@code save.json}, written under the per-user {@link ContentRoot} (desktop:
 * {@code ~/.<app>/save.json}; Android: the app-private files dir) so it is independent of the
 * process working directory.
 */
@Singleton
public class SaveManager {

    public static final String SAVE_FILE = "save.json";

    private final ContentRoot root;
    private final Inventory inventory;
    private final FlagStore flagStore;
    private final TriggerHistory triggerHistory;
    private final GameClock clock;
    private final NarrativeState narrativeState;
    private final NarrativeRunner narrativeRunner;
    private final StageDirector stageDirector;
    private final ActContentRegistry acts;

    // Single-thread daemon executor so frequent autosaves don't serialize+write on the GL thread.
    // Single-threaded => writes are serialized, so a later autosave never races an earlier one.
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "save-writer");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public SaveManager(ContentRoot root, Inventory inventory, FlagStore flagStore,
                       TriggerHistory triggerHistory, GameClock clock, NarrativeState narrativeState,
                       NarrativeRunner narrativeRunner, StageDirector stageDirector,
                       ActContentRegistry acts) {
        this.root = root;
        this.inventory = inventory;
        this.flagStore = flagStore;
        this.triggerHistory = triggerHistory;
        this.clock = clock;
        this.narrativeState = narrativeState;
        this.narrativeRunner = narrativeRunner;
        this.stageDirector = stageDirector;
        this.acts = acts;
    }

    private FileHandle saveFile() {
        return root.writable(SAVE_FILE);
    }

    /** Configures a Json instance with explicit list element types (avoids generic-Map pitfalls). */
    public static Json createJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setElementType(SaveData.class, "flags", SaveData.Entry.class);
        json.setElementType(SaveData.class, "inventory", SaveData.Entry.class);
        json.setElementType(SaveData.class, "triggerHistory", String.class);
        json.setElementType(SaveData.class, "staged", SaveData.StagedOverride.class);
        json.setElementType(SaveData.Narrative.class, "unlockedActs", String.class);
        json.setElementType(SaveData.Narrative.class, "inkActStates", SaveData.ActState.class);
        return json;
    }

    public boolean hasSave() {
        return saveFile().exists();
    }

    /**
     * Captures the singletons' state plus the supplied player/map position and writes the file.
     * Returns whether the write succeeded so the caller doesn't report success on a failed save.
     */
    public boolean save(String mapId, float playerX, float playerY, String facing) {
        String json = serialize(mapId, playerX, playerY, facing);
        try {
            saveFile().writeString(json, false);
            Log.debug("SaveManager", "wrote save (map=" + mapId + ", day=" + clock.getDay() + ", async=false)");
            return true;
        } catch (Exception e) {
            Log.error("SaveManager", "save failed for map " + mapId, e);
            return false;
        }
    }

    /**
     * Like {@link #save} but performs the file write off the GL thread. State is still captured and
     * serialized synchronously (so it reflects this exact frame), then handed to the executor.
     */
    public void saveAsync(String mapId, float playerX, float playerY, String facing) {
        String json = serialize(mapId, playerX, playerY, facing);
        int day = clock.getDay();
        FileHandle file = saveFile();
        var unused = writeExecutor.submit(() -> {
            try {
                file.writeString(json, false);
                Log.debug("SaveManager", "wrote save (map=" + mapId + ", day=" + day + ", async=true)");
            } catch (Exception e) {
                Log.error("SaveManager", "async save failed for map " + mapId, e);
            }
        });
    }

    /** Captures live state into a SaveData and serializes it to JSON. Must run on the GL thread. */
    private String serialize(String mapId, float playerX, float playerY, String facing) {
        SaveData data = new SaveData();
        data.mapId = mapId;
        data.playerX = playerX;
        data.playerY = playerY;
        data.facing = facing;
        clock.setLastPlayedMillis(System.currentTimeMillis());
        data.day = clock.getDay();
        data.lastPlayedMillis = clock.getLastPlayedMillis();

        for (Map.Entry<String, Integer> e : flagStore.getAll().entrySet()) {
            data.flags.add(new SaveData.Entry(e.getKey(), e.getValue()));
        }
        // Sum inventory slots per item id (Inventory.add rebuilds slots on load).
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Inventory.Slot slot : inventory.getSlots()) {
            counts.merge(slot.itemId, slot.count, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            data.inventory.add(new SaveData.Entry(e.getKey(), e.getValue()));
        }
        data.triggerHistory.addAll(triggerHistory.getFired());

        SaveData.Narrative n = data.narrative;
        n.currentAct = narrativeState.getCurrentActId();
        n.unlockedActs.addAll(narrativeState.getUnlockedActs());
        for (Map.Entry<String, String> e : narrativeRunner.captureActStates().entrySet()) {
            SaveData.ActState as = new SaveData.ActState();
            as.actId = e.getKey();
            as.stateJson = e.getValue();
            n.inkActStates.add(as);
        }

        data.stagedAct = stageDirector.currentActId();
        for (StageDirector.StagedOverride o : stageDirector.overrides()) {
            SaveData.StagedOverride so = new SaveData.StagedOverride();
            so.id = o.id;
            so.removed = o.removed;
            so.mapId = o.mapId;
            so.marker = o.marker;
            so.dialogue = o.dialogue;
            so.solid = o.solid;
            data.staged.add(so);
        }

        return createJson().toJson(data);
    }

    /**
     * Reads the save file, applies inventory/flags/trigger-history/narrative/staging, and returns the
     * data. Returns null (so callers fall back to a new game) if the file is missing or corrupt.
     */
    public SaveData load() {
        FileHandle file = saveFile();
        if (!file.exists()) return null;

        SaveData data;
        try {
            data = createJson().fromJson(SaveData.class, file.readString());
        } catch (Exception e) {
            Log.error("SaveManager", "corrupt save; ignoring", e);
            return null;
        }
        if (data == null) {
            Log.error("SaveManager", "save file parsed to null (empty/corrupt); starting fresh");
            return null;
        }

        data = migrate(data);

        Map<String, Integer> flags = new LinkedHashMap<>();
        for (SaveData.Entry e : data.flags) flags.put(e.key, e.value);
        flagStore.loadAll(flags);

        Set<String> fired = new HashSet<>(data.triggerHistory);
        triggerHistory.loadAll(fired);

        inventory.clear();
        for (SaveData.Entry e : data.inventory) inventory.add(e.key, e.value);

        clock.setDay(data.day);
        clock.setLastPlayedMillis(data.lastPlayedMillis);

        // Restore the canonical narrative model, then re-seed the (ephemeral) Ink stories.
        SaveData.Narrative n = data.narrative != null ? data.narrative : new SaveData.Narrative();
        narrativeState.reset();
        String current = n.currentAct != null ? n.currentAct : acts.firstActId();
        narrativeState.setCurrentActId(current);
        narrativeState.unlockAct(current);
        for (String act : n.unlockedActs) narrativeState.unlockAct(act);
        narrativeRunner.clear();
        for (SaveData.ActState as : n.inkActStates) {
            narrativeRunner.restoreActState(as.actId, as.stateJson);
        }

        // Stage the saved act (re-reading its declarative rules), then lay the saved overrides on top.
        stageDirector.enterAct(data.stagedAct != null ? data.stagedAct : current);
        List<StageDirector.StagedOverride> overrides = new ArrayList<>();
        for (SaveData.StagedOverride so : data.staged) {
            StageDirector.StagedOverride o = new StageDirector.StagedOverride(so.id);
            o.removed = so.removed;
            o.mapId = so.mapId;
            o.marker = so.marker;
            o.dialogue = so.dialogue;
            o.solid = so.solid;
            overrides.add(o);
        }
        stageDirector.restoreOverrides(overrides);

        return data;
    }

    /**
     * Upgrades a loaded save to the current schema. Add stepwise migrations keyed on
     * {@code data.version} as the schema evolves; a missing field deserializes to its default, which
     * is often already the right migration.
     */
    private SaveData migrate(SaveData data) {
        if (data.version < SaveData.CURRENT_VERSION) {
            data.version = SaveData.CURRENT_VERSION;
        } else if (data.version > SaveData.CURRENT_VERSION) {
            Log.info("SaveManager", "save version " + data.version + " is newer than supported "
                    + SaveData.CURRENT_VERSION + "; loading best-effort");
        }
        return data;
    }

    public void deleteSave() {
        FileHandle file = saveFile();
        if (file.exists() && !file.delete()) {
            Log.info("SaveManager", "deleteSave could not remove " + file.path() + " (file.delete() returned false)");
        }
    }
}
