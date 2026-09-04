package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * Guard rail for the act-staging layer: every {@code packs/<act>/staging/<act>.yaml} must reference
 * only things that exist. Staging is where act content lives now, so a dangling reference here is a
 * silent story break in exactly the way a dangling {@code dialogueId} used to be.
 *
 * <p>Dialogue knots are checked <b>per act</b>, not against the union: a staged line runs against the
 * act's own Ink story ({@code NarrativeRunner.start(currentActId, knot)}), so naming another act's
 * knot resolves to nothing at runtime. Pure JVM (SnakeYAML + DOM + a line scan of the Ink sources).
 */
class StagingValidationTest {

    private static File packsRoot;
    /** act id -> its staging file. */
    private static Map<String, File> stagingFiles;
    private static Set<String> actorIds;
    private static Set<String> itemIds;
    /** activity type (directory under activities/) -> ids across all packs. */
    private static Map<String, Set<String>> activityIds;
    private static Set<String> declaredFlags;
    /** map id -> the marker names its {@code markers} layer declares. */
    private static Map<String, Set<String>> mapMarkers;
    /** Object names in every map's {@code npcs} (prop) layer — what a props: entry may override. */
    private static Set<String> propNames;
    /** pack id -> the Ink knot names that pack's {@code ink/} declares. */
    private static Map<String, Set<String>> knotsByPack;

    private final List<String> errors = new ArrayList<>();

    @BeforeAll
    static void gather() {
        packsRoot = PackTestSupport.packsRoot();

        stagingFiles = collectStagingFiles();
        actorIds = collectIds("actors", "id");
        itemIds = collectItemIds();
        activityIds = collectActivityIds();
        declaredFlags = collectDeclaredFlags();
        mapMarkers = new HashMap<>();
        propNames = new HashSet<>();
        for (File tmx : filesAcrossPacks("maps", ".tmx")) {
            String mapId = base(tmx, ".tmx");
            mapMarkers.put(mapId, objectNames(tmx, "markers"));
            propNames.addAll(objectNames(tmx, "npcs"));
        }
        knotsByPack = collectKnotsByPack();
    }

    // --- the checks --------------------------------------------------------------------------

    @Test
    void stagingFileNamesMatchTheirAct() {
        for (Map.Entry<String, File> e : stagingFiles.entrySet()) {
            File f = e.getValue();
            String packId = f.getParentFile().getParentFile().getName();
            String declared = str(loadYaml(f).get("act"));
            if (!e.getKey().equals(packId)) {
                errors.add(f.getPath() + ": staging file must be named <packId>.yaml (pack is '" + packId + "')");
            }
            if (declared != null && !declared.equals(packId)) {
                errors.add(f.getPath() + ": act '" + declared + "' does not match its pack '" + packId + "'");
            }
        }
        assertNoErrors();
    }

    @Test
    void castReferencesResolve() {
        forEachEntry("cast", (act, file, entry) -> {
            String where = file.getName() + " cast '" + str(entry.get("actor")) + "'";
            checkRef(where + " actor", str(entry.get("actor")), actorIds, "actor definition (actors/<id>.yaml)");
            checkPlacement(where, entry);
            checkKnot(where + " dialogue", str(entry.get("dialogue")), act);
        });
        assertNoErrors();
    }

    @Test
    void obstacleReferencesResolve() {
        forEachEntry("obstacles", (act, file, entry) -> {
            String id = str(entry.get("id"));
            String where = file.getName() + " obstacle '" + id + "'";
            if (id == null) errors.add(where + ": an obstacle needs an id (overrides key on it)");
            checkPlacement(where, entry);
            checkKnot(where + " dialogue", str(entry.get("dialogue")), act);
            checkFlag(where + " cleared_when", str(entry.get("cleared_when")));
            if (entry.get("activity") instanceof Map<?, ?> activity) {
                String type = str(activity.get("type"));
                String pid = str(activity.get("id"));
                Set<String> ids = activityIds.get(type);
                if (ids == null) {
                    errors.add(where + ": unknown activity.type '" + type
                            + "' (no activities/" + type + "/ in any pack; is it bound in WorldModule?)");
                } else {
                    checkRef(where + " activity.id", pid, ids, type + " activity");
                }
            }
        });
        assertNoErrors();
    }

    @Test
    void itemAndTriggerReferencesResolve() {
        forEachEntry("items", (act, file, entry) -> {
            String where = file.getName() + " item '" + str(entry.get("itemId")) + "'";
            checkRef(where, str(entry.get("itemId")), itemIds, "item");
            checkPlacement(where, entry);
        });
        forEachEntry("triggers", (act, file, entry) -> {
            String where = file.getName() + " trigger '" + str(entry.get("id")) + "'";
            checkPlacement(where, entry);
            checkFlag(where + " set_flag", str(entry.get("set_flag")));
            checkFlag(where + " require_flag", str(entry.get("require_flag")));
        });
        assertNoErrors();
    }

