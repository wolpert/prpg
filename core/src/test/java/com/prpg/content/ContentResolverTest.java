package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.prpg.assets.AssetManifest;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link ContentResolver} resolution-order coverage: a mounted pack wins; otherwise it falls back to
 * the bundled internal assets; and {@code list} unions both. The internal fallback is injected via the
 * package-private test seam so no libGDX {@code Gdx.files} is needed.
 */
class ContentResolverTest {

    /** A fallback that fabricates an absolute handle so we can assert "fell through to internal". */
    private static final FileHandleResolver INTERNAL =
            path -> new FileHandle(new File("/bundled", path));

    @Test
    void packHitWinsOverInternal() {
        PackRegistry packs = mock(PackRegistry.class);
        FileHandle packHandle = new FileHandle(new File("/content/act3", "maps/x.tmx"));
        when(packs.resolve("maps/x.tmx")).thenReturn(packHandle);

        ContentResolver resolver = new ContentResolver(mock(AssetManifest.class), packs, INTERNAL);
        assertEquals(packHandle, resolver.resolve("maps/x.tmx"));
    }

    @Test
    void fallsBackToInternalWhenNoPackProvides() {
        PackRegistry packs = mock(PackRegistry.class);
        when(packs.resolve(anyString())).thenReturn(null);

        ContentResolver resolver = new ContentResolver(mock(AssetManifest.class), packs, INTERNAL);
        FileHandle h = resolver.resolve("ui/uiskin.json");
        assertTrue(h.path().replace('\\', '/').endsWith("/bundled/ui/uiskin.json"), h.path());
    }

    @Test
    void existsConsultsPackThenManifest() {
        PackRegistry packs = mock(PackRegistry.class);
        when(packs.has("maps/x.tmx")).thenReturn(true);
        when(packs.has("config/game.yaml")).thenReturn(false);

        AssetManifest manifest = mock(AssetManifest.class);
        when(manifest.contains("config/game.yaml")).thenReturn(true);

        ContentResolver resolver = new ContentResolver(manifest, packs, INTERNAL);
        assertTrue(resolver.exists("maps/x.tmx"), "provided by a pack");
        assertTrue(resolver.exists("config/game.yaml"), "provided by bundled manifest");
    }

    @Test
    void listUnionsPackAndBundled() {
        PackRegistry packs = mock(PackRegistry.class);
        when(packs.list("quests/")).thenReturn(List.of("quests/act3_quest.yaml"));

        AssetManifest manifest = mock(AssetManifest.class);
        when(manifest.files()).thenReturn(Set.of("quests/the_settling_in.yaml", "maps/a.tmx"));

        ContentResolver resolver = new ContentResolver(manifest, packs, INTERNAL);
        assertEquals(
                List.of("quests/act3_quest.yaml", "quests/the_settling_in.yaml"),
                resolver.list("quests/"));
    }
}
