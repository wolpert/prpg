package com.prpg.screens;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.prpg.assets.AssetManifest;
import com.prpg.assets.LoadableAsset;
import com.prpg.util.Log;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Drives initial asset loading via {@link AssetManager} and reflects progress with a Scene2D
 * {@link ProgressBar}. The transition out is gated on both the manager finishing AND a minimum
 * display time — instant loads otherwise flash by unreadably.
 *
 * <p>Asset queue is driven by a multi-bound {@code Set<LoadableAsset>} (scaffold + sample
 * enums contribute via Dagger {@code @ElementsIntoSet}); before queuing, each path is validated
 * against {@link AssetManifest} (the gradle-generated {@code assets.txt}) so a renamed or
 * deleted file fails fast with a precise error.
 *
 * <p>The {@link Skin} itself is loaded eagerly by Dagger (not via the manager) since this screen
 * needs it to render the bar — the chicken-and-egg is unavoidable for the screen that displays
 * loading progress for everything else.
 */
@Singleton
public class LoadingScreen extends BaseScreen {

    private static final float MIN_DURATION = 0.8f;

    private final Provider<ScreenNavigator> nav;
    private final AssetManager assets;
    private final AssetManifest manifest;
    private final Set<LoadableAsset> loadables;
    private final ProgressBar progressBar;
    private final Label percentLabel;
    private float elapsed;
    private boolean queued;

    @Inject
    public LoadingScreen(SpriteBatch batch,
                         Skin skin,
                         Provider<ScreenNavigator> nav,
                         AssetManager assets,
                         AssetManifest manifest,
                         Set<LoadableAsset> loadables) {
        super(batch);
        this.nav = nav;
        this.assets = assets;
        this.manifest = manifest;
        this.loadables = loadables;

        progressBar = new ProgressBar(0f, 1f, 0.01f, false, skin);
        progressBar.setAnimateDuration(0.1f);
        percentLabel = new Label("0%", skin);

        Table table = new Table();
        table.setFillParent(true);
        table.add(new Label("Loading...", skin)).padBottom(16).row();
        table.add(progressBar).width(360f).padBottom(8).row();
        table.add(percentLabel);
        stage.addActor(table);
    }

    @Override
    public void show() {
        super.show();
        elapsed = 0f;
        if (!queued) {
            for (LoadableAsset asset : loadables) {
                if (!manifest.contains(asset.path())) {
                    throw new GdxRuntimeException("Asset " + asset + " path '" + asset.path()
                            + "' is not in " + AssetManifest.MANIFEST_PATH
                            + " — file is missing or the manifest is stale (re-run :core:processResources).");
                }
                assets.load(asset.path(), asset.type());
            }
            queued = true;
        }
    }

    @Override
    public void render(float delta) {
        boolean done;
        try {
            done = assets.update();
        } catch (RuntimeException e) {
            Log.error("LoadingScreen", "asset load failed during AssetManager.update(); see cause", e);
            throw e; // fail fast — a missing/corrupt queued asset is a build/content bug
        }
        float progress = assets.getProgress();
        progressBar.setValue(progress);
        percentLabel.setText(((int) (progress * 100f)) + "%");

        super.render(delta);
        elapsed += delta;
        if (done && elapsed >= MIN_DURATION) {
            nav.get().goToMainMenu();
        }
    }
}
