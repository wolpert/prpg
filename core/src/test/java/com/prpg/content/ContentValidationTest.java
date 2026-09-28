package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

/**
 * Walks the real content packs under {@code packs/} and checks the data that stays outside Ink:
 * activity tuning files are well formed (and carry no story), portals lead to real maps and spawns,
 * and maps hold only geography (story content is staged by Ink, so a story object left in a map
 * would be silently ignored). What stories refer to is checked by {@code InkWorldValidationTest}.
 * Content is gathered across <b>all</b> packs (the pack union). Pure JVM (SnakeYAML + DOM): no libGDX.
 */
class ContentValidationTest {

    /** The object layers a map may hold; everything else the story stages. */
    private static final Set<String> GEOGRAPHY_LAYERS = Set.of("spawn", "portals", "markers");

    private static List<File> activityFiles;
    /** map id -> the TMX file that defines it, across all packs. */
    private static Map<String, File> mapFiles;

    private final List<String> errors = new ArrayList<>();

    @BeforeAll
    static void locatePacks() {
        activityFiles = new ArrayList<>();
        for (File pack : packDirs()) {
            File[] types = new File(pack, "activities").listFiles(File::isDirectory);
            if (types == null) continue;
            for (File type : types) activityFiles.addAll(listFiles(type, ".yaml"));
        }
        mapFiles = collectMapFiles();
    }

    private static File[] packDirs() {
        return PackTestSupport.packDirs();
    }

    private static List<File> filesAcrossPacks(String relDir, String suffix) {
        List<File> out = new ArrayList<>();
        for (File pack : packDirs()) {
            out.addAll(listFiles(new File(pack, relDir), suffix));
        }
        return out;
    }

    // --- activities: tuning data only ------------------------------------------------------------

    @Test
    void activityFilesCarryTuningOnly() {
        // What winning means is story, decided by the knot that plays the activity. A leftover
        // on_complete block would also make the definition fail to load (unknown field).
        for (File f : activityFiles) {
            if (loadYaml(f).containsKey("on_complete")) {
                String type = f.getParentFile().getName();
                errors.add(type + "/" + f.getName() + ": on_complete is retired; write the outcome in the"
                        + " Ink knot after '>>> play " + type + " " + f.getName().replace(".yaml", "")
                        + "' (branch on activity_won)");
            }
        }
        assertNoErrors();
    }

    @Test
    void mergeLaddersAreConsistent() {
        for (File f : activityFiles) {
            if ("merge".equals(f.getParentFile().getName())) checkMergeLadder(f, loadYaml(f));
        }
        assertNoErrors();
    }

    @Test
    void match3BoardAndWinValid() {
        for (File f : activityFiles) {
            if (!"match3".equals(f.getParentFile().getName())) continue;
            Map<String, Object> def = loadYaml(f);
            String where = f.getName();
            if (!(def.get("board") instanceof Map<?, ?> board)) {
                errors.add(where + ": missing board");
                continue;
            }
            int width = intOr(board.get("width"), 0);
            int height = intOr(board.get("height"), 0);
            if (width <= 0 || height <= 0) {
                errors.add(where + ": board width/height must be > 0 (got " + width + "x" + height + ")");
            }
            List<String> names = pieceNames(board.get("pieces"));
            if (names.size() < 3) {
                errors.add(where + ": match-3 needs at least 3 piece types (got " + names.size() + ")");
            }
            Set<String> unique = new HashSet<>(names);
            if (unique.size() != names.size()) {
                errors.add(where + ": piece names must be unique " + names);
            }
            if (board.get("pieces") instanceof List<?> pieces) {
                for (Object p : pieces) {
                    if (p instanceof Map<?, ?> m && m.get("atlas") != null
                            && !PackTestSupport.contentExists(str(m.get("atlas")))) {
                        errors.add(where + ": piece '" + str(m.get("name"))
                                + "' references missing atlas '" + str(m.get("atlas")) + "'");
                    }
                }
            }
            if (!(def.get("win") instanceof Map<?, ?> win)
                    || !(win.get("per_type") instanceof Map<?, ?> perType) || perType.isEmpty()) {
                errors.add(where + ": win.per_type must be a non-empty map of piece -> count");
            } else {
                for (Map.Entry<?, ?> e : perType.entrySet()) {
                    String name = String.valueOf(e.getKey());
                    if (!unique.contains(name)) {
                        errors.add(where + " win.per_type: '" + name + "' is not a declared piece");
                    }
                    if (!(e.getValue() instanceof Integer count) || count <= 0) {
                        errors.add(where + " win.per_type '" + name + "': count must be a positive integer");
                    }
                }
            }
        }
        assertNoErrors();
    }

    @Test
    void lightsOutBoardsHavePositiveDimensions() {
        for (File f : activityFiles) {
            if (!"lightsout".equals(f.getParentFile().getName())) continue;
            Map<String, Object> def = loadYaml(f);
            if (def.get("board") instanceof Map<?, ?> board) {
                if (intOr(board.get("width"), 3) <= 0 || intOr(board.get("height"), 3) <= 0) {
                    errors.add(f.getName() + ": board width/height must be > 0");
                }
            }
        }
        assertNoErrors();
    }

    // --- maps: geography only ---------------------------------------------------------------------

