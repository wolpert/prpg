package com.prpg.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssetManifestTest {

    @Test
    void parsesNonBlankLinesAndIgnoresWhitespace() throws IOException {
        String manifest = """
                atlases/wormwood-and-yarrow.atlas
                  libgdx.png

                ui/uiskin.json
                """;

        Set<String> parsed = AssetManifest.parse(new StringReader(manifest));

        assertEquals(List.of("atlases/wormwood-and-yarrow.atlas", "libgdx.png", "ui/uiskin.json"),
                List.copyOf(parsed));
    }

    @Test
    void containsLooksUpExactPath() throws IOException {
        Set<String> parsed = AssetManifest.parse(new StringReader("libgdx.png\nfoo/bar.atlas\n"));
        AssetManifest manifest = new AssetManifest(parsed);

        assertTrue(manifest.contains("libgdx.png"));
        assertTrue(manifest.contains("foo/bar.atlas"));
        assertFalse(manifest.contains("missing.png"));
    }
}