    @Test
    void propOverridesNameRealProps() {
        forEachEntry("props", (act, file, entry) -> {
            String name = str(entry.get("prop"));
            String where = file.getName() + " prop '" + name + "'";
            checkRef(where, name, propNames, "prop object in some map's npcs layer");
            checkKnot(where + " dialogue", str(entry.get("dialogue")), act);
        });
        assertNoErrors();
    }

    @Test
    void everyConditionFlagIsDeclared() {
        for (String section : new String[]{"cast", "obstacles", "items", "triggers", "props"}) {
            forEachEntry(section, (act, file, entry) -> {
                String where = file.getName() + " [" + section + "]";
                for (String key : new String[]{"when", "unless", "solid_when", "solid_unless"}) {
                    if (entry.get(key) instanceof List<?> list) {
                        for (Object flag : list) checkFlag(where + " " + key, str(flag));
                    }
                }
            });
        }
        assertNoErrors();
    }

    @Test
    void inkStagingCallsNameRealActorsAndMarkers() {
        // place_actor("rowan", "boundary_path", "stone_row") — the literal form authors write.
        Pattern call = Pattern.compile(
                "\\b(place_actor|remove_actor|set_actor_dialogue|set_solid|staged_on)\\s*\\(([^)]*)\\)");
        for (File pack : packDirs()) {
            for (File ink : inkFiles(new File(pack, "ink"))) {
                // bridge.ink declares and stubs these; it references no real ids.
                if ("bridge.ink".equals(ink.getName())) continue;
                String text = read(ink);
                Matcher m = call.matcher(text);
                while (m.find()) {
                    List<String> args = literalArgs(m.group(2));
                    if (args.isEmpty()) continue; // variable args — not statically checkable
                    String where = ink.getName() + " " + m.group(1) + "(" + m.group(2).trim() + ")";
                    String id = args.get(0);
                    // The id may be an actor, an obstacle, or a prop; all three are valid targets.
                    if (id != null && !actorIds.contains(id) && !stagedIds().contains(id)
                            && !propNames.contains(id)) {
                        errors.add(where + ": '" + id + "' is not a known actor, obstacle or prop");
                    }
                    if ("place_actor".equals(m.group(1)) && args.size() >= 3) {
                        String map = args.get(1);
                        String marker = args.get(2);
                        if (map != null && !mapMarkers.containsKey(map)) {
                            errors.add(where + ": no such map '" + map + "'");
                        } else if (map != null && marker != null
                                && !mapMarkers.get(map).contains(marker)) {
                            errors.add(where + ": map '" + map + "' has no marker '" + marker + "'");
                        }
                    }
                }
            }
        }
        assertNoErrors();
    }

    // --- shared checks -----------------------------------------------------------------------

    /** A placement must name a real map and a marker that map actually declares. */
    private void checkPlacement(String where, Map<String, Object> entry) {
        if (!(entry.get("at") instanceof Map<?, ?> at)) {
            errors.add(where + ": missing 'at: { map, marker }'");
            return;
        }
        String map = str(at.get("map"));
        String marker = str(at.get("marker"));
        if (map == null || !mapMarkers.containsKey(map)) {
            errors.add(where + ": at.map '" + map + "' is not a map in any pack");
            return;
        }
        if (marker == null) {
            errors.add(where + ": at.marker is required (placements name a marker, never coordinates)");
        } else if (!mapMarkers.get(map).contains(marker)) {
            errors.add(where + ": map '" + map + "' has no marker '" + marker
                    + "' (add it to that map's markers object layer)");
        }
    }

    /**
     * A staged knot runs against its own act's story, so it must exist in <em>that</em> pack's Ink.
     */
    private void checkKnot(String where, String knot, String actId) {
        if (knot == null) return;
        Set<String> knots = knotsByPack.getOrDefault(actId, Set.of());
        if (!knots.contains(knot)) {
            errors.add(where + ": '" + knot + "' is not a knot in " + actId
                    + "'s Ink (a staged line runs against its own act's story)");
        }
    }

    private void checkRef(String where, String value, Set<String> valid, String kind) {
        if (value == null) {
            errors.add(where + ": required");
        } else if (!valid.contains(value)) {
            errors.add(where + ": unknown " + kind + " '" + value + "'");
        }
    }

    private void checkFlag(String where, String flag) {
        if (flag != null && !declaredFlags.contains(flag)) {
            errors.add(where + ": undeclared flag '" + flag + "' (add it to a pack's flags.yaml)");
        }
    }

    private void assertNoErrors() {
        assertTrue(errors.isEmpty(), "staging reference errors:\n  " + String.join("\n  ", errors));
    }

