package com.prpg.world.stage.config;

/**
 * A character's identity, independent of where they stand. Loaded from {@code actors/<id>.yaml}
 * across every mounted pack (recurring cast lives in baseline; an act may add its own).
 *
 * <p>Deliberately positionless: an actor's location is per-act staging data (see
 * {@link StagingConfig}), so the same Rowan can appear in three acts at three places without three
 * definitions. Plain public fields, populated by SnakeYAML field access (see {@code ConfigLoader});
 * lives in a leaf {@code config} package so the Android R8 rule {@code -keep class **.config.** { *; }}
 * protects it from reflection stripping.
 */
public class ActorDef {

    /** Stable actor id — the handle staging entries and Ink externals reference. */
    public String id;

    /** Display name, or an {@code @key} resolved against {@code i18n/strings.properties}. */
    public String name;

    /** Optional {@code sprites/<sprite>.sprite.yaml} descriptor for real art. */
    public String sprite;

    /** Placeholder swatch colour, 6-digit hex with no {@code #} (e.g. {@code 5E8B7E}). */
    public String color;
}
