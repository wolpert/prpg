package com.prpg.content.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-pack manifest ({@code pack.yaml} at a content pack's root): the self-describing contract
 * that lets a pack be dropped in and discovered with no central registration. Read at mount time by
 * {@code PackMounter}/{@code PackRegistry}, and at build time by the Gradle pack tasks.
 *
 * <p>Plain public fields, populated by SnakeYAML field access (see {@code ConfigLoader}). Lives in a
 * leaf {@code config} package so the Android R8 rule {@code -keep class **.config.** { *; }} protects
 * it from reflection stripping.
 */
public class PackManifest {

    /** Manifest schema version (for forward-compatible parsing). */
    public int schemaVersion = 1;

    /** Stable pack id; also the pack's directory name under the content root. */
    public String id;

    /** {@code baseline} (shared assets), {@code act}, or {@code epilogue}. */
    public String kind;

    /** Content version; drives re-extraction when a newer copy is bundled/delivered. */
    public int version = 1;

    /**
     * Whether this pack ships inside the app (staged by the build) rather than as a DLC zip. Read by
     * the Gradle {@code stageBundledPacks} task; the runtime treats bundled and mounted packs alike.
     */
    public boolean bundled;

    /** Whether this pack references shared assets from the baseline pack. */
    public boolean requiresBaseline = true;

    /** Optional minimum baseline content version this pack needs (e.g. {@code ">=1"}); null = any. */
    public String baselineVersion;

    /** Narrative entries this pack contributes to the act catalog (merged by the registry). */
    public List<Provided> provides = new ArrayList<>();

    /** Generated list of every content file in this pack, relative to the pack root. */
    public List<String> files = new ArrayList<>();

    /** Flags this pack declares (merged into the global flag namespace). */
    public List<String> flags = new ArrayList<>();

    /** Flags this pack consumes but does not declare; validated against the catalog. */
    public List<String> requiresFlags = new ArrayList<>();

    /**
     * A narrative act/epilogue this pack provides. Mirrors {@code NarrativeManifest.ActEntry}. An act
     * owns its own maps, so it also names the map + spawn the player lands on when the act begins.
     */
    public static class Provided {
        public String id;
        public String title;
        public int order;
        /** Optional flag whose setting marks this act's narrative gate as crossed. */
        public String gateFlag;
        /** Map id (a TMX base name in this pack) the act opens on. */
        public String entryMap;
        /** Named {@code spawn} object on {@link #entryMap}; the first spawn when null. */
        public String entrySpawn;
    }
}
