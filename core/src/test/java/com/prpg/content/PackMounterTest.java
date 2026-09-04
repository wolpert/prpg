package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.prpg.assets.AssetManifest;
import com.prpg.config.ConfigLoader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Headless coverage of {@link PackMounter}: a zip dropped in the content root is extracted into
 * {@code <root>/<id>/}, the zip is deleted, the registry is written, and a loose unzipped folder is
 * backfilled. Drives the real {@link PackRegistry} afterward to prove the mounted pack resolves.
 */
class PackMounterTest {

    @TempDir
    Path tmp;

    private ContentRoot root;
    private PackRegistry registry;
    private PackMounter mounter;

    @BeforeEach
    void setUp() {
        root = mock(ContentRoot.class);
        when(root.packsRoot()).thenAnswer(i -> new FileHandle(tmp.toFile()));
        when(root.packs(anyString())).thenAnswer(i -> new FileHandle(new File(tmp.toFile(), i.getArgument(0))));
        registry = new PackRegistry(root, new ConfigLoader());
        // Default: no bundled packs, internal source points nowhere (extraction no-ops).
        AssetManifest noBundled = mock(AssetManifest.class);
        when(noBundled.files()).thenReturn(Set.of());
        FileHandleResolver noInternal = path -> new FileHandle(new File(tmp.toFile(), "__none__/" + path));
        mounter = new PackMounter(root, new ConfigLoader(), registry, noBundled, noInternal);
    }

    private void writeZip(String zipName, Map<String, String> entries) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp.resolve(zipName)))) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    @Test
    void installsZipAndResolvesThroughRegistry() throws Exception {
        writeZip("act7.zip", Map.of(
                "pack.yaml", "id: act7\nkind: act\nversion: 1\nfiles:\n  - maps/a.tmx\n",
                "maps/a.tmx", "<map/>"));

        mounter.mount();

        assertFalse(tmp.resolve("act7.zip").toFile().exists(), "zip consumed after install");
        assertTrue(tmp.resolve("act7/pack.yaml").toFile().exists(), "pack.yaml extracted");
        assertTrue(tmp.resolve("act7/maps/a.tmx").toFile().exists(), "content extracted");
        assertTrue(tmp.resolve("packs.yaml").toFile().exists(), "registry written");

        FileHandle resolved = registry.resolve("maps/a.tmx");
        assertTrue(resolved != null && resolved.path().contains("/act7/"), "registry resolves the mounted map");
    }

    @Test
    void backfillsLooseFolder() throws Exception {
        Path manifest = tmp.resolve("act8/pack.yaml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "id: act8\nkind: act\nversion: 3\nfiles:\n  - maps/b.tmx\n");

        mounter.mount();

        String packsYaml = Files.readString(tmp.resolve("packs.yaml"));
        assertTrue(packsYaml.contains("id: act8"), packsYaml);
        assertTrue(packsYaml.contains("version: 3"), packsYaml);
    }

    @Test
    void rejectsZipSlipEntry() throws Exception {
        // An entry that escapes the destination must not be written outside the pack dir.
        writeZip("evil.zip", Map.of(
                "pack.yaml", "id: evil\nkind: act\nversion: 1\nfiles: []\n",
                "../../escape.txt", "pwned"));

        mounter.mount();

        assertFalse(tmp.getParent().resolve("escape.txt").toFile().exists(), "zip-slip blocked");
    }

    @Test
    void extractsBundledPackFromInternalAssets() throws Exception {
        // A fake "internal assets" tree holding a staged bundled pack.
        Path internalDir = tmp.resolve("__internal__");
        Files.createDirectories(internalDir.resolve("packs/baseline/config"));
        Files.writeString(internalDir.resolve("packs/baseline/pack.yaml"),
                "id: baseline\nkind: baseline\nversion: 1\nfiles:\n  - config/game.yaml\n");
        Files.writeString(internalDir.resolve("packs/baseline/config/game.yaml"), "world:\n  startMap: x\n");

        AssetManifest manifest = mock(AssetManifest.class);
        when(manifest.files()).thenReturn(Set.of("packs/baseline/pack.yaml", "packs/baseline/config/game.yaml"));
        FileHandleResolver internal = path -> new FileHandle(new File(internalDir.toFile(), path));

        PackMounter bundledMounter = new PackMounter(root, new ConfigLoader(), registry, manifest, internal);
        bundledMounter.mount();

        assertTrue(tmp.resolve("baseline/pack.yaml").toFile().exists(), "pack.yaml extracted");
        assertTrue(tmp.resolve("baseline/config/game.yaml").toFile().exists(), "listed file extracted");
        FileHandle game = registry.resolve("config/game.yaml");
        assertTrue(game != null && game.path().contains("/baseline/"), "registry resolves the bundled file");

        // Idempotent: a second mount at the same version doesn't fail or duplicate.
        bundledMounter.mount();
        assertTrue(tmp.resolve("baseline/config/game.yaml").toFile().exists());
    }

    @Test
    void noContentRootIsHarmless() {
        // Empty root, nothing dropped — mount writes an empty registry and resolves nothing.
        mounter.mount();
        assertTrue(registry.list("maps/").isEmpty());
    }
}
