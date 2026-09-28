package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.bladecoder.ink.runtime.Story;
import com.prpg.items.Inventory;
import com.prpg.narrative.InkTestSupport;
import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.StoryVariables;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.world.command.Commands;
import com.prpg.world.stage.StageDirector;
import com.prpg.world.stage.StagedPlacement;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Guard rail for everything a story <em>refers to</em> outside itself. The Ink compiler already
 * catches a misspelled variable or knot ({@code -> knot} arguments are checked like any divert);
 * this test catches the names it can't see: maps, markers, spawns, portals, activities, items and
 * sprites, written as string literals in {@code stage()}/{@code cast()} calls, inventory calls and
 * {@code >>>} command lines. It also runs each act's {@code stage()} for real (at the story's
 * starting state) so computed arguments are covered too, and checks that every story variable is
 * declared in exactly one file (sharing between acts is by name, so a duplicate is a silent link).
 * Pure JVM: SnakeYAML, DOM, a line scan of the Ink sources, and the blade-ink compiler.
 */
class InkWorldValidationTest {

    /** map id -> the TMX file that defines it, across all packs. */
    private static Map<String, File> mapFiles;
    /** map id -> object names per layer ({@code spawn}, {@code portals}, {@code markers}). */
    private static Map<String, Map<String, Set<String>>> mapObjects;
    private static Set<String> itemIds;
    /** activity type (directory under activities/) -> ids across all packs. */
    private static Map<String, Set<String>> activityIds;
    /** Every Ink file under a pack's ink/, keyed by the pack that owns it. */
    private static Map<String, List<File>> inkFilesByPack;

    private final List<String> errors = new ArrayList<>();

