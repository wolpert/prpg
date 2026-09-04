package com.prpg.assets;

/**
 * Describes a single asset that can be queued through libGDX's
 * {@link com.badlogic.gdx.assets.AssetManager}.
 *
 * <p>Any enum (or class) implementing this contract can be contributed into the
 * {@code Set<LoadableAsset>} consumed by
 * {@link com.prpg.screens.LoadingScreen} via Dagger multibindings,
 * letting the scaffold and demo (sample) split their asset lists without the loading
 * screen having to know which is which.
 */
public interface LoadableAsset {

    /** Asset path, relative to {@code assets/} (must also appear in {@code assets.txt}). */
    String path();

    /** Loader type the {@link com.badlogic.gdx.assets.AssetManager} should use. */
    Class<?> type();
}
