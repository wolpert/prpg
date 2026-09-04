package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.files.FileHandle;
import com.prpg.config.ConfigLoader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Headless coverage of {@link PackRegistry}: it reads {@code packs.yaml} + each pack's {@code pack.yaml}
 * from the (temp) content root and answers ownership queries. Crucially, an act/epilogue pack shadows
 * the baseline when both declare the same shared file. Pure JVM — absolute {@link FileHandle}s need no
 * libGDX init.
 */
class PackRegistryTest {

    @TempDir
    Path tmp;

    private PackRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        write("packs.yaml",
                "packs:\n"
                        + "  - { id: act3, version: 2 }\n"
                        + "  - { id: baseline, version: 1 }\n");
        write("act3/pack.yaml",
                "id: act3\nkind: act\nversion: 2\n"
                        + "files:\n  - maps/act3.tmx\n  - tilesets/shared.tsx\n");
        write("baseline/pack.yaml",
                "id: baseline\nkind: baseline\nversion: 1\n"
                        + "files:\n  - tilesets/shared.tsx\n  - tilesets/wooden.tsx\n");

        ContentRoot root = mock(ContentRoot.class);
        when(root.packsRoot()).thenAnswer(i -> new FileHandle(tmp.toFile()));
        when(root.packs(anyString())).thenAnswer(i -> new FileHandle(new File(tmp.toFile(), i.getArgument(0))));

        registry = new PackRegistry(root, new ConfigLoader());
    }

    private void write(String rel, String content) throws Exception {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    @Test
    void actPackShadowsBaselineForSharedFile() {
        FileHandle shared = registry.resolve("tilesets/shared.tsx");
        assertTrue(shared.path().contains("/act3/"), "act pack wins over baseline: " + shared.path());
    }

    @Test
    void baselineServesItsOwnFiles() {
        FileHandle wooden = registry.resolve("tilesets/wooden.tsx");
        assertTrue(wooden.path().contains("/baseline/"), wooden.path());
    }

    @Test
    void resolvesPackOwnedMap() {
        assertTrue(registry.resolve("maps/act3.tmx").path().contains("/act3/"));
    }

    @Test
    void unknownPathIsNull() {
        assertNull(registry.resolve("maps/nope.tmx"));
        assertFalse(registry.has("maps/nope.tmx"));
    }

    @Test
    void listUnionsAndDeduplicates() {
        assertEquals(List.of("tilesets/shared.tsx", "tilesets/wooden.tsx"), registry.list("tilesets/"));
    }

    @Test
    void emptyRegistryWhenNoFile() {
        ContentRoot empty = mock(ContentRoot.class);
        Path other = tmp.resolve("empty");
        when(empty.packs(anyString())).thenAnswer(i -> new FileHandle(new File(other.toFile(), i.getArgument(0))));
        PackRegistry none = new PackRegistry(empty, new ConfigLoader());
        assertNull(none.resolve("anything"));
        assertTrue(none.list("maps/").isEmpty());
    }
}
