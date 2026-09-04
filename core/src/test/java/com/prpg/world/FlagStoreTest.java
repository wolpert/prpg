package com.prpg.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlagStoreTest {

    @Test
    void clearRunKeepsMetaFlagsDropsRunFlags() {
        FlagStore flags = new FlagStore();
        flags.setFlag("a1.read_will");
        flags.setFlag("meta.tutorial_done");

        flags.clearRun();

        assertFalse(flags.hasFlag("a1.read_will"), "run flag should be cleared");
        assertTrue(flags.hasFlag("meta.tutorial_done"), "meta flag should persist");
    }

    @Test
    void clearWipesEverything() {
        FlagStore flags = new FlagStore();
        flags.setFlag("meta.tutorial_done");
        flags.clear();
        assertFalse(flags.hasFlag("meta.tutorial_done"));
    }
}