    @Test
    void portalsLeadToRealMapsAndSpawns() {
        for (File f : mapFiles.values()) {
            Document doc = loadXml(f);
            NodeList groups = doc.getElementsByTagName("objectgroup");
            for (int i = 0; i < groups.getLength(); i++) {
                Element group = (Element) groups.item(i);
                if (!"portals".equals(group.getAttribute("name"))) continue;
                NodeList objects = group.getElementsByTagName("object");
                for (int j = 0; j < objects.getLength(); j++) {
                    Element obj = (Element) objects.item(j);
                    String where = f.getName() + " portal '" + obj.getAttribute("name") + "'";
                    if (obj.getAttribute("name").isEmpty()) {
                        errors.add(where + ": a portal needs a name (the story's lock() refers to it)");
                    }
                    Map<String, String> props = props(obj);
                    String target = props.get("target_map");
                    if (target == null || !mapFiles.containsKey(target)) {
                        errors.add(where + ": target_map '" + target + "' is not a map in any pack");
                    } else {
                        String spawn = props.get("target_spawn");
                        if (spawn != null && !spawnExists(target, spawn)) {
                            errors.add(where + ": target_spawn '" + spawn + "' not found in " + target);
                        }
                    }
                    for (String key : props.keySet()) {
                        if (!Set.of("target_map", "target_spawn").contains(key)) {
                            errors.add(where + ": unknown property '" + key
                                    + "' (to gate a portal, lock() it in the act's stage())");
                        }
                    }
                }
            }
        }
        assertNoErrors();
    }

    @Test
    void mapsHoldOnlyGeography() {
        for (File f : mapFiles.values()) {
            Document doc = loadXml(f);
            NodeList groups = doc.getElementsByTagName("objectgroup");
            for (int i = 0; i < groups.getLength(); i++) {
                Element group = (Element) groups.item(i);
                String layer = group.getAttribute("name");
                if (GEOGRAPHY_LAYERS.contains(layer)) continue;
                if (group.getElementsByTagName("object").getLength() > 0) {
                    errors.add(f.getName() + ": object layer '" + layer + "' is ignored by the engine; a map"
                            + " holds only " + GEOGRAPHY_LAYERS + ". Add a marker and stage the object from"
                            + " the act's Ink stage() instead");
                }
            }
        }
        assertNoErrors();
    }

    // --- helpers -----------------------------------------------------------------------------

    private void checkMergeLadder(File f, Map<String, Object> def) {
        if (!(def.get("ladder") instanceof List<?> ladder)) return;
        Set<String> ids = new HashSet<>();
        for (Object e : ladder) {
            if (e instanceof Map<?, ?> m) ids.add(str(m.get("id")));
        }
        for (Object e : ladder) {
            if (!(e instanceof Map<?, ?> m)) continue;
            if (m.get("from") instanceof List<?> pair) {
                for (Object p : pair) {
                    if (!ids.contains(String.valueOf(p))) {
                        errors.add(f.getName() + " ladder '" + str(m.get("id"))
                                + "': from references unknown id '" + p + "'");
                    }
                }
            }
        }
        if (def.get("win") instanceof Map<?, ?> w) {
            String produce = str(w.get("produce"));
            if (produce != null && !ids.contains(produce)) {
                errors.add(f.getName() + " win.produce '" + produce + "' is not a ladder id");
            }
        }
    }

    private boolean spawnExists(String mapId, String spawnName) {
        File mapFile = mapFiles.get(mapId);
        if (mapFile == null) return false;
        Document doc = loadXml(mapFile);
        NodeList groups = doc.getElementsByTagName("objectgroup");
        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);
            if (!"spawn".equals(group.getAttribute("name"))) continue;
            NodeList objects = group.getElementsByTagName("object");
            for (int j = 0; j < objects.getLength(); j++) {
                if (spawnName.equals(((Element) objects.item(j)).getAttribute("name"))) return true;
            }
        }
        return false;
    }

    private static Map<String, String> props(Element object) {
        Map<String, String> map = new HashMap<>();
        NodeList propsNodes = object.getElementsByTagName("property");
        for (int i = 0; i < propsNodes.getLength(); i++) {
            Element p = (Element) propsNodes.item(i);
            map.put(p.getAttribute("name"), p.getAttribute("value"));
        }
        return map;
    }

    private static Map<String, File> collectMapFiles() {
        Map<String, File> out = new HashMap<>();
        for (File f : filesAcrossPacks("maps", ".tmx")) {
            out.put(f.getName().substring(0, f.getName().length() - ".tmx".length()), f);
        }
        return out;
    }

    private void assertNoErrors() {
        assertTrue(errors.isEmpty(), "content reference errors:\n  " + String.join("\n  ", errors));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOr(Object o, int fallback) {
        return o instanceof Integer i ? i : fallback;
    }

    private static List<String> pieceNames(Object pieces) {
        List<String> out = new ArrayList<>();
        if (pieces instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof Map<?, ?> m) {
                    if (m.get("name") != null) out.add(String.valueOf(m.get("name")));
                } else if (p != null) {
                    out.add(String.valueOf(p));
                }
            }
        }
        return out;
    }

    private static List<File> listFiles(File dir, String suffix) {
        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(suffix));
        if (files != null) {
            for (File f : files) out.add(f);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(File f) {
        try (Reader r = new FileReader(f)) {
            Object loaded = new Yaml().load(r);
            return loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + f, e);
        }
    }

    private static Document loadXml(File f) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
        } catch (Exception e) {
            throw new RuntimeException("failed to parse " + f, e);
        }
    }
}
