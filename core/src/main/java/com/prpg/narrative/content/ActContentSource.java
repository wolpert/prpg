package com.prpg.narrative.content;

/**
 * Resolves an act's compiled Ink JSON, wherever it lives — bundled in the base install or delivered
 * as DLC. An interface so the runtime composes bundled + DLC sources, and tests inject an in-memory
 * source to run a DLC act in isolation (the DLC-seam test). See
 * {@code docs/content-guide.md} (§1, §12.2).
 */
public interface ActContentSource {

    /** Whether this source can supply the act with the given stable id (i.e. it is installed). */
    boolean has(String actId);

    /** The act's compiled Ink JSON. Callers must check {@link #has} first; behaviour otherwise is impl-defined. */
    String read(String actId);
}
