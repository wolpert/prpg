package com.prpg.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.error.YAMLException;

class ConfigLoaderTest {

    private final ConfigLoader loader = new ConfigLoader();

    @Test
    void parsesNestedYamlIntoPublicFields() {
        String yaml = """
                title: My Game
                player:
                  speed: 120
                world:
                  viewWidth: 320
                """;

        GameConfig config = loader.load(GameConfig.class, new StringReader(yaml));

        assertEquals("My Game", config.title);
        assertEquals(120f, config.player.speed);
        assertEquals(320f, config.world.viewWidth);
        assertEquals(320f, config.world.viewHeight, "omitted fields keep their Java defaults");
    }

    @Test
    void rejectsUnknownFields() {
        // SnakeYAML's strict POJO constructor catches typos in config keys instead of silently ignoring them.
        String yaml = """
                title: Oops
                playr:
                  speed: 1
                """;

        assertThrows(YAMLException.class, () -> loader.load(GameConfig.class, new StringReader(yaml)));
    }
}