    // --- traversal ---------------------------------------------------------------------------

    private interface EntryVisitor {
        void visit(String actId, File file, Map<String, Object> entry);
    }

    @SuppressWarnings("unchecked")
    private void forEachEntry(String section, EntryVisitor visitor) {
        for (Map.Entry<String, File> e : stagingFiles.entrySet()) {
            Object list = loadYaml(e.getValue()).get(section);
            if (!(list instanceof List<?> entries)) continue;
            for (Object o : entries) {
                if (o instanceof Map<?, ?> entry) {
                    visitor.visit(e.getKey(), e.getValue(), (Map<String, Object>) entry);
                }
            }
        }
    }

    /** Every obstacle/trigger id declared by any staging file — valid targets for an Ink override. */
    private Set<String> stagedIds() {
        Set<String> ids = new HashSet<>();
        for (String section : new String[]{"obstacles", "triggers"}) {
            forEachEntry(section, (act, file, entry) -> {
                if (entry.get("id") != null) ids.add(str(entry.get("id")));
            });
        }
        return ids;
    }

    // --- gathering ---------------------------------------------------------------------------

    private static File[] packDirs() {
        return PackTestSupport.packDirs();
    }

    /** activity type -> ids, from every pack's {@code activities/<type>/<id>.yaml}. */
    private static Map<String, Set<String>> collectActivityIds() {
        Map<String, Set<String>> out = new HashMap<>();
        for (File pack : packDirs()) {
            File[] types = new File(pack, "activities").listFiles(File::isDirectory);
            if (types == null) continue;
            for (File type : types) {
                Set<String> ids = out.computeIfAbsent(type.getName(), k -> new HashSet<>());
                File[] files = type.listFiles((d, n) -> n.endsWith(".yaml"));
                if (files != null) {
                    for (File f : files) ids.add(base(f, ".yaml"));
                }
            }
        }
        return out;
    }

    private static List<File> filesAcrossPacks(String relDir, String suffix) {
        List<File> out = new ArrayList<>();
        for (File pack : packDirs()) {
            File[] files = new File(pack, relDir).listFiles((d, n) -> n.endsWith(suffix));
            if (files != null) {
                for (File f : files) out.add(f);
            }
        }
        return out;
    }

    private static Map<String, File> collectStagingFiles() {
        Map<String, File> out = new LinkedHashMap<>();
        for (File f : filesAcrossPacks("staging", ".yaml")) {
            out.put(base(f, ".yaml"), f);
        }
        return out;
    }

    /** The {@code key} field of every {@code <relDir>/*.yaml} across all packs. */
    private static Set<String> collectIds(String relDir, String key) {
        Set<String> ids = new HashSet<>();
        for (File f : filesAcrossPacks(relDir, ".yaml")) {
            String id = str(loadYaml(f).get(key));
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static Set<String> collectItemIds() {
        Set<String> ids = new HashSet<>();
        for (File pack : packDirs()) {
            File f = new File(pack, "items/items.yaml");
            if (!f.exists()) continue;
            if (loadYaml(f).get("items") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) ids.add(str(m.get("id")));
                }
            }
        }
        return ids;
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

    /** Knots per pack — {@code === name ===}; stitches ({@code = name}) are sub-targets, not matched. */
    private static Map<String, Set<String>> collectKnotsByPack() {
        Pattern knot = Pattern.compile("^={2,}\\s*([A-Za-z0-9_]+)");
        Map<String, Set<String>> out = new HashMap<>();
        for (File pack : packDirs()) {
            Set<String> knots = new HashSet<>();
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
            out.put(pack.getName(), knots);
        }
        return out;
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

    /** Object names in one object layer of a TMX. */
    private static Set<String> objectNames(File tmx, String layer) {
        Set<String> names = new HashSet<>();
        Document doc = loadXml(tmx);
        NodeList groups = doc.getElementsByTagName("objectgroup");
        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);
            if (!layer.equals(group.getAttribute("name"))) continue;
            NodeList objects = group.getElementsByTagName("object");
            for (int j = 0; j < objects.getLength(); j++) {
                String name = ((Element) objects.item(j)).getAttribute("name");
                if (!name.isEmpty()) names.add(name);
            }
        }
        return names;
    }

    /** Quoted string literals from an Ink call's arg list; empty if any arg is a variable. */
    private static List<String> literalArgs(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
                out.add(t.substring(1, t.length() - 1));
            } else {
                return List.of();
            }
        }
        return out;
    }

    private static String read(File f) {
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + f, e);
        }
    }

    private static String base(File f, String suffix) {
        return f.getName().substring(0, f.getName().length() - suffix.length());
    }

    private static Set<String> baseNames(List<File> files) {
        Set<String> out = new HashSet<>();
        for (File f : files) out.add(base(f, ".yaml"));
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
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
