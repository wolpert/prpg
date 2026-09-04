package com.prpg.activities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.ui.ColorTextures;
import com.prpg.ui.Fonts;
import com.prpg.util.Log;
import java.io.Reader;

/**
 * Shared scaffolding for <b>popup</b> activities: a centred panel over the world with a title, a
 * content area the subclass fills, a hint line, and a Close button; ESC/BACK closes; a win-pause
 * timer closes automatically after a solve. Grid math ({@link GridLayout}) is fitted to the panel's
 * budget so board-style popups adapt to the screen like the full-screen ones do.
 *
 * <p>The stage's root is hidden while closed, so it is not hit-testable and never eats world taps;
 * the world adds {@link #getStage()} to its input multiplexer once and forgets about it.
 */
public abstract class BasePopupActivity implements PopupActivity {

    protected final Skin skin;
    protected final Fonts fonts;
    protected final ColorTextures colorTextures;
    protected final ConfigLoader configLoader;
    protected final ContentResolver content;
    protected final OnCompleteApplier onComplete;

    protected final Stage stage;
    protected final GridLayout grid = new GridLayout();

    private final Table root;
    private final Table panel;
    private final Label titleLabel;
    private final Table body;
    private final Label hintLabel;

    protected String pendingDialogue;
    protected String activityId;

    private boolean open;
    private float winPauseTimer;
    private boolean winPausing;

    protected BasePopupActivity(SpriteBatch batch, Skin skin, Fonts fonts, ColorTextures colorTextures,
                                ConfigLoader configLoader, ContentResolver content,
                                OnCompleteApplier onComplete) {
        this.skin = skin;
        this.fonts = fonts;
        this.colorTextures = colorTextures;
        this.configLoader = configLoader;
        this.content = content;
        this.onComplete = onComplete;

        ScreenViewport viewport = new ScreenViewport();
        viewport.setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage = new Stage(viewport, batch);

        root = new Table();
        root.setFillParent(true);
        root.center();
        // A dim veil behind the panel; also absorbs stray taps so they never reach world steering.
        root.setBackground(new TextureRegionDrawable(colorTextures.rect("000000", 8, 8)));
        root.getColor().a = 0.55f;

        panel = new Table(skin);
        panel.setBackground(skin.newDrawable("white", 0.09f, 0.09f, 0.12f, 1f));
        panel.pad(14);
        panel.getColor().a = 1f;

        titleLabel = new Label("", new Label.LabelStyle(fonts.dialogueSpeaker(), null));
        panel.add(titleLabel).left().padBottom(8).row();

        body = new Table();
        panel.add(body).row();

        hintLabel = new Label("", new Label.LabelStyle(fonts.small(), null));
        hintLabel.setWrap(true);
        hintLabel.setAlignment(Align.center);
        panel.add(hintLabel).width(220).padTop(10).row();

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });
        panel.add(closeButton).width(120).height(36).padTop(10).row();

        root.add(panel);
        root.setVisible(false);
        stage.addActor(root);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (open && (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK)) {
                    close();
                    return true;
                }
                return false;
            }
        });
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
            Log.error("BasePopupActivity", "failed to load activity definition " + path, e);
            throw new RuntimeException("failed to load activity " + path, e);
        }
    }

    // --- subclass hooks -------------------------------------------------------------------------

    /** Builds the popup's content into {@code body} (cleared beforehand). Called on open and resize. */
    protected abstract void buildBody(Table body);

    protected abstract int boardWidth();
    protected abstract int boardHeight();

    /** Largest cell size (dp) a subclass wants; the fit shrinks it on small screens. */
    protected int maxCellSize() { return 56; }
    protected int minCellSize() { return 20; }

    protected void setTitle(String title) { titleLabel.setText(title == null ? "" : title); }
    protected void setHint(String hint) { hintLabel.setText(hint == null ? "" : hint); }

    protected int cellSize() { return grid.cellSize(); }
    protected float cellX(int x) { return grid.cellX(x); }
    protected float cellY(int y) { return grid.cellY(y); }
    protected int[] cellAt(float lx, float ly) { return grid.cellAt(lx, ly); }

    /** Fits the grid into roughly two thirds of the screen, leaving room for the panel chrome. */
    protected void computeCellSize() {
        float density = Math.max(0.0001f, Gdx.graphics.getDensity());
        float worldW = Gdx.graphics.getWidth() / density;
        float worldH = Gdx.graphics.getHeight() / density;
        grid.setBoard(boardWidth(), boardHeight());
        grid.fit(worldW - 80f, worldH * 0.6f, minCellSize(), maxCellSize());
    }

    /** Begins a pause that closes the popup once {@code seconds} elapse. */
    protected void startWinPause(float seconds) {
        winPausing = true;
        winPauseTimer = seconds;
    }

    // --- PopupActivity ---------------------------------------------------------------------------

    @Override
    public void open() {
        open = true;
        winPausing = false;
        rebuild();
        root.setVisible(true);
        root.setTouchable(Touchable.enabled);
        stage.setKeyboardFocus(root);
    }

    private void rebuild() {
        computeCellSize();
        body.clearChildren();
        buildBody(body);
        panel.invalidateHierarchy();
        panel.pack();
    }

    @Override
    public void close() {
        open = false;
        winPausing = false;
        root.setVisible(false);
        root.setTouchable(Touchable.disabled);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void update(float delta) {
        if (!open) return;
        if (winPausing) {
            winPauseTimer -= delta;
            if (winPauseTimer <= 0f) {
                close();
                return;
            }
        }
        stage.act(delta);
    }

    @Override
    public void draw() {
        if (!open) return;
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage.getViewport().update(width, height, true);
        if (open) rebuild();
    }

    @Override
    public Stage getStage() {
        return stage;
    }

    @Override
    public String consumePendingDialogue() {
        String d = pendingDialogue;
        pendingDialogue = null;
        return d;
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
