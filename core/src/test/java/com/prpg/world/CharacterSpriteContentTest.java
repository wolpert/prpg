package com.prpg.world;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Cross-reference guard rail for character sprite descriptors (the {@code *.sprite.yaml} files read by
 * {@code CharacterSpriteLoader}): every animation the loader will request — {@code <region>_<state>_<dir>}
 * for states idle/walk and directions down/up/right (plus left when not flip-derived) — must exist as a
 * region in the referenced atlas. Catches a renamed/missing Aseprite tag or a stale descriptor before it
 * becomes a runtime "no regions named" crash. Pure JVM (SnakeYAML + atlas text parse) — no GL.
 */
class CharacterSpriteContentTest {

    private static File packsRoot;

    @BeforeAll
    static void locatePacks() {
        for (String candidate : new String[]{"packs", "../packs", "../../packs"}) {
            File f = new File(candidate);
            if (new File(f, "baseline").isDirectory()) {
                packsRoot = f;
                break;
            }
        }
        if (packsRoot == null) fail("could not locate packs/ from " + new File(".").getAbsolutePath());
    }

    @Test
    void everyDescriptorRegionExistsInItsAtlas() {
        List<String> errors = new ArrayList<>();
        List<File> descriptors = findDescriptors();
        assertTrue(!descriptors.isEmpty(), "expected at least one *.sprite.yaml descriptor");

        for (File desc : descriptors) {
            Map<String, Object> def = loadYaml(desc);
            String region = String.valueOf(def.get("region"));
            String atlasPath = String.valueOf(def.get("atlas"));
            boolean flipLeft = !Boolean.FALSE.equals(def.get("flipRightForLeft"));

            File atlas = resolveLogical(atlasPath);
            if (atlas == null) {
                errors.add(desc.getName() + ": atlas '" + atlasPath + "' not found in any pack");
                continue;
            }
            Set<String> regions = atlasRegionNames(atlas);

            List<String> dirs = new ArrayList<>(List.of("down", "up", "right"));
            if (!flipLeft) dirs.add("left"); // otherwise left is derived by flipping right
            for (String state : new String[]{"idle", "walk"}) {
                for (String dir : dirs) {
                    String name = region + "_" + state + "_" + dir;
                    if (!regions.contains(name)) {
                        errors.add(desc.getName() + ": atlas '" + atlasPath
                                + "' has no region '" + name + "'");
                    }
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n  ", errors));
    }

    // --- helpers ------------------------------------------------------------------------------

    private static List<File> findDescriptors() {
        List<File> out = new ArrayList<>();
        File[] packs = packsRoot.listFiles(f -> f.isDirectory() && new File(f, "pack.yaml").isFile());
        if (packs == null) return out;
        for (File pack : packs) {
            File sprites = new File(pack, "sprites");
            File[] files = sprites.listFiles((d, n) -> n.endsWith(".sprite.yaml"));
            if (files != null) {
                for (File f : files) out.add(f);
            }
        }
        return out;
    }

    /** Resolve a logical content path (e.g. atlases/baseline.atlas) against the pack union. */
    private static File resolveLogical(String logicalPath) {
        File[] packs = packsRoot.listFiles(f -> f.isDirectory() && new File(f, "pack.yaml").isFile());
        if (packs == null) return null;
        for (File pack : packs) {
            File candidate = new File(pack, logicalPath);
            if (candidate.isFile()) return candidate;
        }
        return null;
    }

    /** Region base names in a libGDX .atlas: non-indented lines that aren't the page header or a page image. */
    private static Set<String> atlasRegionNames(File atlas) {
        Set<String> names = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(atlas.toPath())) {
                if (line.isBlank() || Character.isWhitespace(line.charAt(0))) continue; // region props are indented
                if (line.contains(":")) continue;                                       // size:/format:/filter:/repeat:
                if (line.endsWith(".png") || line.endsWith(".jpg") || line.endsWith(".jpeg")) continue; // page image
                names.add(line.trim());
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + atlas, e);
        }
        return names;
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
}
