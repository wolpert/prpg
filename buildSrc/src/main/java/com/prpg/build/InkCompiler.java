package com.prpg.build;

import com.bladecoder.ink.compiler.Compiler;
import com.bladecoder.ink.compiler.IFileHandler;
import com.bladecoder.ink.runtime.Error.ErrorType;
import com.bladecoder.ink.runtime.Story;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * In-JVM .ink &rarr; .json compiler, used by the root {@code compileInk} Gradle task. Pure-JVM
 * (blade-ink-compiler), so unlike the Aseprite pipeline there is no external binary to install — but
 * we keep the same convention (opt-in task, committed output) so CI never needs the Ink toolchain.
 *
 * <p>{@code INCLUDE} directives are resolved first relative to the main file's own directory, then
 * relative to the shared {@code ink/} root (so every act can {@code INCLUDE common/bridge.ink}).
 */
public final class InkCompiler {

    private InkCompiler() {}

    /** Compiles {@code mainInk} to an Ink JSON string, or throws with all collected compile errors. */
    public static String compile(File mainInk, File inkRoot) throws Exception {
        String source = Files.readString(mainInk.toPath(), StandardCharsets.UTF_8);

        List<String> errors = new ArrayList<>();
        Compiler.Options options = new Compiler.Options();
        options.sourceFilename = mainInk.getName();
        options.fileHandler = new RootedFileHandler(mainInk.getParentFile(), inkRoot);
        options.errorHandler = (message, type) -> {
            if (type == ErrorType.Error) {
                errors.add(message);
            }
        };

        Story story;
        try {
            story = new Compiler(source, options).compile();
        } catch (Exception e) {
            if (!errors.isEmpty()) {
                throw new IllegalStateException(
                        "Ink compile failed for " + mainInk + ":\n  " + String.join("\n  ", errors), e);
            }
            throw e;
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Ink compile failed for " + mainInk + ":\n  " + String.join("\n  ", errors));
        }
        if (story == null) {
            throw new IllegalStateException("Ink compile produced no story for " + mainInk);
        }
        return story.toJson();
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
            File relative = new File(mainDir, includeName);
            if (relative.exists()) {
                return relative.getAbsolutePath();
            }
            return new File(inkRoot, includeName).getAbsolutePath();
        }

        @Override
        public String loadInkFileContents(String fullFilename) throws IOException {
            return Files.readString(new File(fullFilename).toPath(), StandardCharsets.UTF_8);
        }
    }
}
