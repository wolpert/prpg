package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.bladecoder.ink.runtime.Story;
import com.prpg.items.Inventory;
import com.prpg.world.FlagStore;
import com.prpg.world.GameClock;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guard rail for the Ink content: every act compiles, the bridge contract matches the Ink
 * {@code EXTERNAL} declarations both ways, every catalogued act has source, and every bundled act
 * has its compiled JSON committed (CI consumes committed JSON).
 */
class InkContentValidationTest {

    @Test
    void thereIsAtLeastOneAct() {
        assertFalse(InkTestSupport.actIdsWithInk().isEmpty(), "no pack has an ink/<id>.ink story");
    }

    @Test
    void everyActCompilesCleanly() {
        for (String act : InkTestSupport.actIdsWithInk()) {
            try {
                InkTestSupport.compileJson(act); // throws with collected errors on failure
            } catch (Exception e) {
                fail("act failed to compile: " + act + ": " + e.getMessage());
            }
        }
    }

    @Test
    void bridgeBindingsMatchExternalDeclarationsBothWays() {
        Set<String> declared = declaredExternals();
        Set<String> bound = new LinkedHashSet<>(StateBridge.READ_FUNCTIONS);
        bound.addAll(StateBridge.WRITE_FUNCTIONS);

        Set<String> declaredNotBound = new LinkedHashSet<>(declared);
        declaredNotBound.removeAll(bound);
        assertTrue(declaredNotBound.isEmpty(),
                "EXTERNALs declared in bridge.ink but not bound by StateBridge: " + declaredNotBound);

        Set<String> boundNotDeclared = new LinkedHashSet<>(bound);
        boundNotDeclared.removeAll(declared);
        assertTrue(boundNotDeclared.isEmpty(),
                "StateBridge binds functions not declared EXTERNAL in bridge.ink: " + boundNotDeclared);
    }

    @Test
    void everyDeclaredExternalIsActuallyBound() throws Exception {
        // validateExternalBindings throws if any declared EXTERNAL is unbound. Run it for every act,
        // so an act that adds a new EXTERNAL without a binding fails the build.
        for (String act : InkTestSupport.actIdsWithInk()) {
            Story story = InkTestSupport.story(act);
            FlagStore flags = new FlagStore();
            NarrativeState state = new NarrativeState();
            var stage = InkTestSupport.stageDirector(flags);
            var progression = new ActProgression(state, id -> true,
                    InkTestSupport.registry(InkTestSupport.sourceFor(act), act), stage);
            new StateBridge(state, flags, mock(Inventory.class), new GameClock(), id -> true, stage,
                    progression).install(story);
            story.validateExternalBindings();
        }
    }

    @Test
    void everyManifestActHasInkSource() {
        File manifestFile = new File(InkTestSupport.packsRoot(), "baseline/narrative/manifest.yaml");
        Map<String, Object> manifest = loadYaml(manifestFile);
        Object actsObj = manifest.get("acts");
        assertTrue(actsObj instanceof List, "manifest has an acts list");
        for (Object o : (List<?>) actsObj) {
            if (!(o instanceof Map<?, ?> act)) continue;
            String id = String.valueOf(act.get("id"));
            File ink = InkTestSupport.inkSource(id);
            assertTrue(ink.exists(), "manifest act '" + id + "' has ink source at " + ink);
        }
    }

    @Test
    void bundledActsAreCommittedAsCompiledJson() {
        // Packs marked bundled: true ship compiled Ink inside the app, so their JSON must be committed.
        File packs = InkTestSupport.packsRoot();
        for (String act : InkTestSupport.actIdsWithInk()) {
            Map<String, Object> pack = loadYaml(new File(packs, act + "/pack.yaml"));
            if (!Boolean.TRUE.equals(pack.get("bundled"))) continue;
            File json = new File(packs, act + "/narrative/" + act + ".ink.json");
            assertTrue(json.exists(), "bundled act committed: " + json + " (run ./gradlew compileInk)");
        }
    }

    // --- helpers ------------------------------------------------------------------------------

    private static Set<String> declaredExternals() {
        File bridge = new File(InkTestSupport.commonInkRoot(), "common/bridge.ink");
        Set<String> names = new HashSet<>();
        Pattern p = Pattern.compile("^\\s*EXTERNAL\\s+(\\w+)\\s*\\(");
        try {
            for (String line : Files.readAllLines(bridge.toPath(), StandardCharsets.UTF_8)) {
                Matcher m = p.matcher(line);
                if (m.find()) names.add(m.group(1));
            }
        } catch (Exception e) {
            fail("could not read " + bridge + ": " + e.getMessage());
        }
        assertFalse(names.isEmpty(), "found EXTERNAL declarations in bridge.ink");
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(File f) {
        try (var r = new java.io.FileReader(f)) {
            Object loaded = new Yaml().load(r);
            return loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + f, e);
        }
    }
}
