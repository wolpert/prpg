package com.prpg.narrative.content;

import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.content.PackRegistry;
import com.prpg.content.config.PackManifest;
import com.prpg.narrative.config.NarrativeManifest;
import com.prpg.narrative.config.NarrativeManifest.ActEntry;
import com.prpg.util.Log;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The act spine, derived from data: the catalog ({@code narrative/manifest.yaml}) merged with each
 * mounted pack's {@code provides:}. Answers "which acts exist, in what order, where does each begin,
 * and is its content installed". There is no Java enum of acts; adding an act is a pack, not a
 * code change.
 *
 * <p>The manifest is read through the {@link ContentResolver} content seam, but {@link #useManifest}
 * lets headless tests install a catalog without GL.
 */
@Singleton
public class ActContentRegistry {

    static final String MANIFEST_PATH = "narrative/manifest.yaml";

    private final ConfigLoader configLoader;
    private final ContentResolver content;
    private final PackRegistry packs;
    private final ActContentSource source;

    private Map<String, ActEntry> entries;

    @Inject
    public ActContentRegistry(ConfigLoader configLoader, ContentResolver content, PackRegistry packs,
                              ActContentSource source) {
        this.configLoader = configLoader;
        this.content = content;
        this.packs = packs;
        this.source = source;
    }

    private void ensureLoaded() {
        if (entries != null) return;
        if (!content.exists(MANIFEST_PATH)) {
            Log.info("ActContentRegistry", "no " + MANIFEST_PATH + "; catalog built from mounted packs only");
            useManifest(null);
            return;
        }
        try (Reader reader = content.resolve(MANIFEST_PATH).reader()) {
            useManifest(configLoader.load(NarrativeManifest.class, reader));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load " + MANIFEST_PATH, e);
        }
    }

    /**
     * Test seam: install a catalog directly (avoids needing libGDX files in unit tests). Mounted-pack
     * {@code provides:} entries are folded in here too, so the test seam matches the production path.
     */
    public void useManifest(NarrativeManifest manifest) {
        Map<String, ActEntry> map = new LinkedHashMap<>();
        if (manifest != null && manifest.acts != null) {
            for (ActEntry e : manifest.acts) {
                if (e != null && e.id != null) {
                    map.put(e.id, e);
                } else {
                    Log.error("ActContentRegistry", "skipping manifest act entry with null id");
                }
            }
        }
        this.entries = map;
        mergePackProvides();
    }

    /**
     * Folds each mounted pack's {@code provides:} entries into the catalog. An act new to the catalog
     * is added (self-declaring pack, no catalog edit needed); an act the catalog already advertises
     * keeps its catalog metadata but picks up the fields only the pack knows: the entry map/spawn,
     * and a title/gate flag the catalog left blank.
     */
    private void mergePackProvides() {
        for (PackManifest manifest : packs.manifests()) {
            if (manifest == null || manifest.provides == null) continue;
            for (PackManifest.Provided p : manifest.provides) {
                if (p == null || p.id == null) continue;
                ActEntry existing = entries.get(p.id);
                if (existing == null) {
                    entries.put(p.id, fromProvided(p));
                    Log.debug("ActContentRegistry", "folded pack-provided act '" + p.id + "' into catalog");
                } else {
                    if (existing.entry_map == null) existing.entry_map = p.entryMap;
                    if (existing.entry_spawn == null) existing.entry_spawn = p.entrySpawn;
                    if (existing.title == null) existing.title = p.title;
                    if (existing.gate_flag == null) existing.gate_flag = p.gateFlag;
                    if (existing.order == 0) existing.order = p.order;
                }
            }
        }
    }

    private static ActEntry fromProvided(PackManifest.Provided p) {
        ActEntry e = new ActEntry();
        e.id = p.id;
        e.title = p.title;
        e.order = p.order;
        e.gate_flag = p.gateFlag;
        e.entry_map = p.entryMap;
        e.entry_spawn = p.entrySpawn;
        // Self-declared (not in the bundled catalog): treat as deliverable, paid-tier by default.
        e.source = "DLC";
        e.entitlement = "PAID";
        return e;
    }

    /** Every declared act, sorted by {@code order}. */
    public List<ActEntry> allActs() {
        ensureLoaded();
        List<ActEntry> out = new ArrayList<>(entries.values());
        out.sort(Comparator.comparingInt((ActEntry e) -> e.order).thenComparing(e -> e.id));
        return out;
    }

    public ActEntry entry(String actId) {
        ensureLoaded();
        return actId == null ? null : entries.get(actId);
    }

    /** True if the act is declared in the catalog (or provided by a mounted pack). */
    public boolean isDeclared(String actId) {
        return entry(actId) != null;
    }

    /** True if the act's compiled content is present (bundled, or DLC pack delivered). */
    public boolean isInstalled(String actId) {
        return actId != null && source.has(actId);
    }

    /** The lowest-ordered act: where a new game begins. Null when nothing is declared. */
    public String firstActId() {
        List<ActEntry> acts = allActs();
        return acts.isEmpty() ? null : acts.get(0).id;
    }

    /** The act after {@code actId} in order, or null at the end of the spine (or if unknown). */
    public String nextActId(String actId) {
        List<ActEntry> acts = allActs();
        for (int i = 0; i < acts.size() - 1; i++) {
            if (acts.get(i).id.equals(actId)) return acts.get(i + 1).id;
        }
        return null;
    }
}
