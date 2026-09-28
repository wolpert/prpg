package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.bladecoder.ink.compiler.Compiler;
import com.bladecoder.ink.compiler.IFileHandler;
import com.bladecoder.ink.runtime.Story;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.content.PackRegistry;
import com.prpg.items.Inventory;
import com.prpg.narrative.config.NarrativeManifest;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.narrative.content.ActContentSource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Test support for anything that runs the real sample stories. Compiles {@code ink/} source on the
 * fly with the same blade-ink compiler the Gradle task uses, so tests never depend on committed JSON
 * being fresh. Pure JVM, no libGDX.
 */
public final class InkTestSupport {

    private InkTestSupport() {}

    private static final Map<String, String> CACHE = new LinkedHashMap<>();

    /** Locates the repo-root {@code packs/} dir from whatever working dir the test runner uses. */
    public static File packsRoot() {
        for (String candidate : new String[]{"packs", "../packs", "../../packs"}) {
            File f = new File(candidate);
            if (new File(f, "baseline").isDirectory()) return f;
        }
        fail("could not locate packs/ dir from " + new File(".").getAbsolutePath());
        return null; // unreachable
    }

    /** The shared Ink include root: {@code packs/baseline/ink/} (holds {@code common/bridge.ink}). */
    public static File commonInkRoot() {
        return new File(packsRoot(), "baseline/ink");
    }

    /** The main Ink source for an act: {@code packs/<actId>/ink/<actId>.ink}. */
    public static File inkSource(String actId) {
        return new File(packsRoot(), actId + "/ink/" + actId + ".ink");
    }

    /** Every pack id that has an {@code ink/<id>.ink} story, sorted. */
    public static List<String> actIdsWithInk() {
        List<String> out = new ArrayList<>();
        File[] packs = packsRoot().listFiles(File::isDirectory);
        if (packs == null) return out;
        for (File pack : packs) {
            if (inkSource(pack.getName()).isFile()) out.add(pack.getName());
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Compiles {@code packs/<actId>/ink/<actId>.ink} to JSON (cached per actId). */
    public static String compileJson(String actId) {
        return CACHE.computeIfAbsent(actId, InkTestSupport::compileUncached);
    }

    private static String compileUncached(String actId) {
        File main = inkSource(actId);
        try {
            String source = Files.readString(main.toPath(), StandardCharsets.UTF_8);
            Compiler.Options options = new Compiler.Options();
            options.sourceFilename = main.getName();
            options.fileHandler = new RootedFileHandler(main.getParentFile(), commonInkRoot());
            Story story = new Compiler(source, options).compile();
            return story.toJson();
        } catch (Exception e) {
            throw new IllegalStateException("failed to compile ink act " + actId, e);
        }
    }

    /** A fresh runtime Story for an act (not bridged). */
    public static Story story(String actId) {
        try {
            return new Story(compileJson(actId));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load ink act " + actId, e);
        }
    }

    /** An in-memory {@link ActContentSource} that supplies exactly the named acts. */
    public static ActContentSource sourceFor(String... actIds) {
        Map<String, String> jsons = new LinkedHashMap<>();
        for (String id : actIds) jsons.put(id, compileJson(id));
        return new ActContentSource() {
            @Override
            public boolean has(String actId) {
                return jsons.containsKey(actId);
            }

            @Override
            public String read(String actId) {
                return jsons.get(actId);
            }
        };
    }

    /** A catalog declaring the given acts in order (order = position + 1), over {@code source}. */
    public static ActContentRegistry registry(ActContentSource source, String... actIds) {
        ActContentRegistry registry = new ActContentRegistry(
                new ConfigLoader(), mock(ContentResolver.class), mock(PackRegistry.class), source);
        NarrativeManifest manifest = new NarrativeManifest();
        for (int i = 0; i < actIds.length; i++) {
            NarrativeManifest.ActEntry e = new NarrativeManifest.ActEntry();
            e.id = actIds[i];
            e.order = i + 1;
            e.entitlement = i == 0 ? "FREE" : "PAID";
            manifest.acts.add(e);
        }
        registry.useManifest(manifest);
        return registry;
    }

    /** Advances a linear (choice-free) conversation to its end, collecting each line's text. */
    public static List<String> drain(NarrativeRunner r) {
        List<String> lines = new ArrayList<>();
        int guard = 0;
        while (r.isActive() && !r.hasChoices() && guard++ < 500) {
            if (!r.currentText().isEmpty()) lines.add(r.currentText());
            r.advance();
        }
        return lines;
    }

    /** Drives a conversation to its end, always taking the first available choice. Returns all lines. */
    public static List<String> drivePickingFirst(NarrativeRunner r) {
        List<String> lines = new ArrayList<>();
        int guard = 0;
        while (r.isActive() && guard++ < 500) {
            if (!r.currentText().isEmpty()) lines.add(r.currentText());
            if (r.hasChoices()) {
                r.selectChoice(0);
            } else {
                r.advance();
            }
        }
        return lines;
    }

    /**
     * A {@link NarrativeRunner} wired to a {@link StateBridge} over the supplied collaborators, with a
     * catalog of the two sample acts so {@code next_act_gate()} / {@code advance_act()} are live, and
     * with {@code commands} as the registered story commands.
     */
    public static NarrativeRunner runner(ActContentSource source, NarrativeState state,
                                         StoryVariables vars, Inventory inv, Entitlement ent,
                                         Map<String, StoryCommand> commands) {
        ActProgression progression = new ActProgression(state, ent, registry(source, "act1", "act2"));
        StateBridge bridge = new StateBridge(state, inv, ent, progression);
        return new NarrativeRunner(source, bridge, vars, commands);
    }

    /** {@link #runner} with every command finishing instantly (a stand-in for the world). */
    public static NarrativeRunner runner(ActContentSource source, NarrativeState state,
                                         StoryVariables vars, Inventory inv, Entitlement ent) {
        return runner(source, state, vars, inv, ent, instantCommands(null));
    }

    /**
     * Every real command name bound to a stand-in that finishes at once, reporting each line it was
     * given to {@code seen} (nullable). {@code play} leaves {@code activity_won} as it is.
     */
    public static Map<String, StoryCommand> instantCommands(Consumer<List<String>> seen) {
        Map<String, StoryCommand> out = new LinkedHashMap<>();
        for (String name : List.of("play", "go", "wait", "fade", "cutscene")) {
            out.put(name, new StoryCommand() {
                @Override
                public boolean start(List<String> args) {
                    if (seen != null) {
                        List<String> line = new ArrayList<>();
                        line.add(name);
                        line.addAll(args);
                        seen.accept(line);
                    }
                    return false;
                }

                @Override
                public boolean update(float delta) {
                    return false;
                }
            });
        }
        return out;
    }

    /** Resolves INCLUDEs against the main file's dir first, then the shared ink/ root. */
    private static final class RootedFileHandler implements IFileHandler {
        private final File mainDir;
        private final File inkRoot;

        RootedFileHandler(File mainDir, File inkRoot) {
            this.mainDir = mainDir;
            this.inkRoot = inkRoot;
        }

        @Override
        public String resolveInkFilename(String includeName) {
            File rel = new File(mainDir, includeName);
            if (rel.exists()) return rel.getAbsolutePath();
            return new File(inkRoot, includeName).getAbsolutePath();
        }

        @Override
        public String loadInkFileContents(String fullFilename) throws IOException {
            return Files.readString(new File(fullFilename).toPath(), StandardCharsets.UTF_8);
        }
    }
}
