package com.prpg.content.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The installed-pack registry ({@code packs.yaml} at the content root) — the enumeration source the
 * game reads at startup. It exists so the runtime never has to <em>list</em> the content directory
 * (Android can't enumerate arbitrary directories at runtime); {@code PackMounter} writes it as it
 * extracts packs, and {@code PackRegistry} reads it.
 *
 * <p>Plain public fields, populated by SnakeYAML field access (see {@code ConfigLoader}). Leaf
 * {@code config} package for the Android R8 keep rule.
 */
public class InstalledPacks {

    public List<Entry> packs = new ArrayList<>();

    public static class Entry {
        public String id;
        public int version;

        public Entry() {}

        public Entry(String id, int version) {
            this.id = id;
            this.version = version;
        }
    }
}
