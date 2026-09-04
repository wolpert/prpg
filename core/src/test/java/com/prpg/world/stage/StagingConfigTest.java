package com.prpg.world.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prpg.config.ConfigLoader;
import com.prpg.world.stage.config.StagingConfig;
import com.prpg.world.stage.config.StagingConfig.CastEntry;
import com.prpg.world.stage.config.StagingConfig.ObstacleEntry;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the staging YAML contract. Entry types inherit their {@code at}/{@code when}/{@code solid}
 * vocabulary from a shared base class, and SnakeYAML's field access has to walk that hierarchy —
 * if it ever stops doing so, every condition silently reads as "unconditional", which would be a
 * quiet content bug rather than a crash. These tests fail loudly instead.
 */
class StagingConfigTest {

    private final ConfigLoader loader = new ConfigLoader();

    private StagingConfig parse(String yaml) {
        try (Reader r = new StringReader(yaml)) {
            return loader.load(StagingConfig.class, r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void inheritedFieldsPopulateFromYaml() {
        StagingConfig config = parse("""
                act: act1
                cast:
                  - actor: rowan
                    at: { map: boundary_path, marker: stone_row }
                    dialogue: rowan_worried
                    when: [a1.learned_to_bundle]
                    unless: [a1.rowan_left]
                    solid: true
                    solid_unless: [a1.rowan_stood_aside]
                """);

        assertEquals("act1", config.act);
        CastEntry entry = config.cast.get(0);
        assertEquals("rowan", entry.actor);
        assertEquals("rowan_worried", entry.dialogue);
        // The inherited half — the part a broken field walk would silently drop.
        assertNotNull(entry.at, "at is declared on the shared Entry base class");
        assertEquals("boundary_path", entry.at.map);
        assertEquals("stone_row", entry.at.marker);
        assertEquals(List.of("a1.learned_to_bundle"), entry.when);
        assertEquals(List.of("a1.rowan_left"), entry.unless);
        assertTrue(entry.solid);
        assertEquals(List.of("a1.rowan_stood_aside"), entry.solid_unless);
    }

    @Test
    void obstacleWithActivityParses() {
        StagingConfig config = parse("""
                act: act1
                obstacles:
                  - id: hall_overgrowth
                    at: { map: aunt_house_interior, marker: hall_north }
                    solid: true
                    activity: { type: match3, id: house_hall_overgrowth }
                    cleared_when: a1.hall_cleared
                    color: 4E6B3A
                """);

        ObstacleEntry herbs = config.obstacles.get(0);
        assertEquals("hall_overgrowth", herbs.id);
        assertTrue(herbs.solid);
        assertEquals("a1.hall_cleared", herbs.cleared_when);
        assertEquals("4E6B3A", herbs.color);
        assertNotNull(herbs.activity);
        assertEquals("match3", herbs.activity.type);
        assertEquals("house_hall_overgrowth", herbs.activity.id);
    }

    @Test
    void omittedConditionsDefaultToEmptyNotNull() {
        // The resolver treats an empty condition list as "unconditional"; a null would NPE.
        StagingConfig config = parse("""
                act: act1
                cast:
                  - actor: wren
                    at: { map: village_square, marker: wren_fence }
                    dialogue: wren_rhyme
                """);
        CastEntry entry = config.cast.get(0);
        assertTrue(entry.when.isEmpty());
        assertTrue(entry.unless.isEmpty());
        assertTrue(entry.solid_when.isEmpty());
        assertFalse(entry.solid);
    }

    @Test
    void theShippedActOneStagingFileParses() {
        File f = stagingFile();
        try (Reader r = new FileReader(f)) {
            StagingConfig config = loader.load(StagingConfig.class, r);
            assertEquals("act1", config.act);
            assertFalse(config.cast.isEmpty(), "the sample act stages at least one character");
            for (CastEntry entry : config.cast) {
                assertNotNull(entry.actor, "every cast entry names an actor");
                assertNotNull(entry.at, "every cast entry has a placement");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to parse " + f, e);
        }
    }

    private static File stagingFile() {
        for (String candidate : new String[]{"packs", "../packs", "../../packs"}) {
            File f = new File(candidate, "act1/staging/act1.yaml");
            if (f.exists()) return f;
        }
        throw new IllegalStateException("could not locate packs/act1/staging/act1.yaml from "
                + new File(".").getAbsolutePath());
    }
}
