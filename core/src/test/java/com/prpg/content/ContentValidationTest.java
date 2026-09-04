package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

/**
 * Walks the real content packs under {@code packs/} and asserts every cross-reference resolves:
 * NPC/activity dialogue ids (against Ink knot names), item ids, activity ids, portal target maps and
 * spawns, every referenced flag, and each act's declared entry map/spawn. Content is gathered across
 * <b>all</b> packs (the pack union), so this stays correct regardless of which pack owns which file.
 * Pure JVM (SnakeYAML + DOM + a line scan of the Ink sources): no libGDX.
 */
class ContentValidationTest {

    private static File packsRoot;
    private static Set<String> itemIds;
    private static Set<String> dialogueKnots;
    /** activity type (directory name under activities/) -> activity ids across all packs. */
    private static Map<String, Set<String>> activityIds;
    private static List<File> activityFiles;
    private static Set<String> declaredFlags;
    /** map id -> the TMX file that defines it, across all packs. */
    private static Map<String, File> mapFiles;
    private static List<File> questFiles;

    private final List<String> errors = new ArrayList<>();

    @BeforeAll
    static void locatePacks() {
        packsRoot = PackTestSupport.packsRoot();
        itemIds = collectItemIds();
        dialogueKnots = loadInkKnots();
        activityIds = collectActivityIds();
        activityFiles = new ArrayList<>();
        for (File pack : packDirs()) {
            File[] types = new File(pack, "activities").listFiles(File::isDirectory);
            if (types == null) continue;
            for (File type : types) activityFiles.addAll(listFiles(type, ".yaml"));
        }
        questFiles = filesAcrossPacks("quests", ".yaml");
        mapFiles = collectMapFiles();
        declaredFlags = collectDeclaredFlags();
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

    @Test
    void allFlagReferencesAreDeclared() {
        for (File f : questFiles) {
            Map<String, Object> quest = loadYaml(f);
            if (quest.get("steps") instanceof List<?> list) {
                for (Object s : list) {
                    if (s instanceof Map<?, ?> step) checkFlag(f.getName() + " step", str(step.get("flag")));
                }
            }
            checkFlag(f.getName() + " complete_flag", str(quest.get("complete_flag")));
            if (quest.get("requires") instanceof List<?> reqs) {
                for (Object r : reqs) checkFlag(f.getName() + " requires", str(r));
            }
        }
        for (File f : activityFiles) {
            if (loadYaml(f).get("on_complete") instanceof Map<?, ?> oc) {
                checkFlag(f.getName() + " on_complete", str(oc.get("set_flag")));
            }
        }
        for (File f : mapFiles.values()) {
            Document doc = loadXml(f);
            NodeList objects = doc.getElementsByTagName("object");
            for (int i = 0; i < objects.getLength(); i++) {
                Map<String, String> p = props((Element) objects.item(i));
                String where = f.getName() + " object '" + ((Element) objects.item(i)).getAttribute("name") + "'";
                checkFlag(where + " set_flag", p.get("set_flag"));
                checkFlag(where + " require_flag", p.get("require_flag"));
                checkFlag(where + " clearedFlag", p.get("clearedFlag"));
            }
        }
        // Ink set_flag("...") / has_flag("...") literals.
        Pattern call = Pattern.compile("\\b(set_flag|has_flag|flag_value|set_flag_value|clear_flag)\\s*\\(\\s*\"([^\"]+)\"");
        for (File pack : packDirs()) {
            for (File ink : inkFiles(new File(pack, "ink"))) {
                if ("bridge.ink".equals(ink.getName())) continue;
                Matcher m = call.matcher(read(ink));
                while (m.find()) checkFlag(ink.getName() + " " + m.group(1), m.group(2));
            }
        }
        assertNoErrors();
    }

    @Test
    void activityOnCompleteRefsResolve() {
        for (File f : activityFiles) {
            Map<String, Object> def = loadYaml(f);
            if (!(def.get("on_complete") instanceof Map<?, ?> oc)) continue;
            String where = f.getName() + " on_complete";
            String give = str(oc.get("give_item"));
            if (give != null && !itemIds.contains(give)) {
                errors.add(where + ": unknown item '" + give + "'");
            }
            if (give != null && oc.get("set_flag") == null) {
                errors.add(where + ": give_item without set_flag would grant the reward on every solve");
            }
            String dlg = str(oc.get("dialogue"));
            if (dlg != null && !dialogueKnots.contains(dlg)) {
                errors.add(where + ": unknown dialogue knot '" + dlg + "'");
            }
            if ("merge".equals(f.getParentFile().getName())) checkMergeLadder(f, def);
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

    @Test
    void mapObjectReferencesResolve() {
        for (File f : mapFiles.values()) {
            Document doc = loadXml(f);
            NodeList groups = doc.getElementsByTagName("objectgroup");
            for (int i = 0; i < groups.getLength(); i++) {
                Element group = (Element) groups.item(i);
                String layer = group.getAttribute("name");
                NodeList objects = group.getElementsByTagName("object");
                for (int j = 0; j < objects.getLength(); j++) {
                    Element obj = (Element) objects.item(j);
                    String where = f.getName() + " [" + layer + "] object '" + obj.getAttribute("name") + "'";
                    Map<String, String> props = props(obj);

                    if ("npcs".equals(layer)) {
                        checkRef(where + " dialogueId", props.get("dialogueId"), dialogueKnots, "dialogue knot");
                    } else if ("items".equals(layer)) {
                        checkRef(where + " itemId", props.get("itemId"), itemIds, "item");
                    } else if ("activities".equals(layer)) {
                        String type = props.get("type");
                        Set<String> ids = activityIds.get(type);
                        if (ids == null) {
                            errors.add(where + ": unknown activity type '" + type + "' (no activities/" + type + "/ in any pack)");
                        } else {
                            checkRef(where + " activityId", props.get("activityId"), ids, type + " activity");
                        }
                    } else if ("portals".equals(layer)) {
                        String target = props.get("target_map");
                        checkRef(where + " target_map", target, mapFiles.keySet(), "map");
                        String spawn = props.get("target_spawn");
                        if (target != null && mapFiles.containsKey(target) && spawn != null
                                && !spawnExists(target, spawn)) {
                            errors.add(where + " target_spawn '" + spawn + "' not found in " + target);
                        }
                    }
                }
            }
        }
        assertNoErrors();
    }

    @Test
    void everyProvidedActHasAnEntryMapInItsOwnPack() {
        for (File pack : packDirs()) {
            Map<String, Object> manifest = loadYaml(new File(pack, "pack.yaml"));
            if (!(manifest.get("provides") instanceof List<?> provides)) continue;
            for (Object o : provides) {
                if (!(o instanceof Map<?, ?> p)) continue;
                String id = str(p.get("id"));
                String where = pack.getName() + "/pack.yaml provides '" + id + "'";
                String entryMap = str(p.get("entryMap"));
                if (entryMap == null) {
                    errors.add(where + ": entryMap is required (an act owns its own maps)");
                    continue;
                }
                File map = new File(pack, "maps/" + entryMap + ".tmx");
                if (!map.isFile()) {
                    errors.add(where + ": entryMap '" + entryMap + "' is not a TMX in this pack's maps/");
                    continue;
                }
                String spawn = str(p.get("entrySpawn"));
                if (spawn != null && !spawnExists(entryMap, spawn)) {
                    errors.add(where + ": entrySpawn '" + spawn + "' not found in " + entryMap);
                }
            }
        }
        assertNoErrors();
    }

    // --- helpers -----------------------------------------------------------------------------

    private static Set<String> loadInkKnots() {
        Set<String> knots = new HashSet<>();
        Pattern knot = Pattern.compile("^={2,}\\s*([A-Za-z0-9_]+)");
        for (File pack : packDirs()) {
            for (File f : inkFiles(new File(pack, "ink"))) {
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        Matcher m = knot.matcher(line.trim());
                        if (m.find()) knots.add(m.group(1));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("failed to read " + f, e);
                }
            }
        }
        return knots;
    }

    private static List<File> inkFiles(File dir) {
        List<File> out = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries == null) return out;
        for (File f : entries) {
            if (f.isDirectory()) {
                out.addAll(inkFiles(f));
            } else if (f.getName().endsWith(".ink")) {
                out.add(f);
            }
        }
        return out;
    }

    private void checkFlag(String where, String flag) {
        if (flag != null && !declaredFlags.contains(flag)) {
            errors.add(where + ": undeclared flag '" + flag + "' (add it to a pack's flags.yaml)");
        }
    }

    private static Set<String> collectDeclaredFlags() {
        Set<String> flags = new HashSet<>();
        for (File pack : packDirs()) {
            File f = new File(pack, "flags.yaml");
            if (!f.exists()) continue;
            if (loadYaml(f).get("flags") instanceof List<?> list) {
                for (Object o : list) flags.add(String.valueOf(o));
            }
        }
        return flags;
    }

    private void checkRef(String where, String value, Set<String> valid, String kind) {
        if (value != null && !valid.contains(value)) {
            errors.add(where + ": unknown " + kind + " '" + value + "'");
        }
    }

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

    private static Set<String> collectItemIds() {
        Set<String> ids = new HashSet<>();
        for (File pack : packDirs()) {
            File f = new File(pack, "items/items.yaml");
            if (!f.exists()) continue;
            if (loadYaml(f).get("items") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) ids.add(String.valueOf(m.get("id")));
                }
            }
        }
        return ids;
    }

    /** activity type -> ids, from every pack's {@code activities/<type>/<id>.yaml}. */
    private static Map<String, Set<String>> collectActivityIds() {
        Map<String, Set<String>> out = new HashMap<>();
        for (File pack : packDirs()) {
            File[] types = new File(pack, "activities").listFiles(File::isDirectory);
            if (types == null) continue;
            for (File type : types) {
                Set<String> ids = out.computeIfAbsent(type.getName(), k -> new HashSet<>());
                for (File f : listFiles(type, ".yaml")) {
                    ids.add(f.getName().substring(0, f.getName().length() - ".yaml".length()));
                }
            }
        }
        return out;
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

    private static String read(File f) {
        try {
            return java.nio.file.Files.readString(f.toPath());
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + f, e);
        }
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
