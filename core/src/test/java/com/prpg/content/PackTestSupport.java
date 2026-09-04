package com.prpg.content;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

/** Locates the repo's {@code packs/} tree from whatever working dir the test runner uses. */
public final class PackTestSupport {

    private PackTestSupport() {}

    public static File packsRoot() {
        for (String candidate : new String[]{"packs", "../packs", "../../packs"}) {
            File f = new File(candidate);
            if (new File(f, "baseline").isDirectory()) return f;
        }
        fail("could not locate packs/ from " + new File(".").getAbsolutePath());
        return null; // unreachable
    }

    /** Every pack directory (those with a pack.yaml). */
    public static File[] packDirs() {
        File[] dirs = packsRoot().listFiles(f -> f.isDirectory() && new File(f, "pack.yaml").isFile());
        return dirs != null ? dirs : new File[0];
    }

    /** True if a logical content path exists under some pack (a pure-JVM stand-in for ContentResolver). */
    public static boolean contentExists(String relPath) {
        if (relPath == null) return false;
        for (File pack : packDirs()) {
            if (new File(pack, relPath).exists()) return true;
        }
        return false;
    }
}
