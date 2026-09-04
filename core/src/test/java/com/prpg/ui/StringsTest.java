package com.prpg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StringsTest {

    private final Strings strings = new Strings(Map.of("ui.journal.title", "Journal"));

    @Test
    void literalsPassThroughUnchanged() {
        assertEquals("You're the one she wrote of.", strings.resolve("You're the one she wrote of."));
    }

    @Test
    void keyReferencesResolve() {
        assertEquals("Journal", strings.resolve("@ui.journal.title"));
    }

    @Test
    void missingKeyFallsBackToRawReference() {
        assertEquals("@ui.nope", strings.resolve("@ui.nope"));
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertNull(strings.resolve(null));
        assertEquals("", strings.resolve(""));
    }
}