    @BeforeAll
    static void gather() {
        mapFiles = new HashMap<>();
        mapObjects = new HashMap<>();
        itemIds = new HashSet<>();
        activityIds = new HashMap<>();
        inkFilesByPack = new LinkedHashMap<>();
        for (File pack : PackTestSupport.packDirs()) {
            for (File tmx : listFiles(new File(pack, "maps"), ".tmx")) {
                String id = base(tmx, ".tmx");
                mapFiles.put(id, tmx);
                mapObjects.put(id, objectNamesByLayer(tmx));
            }
            File items = new File(pack, "items/items.yaml");
            if (items.isFile() && loadYaml(items).get("items") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) itemIds.add(String.valueOf(m.get("id")));
                }
            }
            File[] types = new File(pack, "activities").listFiles(File::isDirectory);
            if (types != null) {
                for (File type : types) {
                    Set<String> ids = activityIds.computeIfAbsent(type.getName(), k -> new HashSet<>());
                    for (File f : listFiles(type, ".yaml")) ids.add(base(f, ".yaml"));
                }
            }
            inkFilesByPack.put(pack.getName(), inkFiles(new File(pack, "ink")));
        }
    }

    // --- where each act begins -----------------------------------------------------------------

    @Test
    void everyActSaysWhereItBeginsAndThatPlaceExists() throws Exception {
        for (String act : InkTestSupport.actIdsWithInk()) {
            Story story = InkTestSupport.story(act);
            String[] entry = ActContentRegistry.parseEntryTag(story.getGlobalTags());
            String where = act + ".ink";
            if (entry == null) {
                errors.add(where + ": no '# entry: <map> [<spawn>]' tag. It must be the file's very first"
                        + " line (before the INCLUDEs), or Ink doesn't treat it as a whole-story tag");
                continue;
            }
            File map = new File(PackTestSupport.packsRoot(), act + "/maps/" + entry[0] + ".tmx");
            if (!map.isFile()) {
                errors.add(where + " # entry: map '" + entry[0] + "' is not a TMX in " + act + "/maps/"
                        + " (an act begins on one of its own maps)");
            } else if (entry[1] != null && !objects(entry[0], "spawn").contains(entry[1])) {
                errors.add(where + " # entry: map '" + entry[0] + "' has no spawn '" + entry[1] + "'");
            }
        }
        assertNoErrors();
    }

    // --- literal references in the Ink source ----------------------------------------------------

    private static final Pattern WORLD_CALL = Pattern.compile(
            "\\b(actor|thing|zone|lock|define|give_item|take_item|has_item|item_count)\\s*\\(([^)]*)\\)");

    @Test
    void worldCallsNameRealMapsMarkersPortalsSpritesAndItems() {
        forEachActLine((act, file, lineNo, line) -> {
            Matcher m = WORLD_CALL.matcher(line);
            while (m.find()) {
                String fn = m.group(1);
                List<String> args = splitArgs(m.group(2));
                String where = file.getName() + ":" + lineNo + " " + fn + "(" + m.group(2).trim() + ")";
                switch (fn) {
                    case "actor", "zone" -> checkPlacement(where, act, args, 4);
                    case "thing" -> checkPlacement(where, act, args, 5);
                    case "lock" -> {
                        String portal = literal(args, 0);
                        if (portal != null && !anyMapHas("portals", portal)) {
                            errors.add(where + ": no map has a portal named '" + portal + "'");
                        }
                    }
                    case "define" -> checkLook(where, args);
                    default -> { // inventory calls
                        String item = literal(args, 0);
                        if (item != null && !itemIds.contains(item)) {
                            errors.add(where + ": unknown item '" + item + "' (add it to baseline items/items.yaml)");
                        }
                    }
                }
            }
        });
        assertNoErrors();
    }

    @Test
    void commandLinesAreKnownAndNameRealThings() {
        Map<String, Commands.Spec> specs = new HashMap<>();
        for (Commands.Spec spec : Commands.ALL) specs.put(spec.name(), spec);
        forEachActLine((act, file, lineNo, line) -> {
            String text = line.trim();
            if (!text.startsWith(NarrativeRunner.COMMAND_PREFIX)) return;
            String where = file.getName() + ":" + lineNo + " '" + text + "'";
            List<String> words = NarrativeRunner.parseCommand(text);
            if (words.isEmpty()) {
                errors.add(where + ": empty command");
                return;
            }
            Commands.Spec spec = specs.get(words.get(0));
            List<String> args = words.subList(1, words.size());
            if (spec == null) {
                errors.add(where + ": unknown command '" + words.get(0) + "' (known: " + specs.keySet() + ")");
                return;
            }
            if (args.size() < spec.minArgs() || args.size() > spec.maxArgs()) {
                errors.add(where + ": usage is '>>> " + spec.usage() + "'");
                return;
            }
            switch (spec.name()) {
                case "play" -> {
                    Set<String> ids = activityIds.get(args.get(0));
                    if (ids == null) {
                        errors.add(where + ": no activity type '" + args.get(0) + "' (no activities/"
                                + args.get(0) + "/ in any pack; is it bound in WorldModule?)");
                    } else if (!ids.contains(args.get(1))) {
                        errors.add(where + ": no activities/" + args.get(0) + "/" + args.get(1) + ".yaml");
                    }
                }
                case "go" -> {
                    if (!mapFiles.containsKey(args.get(0))) {
                        errors.add(where + ": no map '" + args.get(0) + "'");
                    } else if (args.size() > 1 && !objects(args.get(0), "spawn").contains(args.get(1))) {
                        errors.add(where + ": map '" + args.get(0) + "' has no spawn '" + args.get(1) + "'");
                    }
                }
                case "wait" -> checkSeconds(where, args.get(0));
                case "fade" -> {
                    if (!"out".equals(args.get(0)) && !"in".equals(args.get(0))) {
                        errors.add(where + ": fade is 'out' or 'in'");
                    }
                    if (args.size() > 1) checkSeconds(where, args.get(1));
                }
                default -> { } // cutscene ids are Dagger-bound; an unknown one is logged at runtime
            }
        });
        assertNoErrors();
    }

    // --- the real stage() -----------------------------------------------------------------------

    @Test
    void eachActsOpeningStageResolvesOnRealMarkers() {
        for (String act : InkTestSupport.actIdsWithInk()) {
            NarrativeState state = new NarrativeState();
            state.setCurrentActId(act);
            StoryVariables vars = new StoryVariables();
            NarrativeRunner runner = InkTestSupport.runner(InkTestSupport.sourceFor(act), state, vars,
                    mock(Inventory.class), id -> true);
            for (StagedPlacement p : new StageDirector(runner, state, vars).all()) {
                String where = act + " stage() " + p.kind().name().toLowerCase() + " '" + p.id() + "'";
                if (!mapFiles.containsKey(p.mapId())) {
                    errors.add(where + ": no map '" + p.mapId() + "'");
                } else if (!objects(p.mapId(), "markers").contains(p.marker())) {
                    errors.add(where + ": map '" + p.mapId() + "' has no marker '" + p.marker() + "'");
                }
            }
        }
        assertNoErrors();
    }

    // --- variables --------------------------------------------------------------------------------

    private static final Pattern DECLARATION = Pattern.compile("^\\s*(VAR|CONST|LIST)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    @Test
    void everyStoryVariableIsDeclaredInExactlyOneFile() {
        // Story variables are saved and shared between acts by name. Two files declaring the same
        // name would silently share one value; a variable two acts need belongs in world.ink once.
        Map<String, List<String>> declaredIn = new LinkedHashMap<>();
        for (Map.Entry<String, List<File>> pack : inkFilesByPack.entrySet()) {
            for (File f : pack.getValue()) {
                for (String line : lines(f)) {
                    Matcher m = DECLARATION.matcher(stripComment(line));
                    if (m.find()) {
                        declaredIn.computeIfAbsent(m.group(2), k -> new ArrayList<>())
                                .add(PackTestSupport.packsRoot().toPath().relativize(f.toPath()).toString());
                    }
                }
            }
        }
        for (Map.Entry<String, List<String>> e : declaredIn.entrySet()) {
            if (e.getValue().size() > 1) {
                errors.add("'" + e.getKey() + "' is declared in " + e.getValue()
                        + "; declare it once (a variable acts share goes in baseline/ink/common/world.ink)");
            }
        }
        assertNoErrors();
    }

    // --- checks -----------------------------------------------------------------------------------

    /** {@code actor/thing/zone("id", "map", "marker", knot, ...)}: map and marker must exist. */
    private void checkPlacement(String where, String act, List<String> args, int arity) {
        if (args.size() != arity) {
            errors.add(where + ": expected " + arity + " arguments");
            return;
        }
        String map = literal(args, 1);
        String marker = literal(args, 2);
        if (map != null && !mapFiles.containsKey(map)) {
            errors.add(where + ": no map '" + map + "'");
        } else if (map != null && marker != null && !objects(map, "markers").contains(marker)) {
            errors.add(where + ": map '" + map + "' has no marker '" + marker
                    + "' (add it to that map's markers object layer)");
        }
        // A knot given as a string (rather than -> knot, which the compiler checks) must exist.
        String knot = literal(args, 3);
        if (knot != null && !knot.isEmpty() && !knotsOf(act).contains(knot)) {
            errors.add(where + ": '" + knot + "' is not a knot in " + act + "'s Ink (prefer -> " + knot + ")");
        }
    }

    /** {@code define("id", "sprite", "color")}: the sprite descriptor exists, the colour is 6-hex. */
    private void checkLook(String where, List<String> args) {
        if (args.size() != 3) {
            errors.add(where + ": expected define(id, sprite, color)");
            return;
        }
        String sprite = literal(args, 1);
        if (sprite != null && !sprite.isEmpty()
                && !PackTestSupport.contentExists("sprites/" + sprite + ".sprite.yaml")) {
            errors.add(where + ": no sprites/" + sprite + ".sprite.yaml in any pack");
        }
        String color = literal(args, 2);
        if (color != null && !color.isEmpty() && !color.matches("[0-9A-Fa-f]{6}")) {
            errors.add(where + ": colour '" + color + "' is not RRGGBB hex (no '#')");
        }
    }

    private void checkSeconds(String where, String raw) {
        try {
            if (Float.parseFloat(raw) < 0f) errors.add(where + ": seconds can't be negative");
        } catch (NumberFormatException e) {
            errors.add(where + ": '" + raw + "' is not a number of seconds");
        }
    }

    private void assertNoErrors() {
        assertTrue(errors.isEmpty(), "story reference errors:\n  " + String.join("\n  ", errors));
    }

    // --- traversal --------------------------------------------------------------------------------

    private interface LineVisitor {
        void visit(String act, File file, int lineNo, String line);
    }

    /** Every non-comment line of every act's own Ink (the baseline's shared files are declarations). */
    private void forEachActLine(LineVisitor visitor) {
        for (String act : InkTestSupport.actIdsWithInk()) {
            for (File f : inkFilesByPack.getOrDefault(act, List.of())) {
                List<String> lines = lines(f);
                for (int i = 0; i < lines.size(); i++) {
                    visitor.visit(act, f, i + 1, stripComment(lines.get(i)));
                }
            }
        }
    }

    private static final Map<String, Set<String>> KNOTS = new HashMap<>();
    private static final Pattern KNOT = Pattern.compile("^={2,}\\s*(?:function\\s+)?([A-Za-z0-9_]+)");

    private static Set<String> knotsOf(String act) {
        return KNOTS.computeIfAbsent(act, a -> {
            Set<String> out = new HashSet<>();
            for (File f : inkFilesByPack.getOrDefault(a, List.of())) {
                for (String line : lines(f)) {
                    Matcher m = KNOT.matcher(line.trim());
                    if (m.find()) out.add(m.group(1));
                }
            }
            return out;
        });
    }

    private static Set<String> objects(String mapId, String layer) {
        return mapObjects.getOrDefault(mapId, Map.of()).getOrDefault(layer, Set.of());
    }

    private static boolean anyMapHas(String layer, String name) {
        for (String map : mapObjects.keySet()) {
            if (objects(map, layer).contains(name)) return true;
        }
        return false;
    }

    // --- parsing helpers ------------------------------------------------------------------------

    /** A call's comma-separated arguments, trimmed; string literals keep their quotes. */
    private static List<String> splitArgs(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        for (char c : raw.toCharArray()) {
            if (c == '"') inString = !inString;
            if (c == ',' && !inString) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.toString().isBlank() || !out.isEmpty()) out.add(cur.toString().trim());
        return out;
    }

    /** The string literal at {@code i} without quotes, or null when that argument isn't a literal. */
    private static String literal(List<String> args, int i) {
        if (i >= args.size()) return null;
        String a = args.get(i);
        return a.length() >= 2 && a.startsWith("\"") && a.endsWith("\"") ? a.substring(1, a.length() - 1) : null;
    }

    private static String stripComment(String line) {
        int at = line.indexOf("//");
        return at < 0 ? line : line.substring(0, at);
    }

    /** Object names per object layer of a TMX. */
    private static Map<String, Set<String>> objectNamesByLayer(File tmx) {
        Map<String, Set<String>> out = new HashMap<>();
        Document doc = loadXml(tmx);
        NodeList groups = doc.getElementsByTagName("objectgroup");
        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);
            Set<String> names = out.computeIfAbsent(group.getAttribute("name"), k -> new HashSet<>());
            NodeList objects = group.getElementsByTagName("object");
            for (int j = 0; j < objects.getLength(); j++) {
                String name = ((Element) objects.item(j)).getAttribute("name");
                if (!name.isEmpty()) names.add(name);
            }
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

    private static List<File> listFiles(File dir, String suffix) {
        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(suffix));
        if (files != null) {
            for (File f : files) out.add(f);
        }
        return out;
    }

    private static List<String> lines(File f) {
        try {
            return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + f, e);
        }
    }

    private static String base(File f, String suffix) {
        return f.getName().substring(0, f.getName().length() - suffix.length());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(File f) {
        try (Reader r = new FileReader(f, StandardCharsets.UTF_8)) {
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
