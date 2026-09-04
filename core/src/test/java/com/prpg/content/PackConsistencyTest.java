package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Per-pack integrity guard rails (the analogue of {@code ContentValidationTest} for pack manifests):
 * each {@code pack.yaml}'s generated {@code files:} list matches the pack's runtime files on disk,
 * every {@code requiresFlags:} entry is declared by some pack (the cross-pack flag contract), and any
 * committed atlas references page images that exist. Pure JVM (SnakeYAML + a directory walk) — no GL.
 */
class PackConsistencyTest {

    private static File packsRoot;

    @BeforeAll
    static void locatePacks() {
        packsRoot = PackTestSupport.packsRoot();
    }

    private static File[] packDirs() {
        return PackTestSupport.packDirs();
    }

    @Test
    void packIdsMatchTheirFolderAndBaselineIsBundled() {
        List<String> errors = new ArrayList<>();
        for (File pack : packDirs()) {
            Map<String, Object> manifest = loadYaml(new File(pack, "pack.yaml"));
            if (!pack.getName().equals(String.valueOf(manifest.get("id")))) {
                errors.add(pack.getName() + ": pack.yaml id '" + manifest.get("id") + "' must equal the folder name");
            }
            if ("baseline".equals(pack.getName()) && !Boolean.TRUE.equals(manifest.get("bundled"))) {
                errors.add("baseline must be bundled: true (every act depends on it)");
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n  ", errors));
    }

    @Test
    void manifestFilesMatchDisk() {
        List<String> errors = new ArrayList<>();
        for (File pack : packDirs()) {
            Set<String> declared = new TreeSet<>(stringList(loadYaml(new File(pack, "pack.yaml")).get("files")));
            Set<String> onDisk = new TreeSet<>(runtimeFiles(pack));
            if (!declared.equals(onDisk)) {
                Set<String> missing = new TreeSet<>(onDisk);
                missing.removeAll(declared);
                Set<String> stale = new TreeSet<>(declared);
                stale.removeAll(onDisk);
                errors.add(pack.getName() + ": pack.yaml files: out of date — missing " + missing
                        + ", stale " + stale + " (run ./gradlew generatePackManifests)");
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n  ", errors));
    }

    @Test
    void requiredFlagsAreDeclaredSomewhere() {
        Set<String> declared = new HashSet<>();
        for (File pack : packDirs()) {
            declared.addAll(stringList(loadYaml(new File(pack, "pack.yaml")).get("flags")));
        }
        List<String> errors = new ArrayList<>();
        for (File pack : packDirs()) {
            for (String flag : stringList(loadYaml(new File(pack, "pack.yaml")).get("requiresFlags"))) {
                if (!declared.contains(flag)) {
                    errors.add(pack.getName() + " requiresFlags '" + flag + "' is declared by no pack");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n  ", errors));
    }

    @Test
    void committedAtlasPagesExist() {
        List<String> errors = new ArrayList<>();
        for (File pack : packDirs()) {
            File atlasDir = new File(pack, "atlases");
            File[] atlases = atlasDir.listFiles((d, n) -> n.endsWith(".atlas"));
            if (atlases == null) continue;
            for (File atlas : atlases) {
                for (String page : atlasPages(atlas)) {
                    if (!new File(atlasDir, page).isFile()) {
                        errors.add(pack.getName() + "/atlases/" + atlas.getName()
                                + ": missing page image '" + page + "'");
                    }
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n  ", errors));
    }

    // --- helpers ------------------------------------------------------------------------------

    /** A pack's runtime files (relative, sorted) — mirrors PackTool.runtimeFiles in buildSrc. */
    private static List<String> runtimeFiles(File packDir) {
        Path root = packDir.toPath();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
                    .filter(rel -> !rel.equals("pack.yaml") && !rel.equals("pack.meta.yaml")
                            && !rel.startsWith("ink/") && !rel.startsWith("art/"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("failed to walk " + packDir, e);
        }
    }

    /** Page image filenames in a libGDX .atlas — the non-indented lines ending in an image suffix. */
    private static List<String> atlasPages(File atlas) {
        List<String> pages = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(atlas.toPath())) {
                if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))
                        && (line.endsWith(".png") || line.endsWith(".jpg") || line.endsWith(".jpeg"))) {
                    pages.add(line.trim());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + atlas, e);
        }
        return pages;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object e : list) out.add(String.valueOf(e));
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
}
