package com.prpg.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Build-time helper for content packs (used by the root {@code packPack}/{@code packAll}/
 * {@code generatePackManifests} tasks). Pure JDK — no external deps — so it lives in buildSrc
 * alongside {@link InkCompiler}.
 *
 * <p>A pack's {@code pack.yaml} carries authored metadata followed by a generated {@code files:}
 * block (the per-pack analogue of {@code assets.txt}). {@code files:} must be the last top-level key;
 * {@link #regenerateFiles} preserves everything above it and rewrites the list from the pack's actual
 * runtime files (everything except the Ink/art sources and the manifest itself).
 */
public final class PackTool {

    public static final String PACK_MANIFEST = "pack.yaml";

    private PackTool() {}

    /** Rewrites the {@code files:} block of {@code <packDir>/pack.yaml}; returns the file list. */
    public static List<String> regenerateFiles(File packDir) throws IOException {
        File packYaml = new File(packDir, PACK_MANIFEST);
        if (!packYaml.isFile()) {
            throw new IOException("no " + PACK_MANIFEST + " in " + packDir);
        }
        StringBuilder head = new StringBuilder();
        for (String line : Files.readAllLines(packYaml.toPath(), StandardCharsets.UTF_8)) {
            if (line.strip().equals("files:")) {
                break; // drop the old files block; everything below it is regenerated
            }
            head.append(line).append('\n');
        }
        List<String> files = runtimeFiles(packDir);
        StringBuilder out = new StringBuilder(head);
        out.append("files:\n");
        for (String f : files) {
            out.append("  - ").append(f).append('\n');
        }
        Files.writeString(packYaml.toPath(), out.toString(), StandardCharsets.UTF_8);
        return files;
    }

    /** A pack's runtime files (relative, sorted) — excludes Ink/art sources and the manifests. */
    public static List<String> runtimeFiles(File packDir) throws IOException {
        Path root = packDir.toPath();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
                    .filter(PackTool::isRuntimeFile)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static boolean isRuntimeFile(String rel) {
        return !rel.equals(PACK_MANIFEST)
                && !rel.equals("pack.meta.yaml")
                && !rel.startsWith("ink/")
                && !rel.startsWith("art/");
    }

    /** Zips a pack's {@code pack.yaml} + runtime files (manifest regenerated first) into {@code outZip}. */
    public static void zipRuntime(File packDir, File outZip) throws IOException {
        List<String> files = regenerateFiles(packDir);
        File parent = outZip.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outZip.toPath()))) {
            addEntry(zos, packDir, PACK_MANIFEST);
            for (String rel : files) {
                addEntry(zos, packDir, rel);
            }
        }
    }

    private static void addEntry(ZipOutputStream zos, File packDir, String rel) throws IOException {
        zos.putNextEntry(new ZipEntry(rel));
        // Files.copy(Path, OutputStream) writes the bytes and does NOT close the stream.
        Files.copy(new File(packDir, rel).toPath(), zos);
        zos.closeEntry();
    }

    /**
     * Reads a scalar top-level field (e.g. {@code id}, {@code version}, {@code bundled}) from a
     * pack.yaml, ignoring a trailing {@code # comment} and surrounding quotes.
     */
    public static String field(File packYaml, String key) throws IOException {
        Pattern p = Pattern.compile("^" + Pattern.quote(key) + ":\\s*([^#]*?)\\s*(#.*)?$");
        for (String line : Files.readAllLines(packYaml.toPath(), StandardCharsets.UTF_8)) {
            Matcher m = p.matcher(line);
            if (m.matches() && !m.group(1).isEmpty()) {
                return m.group(1).replaceAll("^[\"']|[\"']$", "");
            }
        }
        return null;
    }
}
