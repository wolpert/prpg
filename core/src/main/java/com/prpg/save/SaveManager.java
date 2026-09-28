package com.prpg.save;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.prpg.content.ContentRoot;
import com.prpg.items.Inventory;
import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.StoryVariables;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.util.Log;
import com.prpg.world.GameClock;
import com.prpg.world.stage.StageDirector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
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

    private static final Pattern COMMA = Pattern.compile(",");

    private final ContentRoot root;
    private final Inventory inventory;
    private final StoryVariables variables;
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
    public SaveManager(ContentRoot root, Inventory inventory, StoryVariables variables, GameClock clock,
                       NarrativeState narrativeState, NarrativeRunner narrativeRunner,
                       StageDirector stageDirector, ActContentRegistry acts) {
        this.root = root;
        this.inventory = inventory;
        this.variables = variables;
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
        // An older schema's fields must not make the file unreadable: migrate() needs its version.
        json.setIgnoreUnknownFields(true);
        json.setElementType(SaveData.class, "variables", SaveData.Variable.class);
        json.setElementType(SaveData.class, "inventory", SaveData.Entry.class);
        json.setElementType(SaveData.Variable.class, "origins", String.class);
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
            Log.debug("SaveManager", "wrote save (map=" + mapId + ", async=false)");
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
        FileHandle file = saveFile();
        var unused = writeExecutor.submit(() -> {
            try {
                file.writeString(json, false);
                Log.debug("SaveManager", "wrote save (map=" + mapId + ", async=true)");
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
        data.lastPlayedMillis = clock.getLastPlayedMillis();

        for (Map.Entry<String, Object> e : variables.all().entrySet()) {
            SaveData.Variable v = toSaved(e.getKey(), e.getValue());
            if (v != null) data.variables.add(v);
        }
        // Sum inventory slots per item id (Inventory.add rebuilds slots on load).
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Inventory.Slot slot : inventory.getSlots()) {
            counts.merge(slot.itemId, slot.count, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            data.inventory.add(new SaveData.Entry(e.getKey(), e.getValue()));
        }

        SaveData.Narrative n = data.narrative;
        n.currentAct = narrativeState.getCurrentActId();
        n.unlockedActs.addAll(narrativeState.getUnlockedActs());
        for (Map.Entry<String, String> e : narrativeRunner.captureActStates().entrySet()) {
            SaveData.ActState as = new SaveData.ActState();
            as.actId = e.getKey();
            as.stateJson = e.getValue();
            n.inkActStates.add(as);
        }


        return createJson().toJson(data);
    }

    /**
     * Reads the save file, applies story variables, inventory and the act spine, and returns the data.
     * Returns null (so callers fall back to a new game) if the file is missing, corrupt, or from an
     * older schema this build no longer reads.
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
        if (data == null) return null;

        Map<String, Object> loaded = new LinkedHashMap<>();
        for (SaveData.Variable v : data.variables) {
            Object value = fromSaved(v);
            if (value != null) loaded.put(v.name, value);
        }
        variables.loadAll(loaded);

        inventory.clear();
        for (SaveData.Entry e : data.inventory) inventory.add(e.key, e.value);

        clock.setLastPlayedMillis(data.lastPlayedMillis);

        // Restore the act spine, then re-seed the Ink stories' own bookkeeping (visit counts).
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
        stageDirector.invalidate();

        return data;
    }

    /**
     * Upgrades a loaded save to the current schema, or returns null to discard it. Add stepwise
     * migrations keyed on {@code data.version} as the schema evolves; a missing field deserializes to
     * its default, which is often already the right migration.
     */
    private SaveData migrate(SaveData data) {
        if (data.version < 2) {
            // Version 1 kept flags, trigger history and staging overrides, none of which exist now
            // that story state lives in Ink variables. There is no faithful translation.
            Log.info("SaveManager", "discarding save version " + data.version
                    + " (pre-Ink-state schema); starting a new game");
            return null;
        }
        if (data.version > SaveData.CURRENT_VERSION) {
            Log.info("SaveManager", "save version " + data.version + " is newer than supported "
                    + SaveData.CURRENT_VERSION + "; loading best-effort");
        }
        return data;
    }

    // --- story variable (de)serialization --------------------------------------------------------

    static SaveData.Variable toSaved(String name, Object value) {
        SaveData.Variable v = new SaveData.Variable();
        v.name = name;
        if (value instanceof Boolean b) {
            v.type = "bool";
            v.value = String.valueOf(b);
        } else if (value instanceof Integer i) {
            v.type = "int";
            v.value = String.valueOf(i);
        } else if (value instanceof Float f) {
            v.type = "float";
            v.value = String.valueOf(f);
        } else if (value instanceof String str) {
            v.type = "string";
            v.value = str;
        } else if (value instanceof StoryVariables.ListValue list) {
            v.type = "list";
            v.value = String.join(",", list.items());
            v.origins.addAll(list.origins());
        } else {
            Log.info("SaveManager", "not saving story variable '" + name + "' of unsupported type "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
            return null;
        }
        return v;
    }

    static Object fromSaved(SaveData.Variable v) {
        if (v == null || v.name == null || v.type == null) return null;
        String raw = v.value == null ? "" : v.value;
        try {
            return switch (v.type) {
                case "bool" -> Boolean.parseBoolean(raw);
                case "int" -> Integer.parseInt(raw);
                case "float" -> Float.parseFloat(raw);
                case "string" -> raw;
                case "list" -> new StoryVariables.ListValue(
                        v.origins != null ? v.origins : List.of(),
                        raw.isEmpty() ? List.of() : new ArrayList<>(Arrays.asList(COMMA.split(raw))));
                default -> {
                    Log.info("SaveManager", "unknown saved variable type '" + v.type + "' for '" + v.name + "'; dropped");
                    yield null;
                }
            };
        } catch (NumberFormatException e) {
            Log.info("SaveManager", "unreadable saved value '" + raw + "' for '" + v.name + "'; dropped");
            return null;
        }
    }

    public void deleteSave() {
        FileHandle file = saveFile();
        if (file.exists() && !file.delete()) {
            Log.info("SaveManager", "deleteSave could not remove " + file.path() + " (file.delete() returned false)");
        }
    }
}
