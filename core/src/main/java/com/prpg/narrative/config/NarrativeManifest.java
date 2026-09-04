package com.prpg.narrative.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The act catalog: which acts exist, in what order, how they are delivered, and how they are gated.
 * Loaded from {@code narrative/manifest.yaml} (baseline pack) by {@code ActContentRegistry}, then
 * merged with every mounted pack's {@code provides:}.
 *
 * <p>The catalog lists <b>every</b> act, even ones not installed, so the base game knows a DLC act
 * <i>should</i> exist and can offer to download/restore it rather than dead-ending. A mounted pack
 * that isn't in the catalog still appears (its {@code provides:} is folded in at runtime).
 *
 * <p>Plain public fields, populated by SnakeYAML field access; lives in a leaf {@code config}
 * package for the Android R8 keep rule.
 */
public class NarrativeManifest {

    public List<ActEntry> acts = new ArrayList<>();

    /**
     * DLC groupings, the <i>sales</i> units. A pack is what the build produces (one self-contained
     * act); a DLC bundles one or more packs sold together. Declarative today: {@code DefaultEntitlement}
     * grants anything mounted regardless.
     */
    public List<Dlc> dlc = new ArrayList<>();

    public static class ActEntry {
        public String id;
        public String title;
        public int order;
        /** {@code BUNDLED} (ships in the app) or {@code DLC} (delivered as a pack zip). */
        public String source;
        /** {@code FREE} or {@code PAID}. */
        public String entitlement;
        /** Optional flag whose setting marks this act's narrative gate as crossed. */
        public String gate_flag;
        /** Map the act opens on; normally supplied by the pack's {@code provides:} rather than here. */
        public String entry_map;
        /** Named spawn on {@link #entry_map}; the map's first spawn when null. */
        public String entry_spawn;

        public boolean isFree() {
            return "FREE".equalsIgnoreCase(entitlement);
        }
    }

    public static class Dlc {
        public String id;
        /** {@code FREE} or {@code PAID}. */
        public String entitlement;
        /** Ids of the content packs this DLC delivers. */
        public List<String> packs = new ArrayList<>();
    }
}
