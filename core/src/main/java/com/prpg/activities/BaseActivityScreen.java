package com.prpg.activities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.screens.ScreenNavigator;
import com.prpg.ui.ColorTextures;
import com.prpg.ui.Fonts;
import com.prpg.util.Log;
import java.io.Reader;
import javax.inject.Provider;

/**
 * Shared scaffolding for Scene2D-driven <b>full-screen</b> activities (match-3, merge, ...): the
 * density-scaled stage, YAML definition loading, the ESC/BACK-to-world listener, fit-to-screen grid
 * math ({@link GridLayout}), the win-pause-then-return timer, and the standard lifecycle. Subclasses
 * supply the board dimensions and per-frame logic.
 */
public abstract class BaseActivityScreen implements FullScreenActivity {

    protected final SpriteBatch batch;
    protected final Skin skin;
    protected final Fonts fonts;
    protected final ColorTextures colorTextures;
    protected final ConfigLoader configLoader;
    protected final ContentResolver content;
    protected final OnCompleteApplier onComplete;
    protected final Provider<ScreenNavigator> nav;

    protected final Stage stage;
    protected final GridLayout grid = new GridLayout();

    /** Dialogue queued by a just-completed activity's on_complete block; consumed by the world on return. */
    protected String pendingDialogue;

    /** The id of the activity currently loaded (set by {@link #loadDefinition}); used in log messages. */
    protected String activityId;

    private float winPauseTimer;
    private boolean winPausing;

    protected BaseActivityScreen(SpriteBatch batch, Skin skin, Fonts fonts, ColorTextures colorTextures,
                                 ConfigLoader configLoader, ContentResolver content,
                                 OnCompleteApplier onComplete, Provider<ScreenNavigator> nav) {
        this.batch = batch;
        this.skin = skin;
        this.fonts = fonts;
        this.colorTextures = colorTextures;
        this.configLoader = configLoader;
        this.content = content;
        this.onComplete = onComplete;
        this.nav = nav;

        ScreenViewport viewport = new ScreenViewport();
        viewport.setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage = new Stage(viewport, batch);
    }

    /** Baseline top buffer (dp) so the header never hugs the screen edge, even with no cutout. */
    protected static final float SAFE_TOP_BASE = 24f;

    /** Top padding (dp) that clears the device's display cutout plus the baseline buffer. */
    protected float safeTopPadding() {
        float density = Math.max(1f, Gdx.graphics.getDensity());
        return SAFE_TOP_BASE + Gdx.graphics.getSafeInsetTop() / density;
    }

    /** Loads an activity definition POJO from {@code activities/<type>/<activityId>.yaml}. */
    protected <T> T loadDefinition(String type, Class<T> defType, String activityId) {
        this.activityId = activityId;
        String path = "activities/" + type + "/" + activityId + ".yaml";
        try (Reader reader = content.resolve(path).reader()) {
            T def = configLoader.load(defType, reader);
            if (def == null) throw new IllegalStateException("empty definition");
            return def;
        } catch (Exception e) {
            Log.error("BaseActivityScreen", "failed to load activity definition " + path, e);
            throw new RuntimeException("failed to load activity " + path, e);
        }
    }

    @Override
    public String consumePendingDialogue() {
        String d = pendingDialogue;
        pendingDialogue = null;
        return d;
    }

    /** Routes input to the stage and wires ESC/BACK to return to the world. Call from {@code show()}. */
    protected void installInput() {
        winPausing = false;
        Gdx.input.setInputProcessor(stage);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    leaveToWorld();
                    return true;
                }
                return false;
            }
        });
    }

    /** Returns to the world screen via the shared registry route (no concrete-screen coupling). */
    protected void leaveToWorld() {
        nav.get().goToWorld();
    }

    /** Begins a pause that returns to the world once {@code seconds} elapse. */
    protected void startWinPause(float seconds) {
        winPausing = true;
        winPauseTimer = seconds;
    }

    /** Advances the win-pause timer; returns to the world when it expires. Call once per frame. */
    protected boolean tickWinPause(float delta) {
        if (!winPausing) return false;
        winPauseTimer -= delta;
        if (winPauseTimer <= 0f) {
            winPausing = false;
            leaveToWorld();
        }
        return true;
    }

    // --- grid sizing ------------------------------------------------------------------------------

    protected int cellSize() { return grid.cellSize(); }
    protected abstract int boardWidth();
    protected abstract int boardHeight();

    /** Piece/tile art size as a fraction of a cell; each subclass's art is tuned to this ratio. */
    protected abstract float pieceScale();

    /** The fitted size (dp) of the art drawn inside a cell. */
    protected int pieceSize() {
        return Math.round(grid.cellSize() * pieceScale());
    }

    /** Side margin (dp) kept around the board so it never hugs the screen edge. */
    protected float horizontalMargin() { return 24f; }

    /** Vertical space (dp) reserved for the header (title/progress) + footer (hint), incl. the cutout. */
    protected float reservedHeight() { return 170f + safeTopPadding(); }

    protected int minCellSize() { return 24; }
    protected int maxCellSize() { return 96; }

    /** Recomputes the cell size so the board fills the available screen. Call at the top of {@code show()}. */
    protected void computeCellSize() {
        float density = Math.max(0.0001f, Gdx.graphics.getDensity());
        float unitsPerPixel = 1f / density;
        float worldW = Gdx.graphics.getWidth() * unitsPerPixel;
        float worldH = Gdx.graphics.getHeight() * unitsPerPixel;
        grid.setBoard(boardWidth(), boardHeight());
        grid.fit(worldW - 2f * horizontalMargin(), worldH - reservedHeight(), minCellSize(), maxCellSize());
    }

    protected float cellX(int x) { return grid.cellX(x); }
    protected float cellY(int y) { return grid.cellY(y); }
    protected int[] cellAt(float lx, float ly) { return grid.cellAt(lx, ly); }

    @Override
    public void resize(int width, int height) {
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage.getViewport().update(width, height, true);
        // Refit the board to the new screen size by rebuilding the UI at the new cell size. Guarded
        // so a resize before the activity is launched (no board yet) is a no-op.
        if (boardWidth() > 0 && boardHeight() > 0) {
            show();
        }
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        // Release the input processor and the Android BACK capture so a non-world screen doesn't
        // keep swallowing keys after we leave.
        Gdx.input.setInputProcessor(null);
        Gdx.input.setCatchKey(Input.Keys.BACK, false);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
