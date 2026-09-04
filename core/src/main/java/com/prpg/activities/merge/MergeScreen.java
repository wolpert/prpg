package com.prpg.activities.merge;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.prpg.activities.BaseActivityScreen;
import com.prpg.activities.OnCompleteApplier;
import com.prpg.activities.merge.config.MergeDefinition;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.screens.ScreenNavigator;
import com.prpg.ui.ColorTextures;
import com.prpg.ui.Fonts;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Full-screen merge activity: drag a tile onto another; if their pair is a ladder recipe they
 * combine, otherwise the tile moves (empty target) or snaps back. Win when {@code win.count} of
 * {@code win.produce} exist. Pure logic in {@link MergeBoard}.
 */
@Singleton
public class MergeScreen extends BaseActivityScreen {

    public static final String TYPE = "merge";

    private static final float SWATCH_SCALE = 46f / 54f;
    private static final float WIN_PAUSE_TIME = 1.2f;

    private Group boardGroup;
    private Group[][] tiles;
    private Label titleLabel;
    private Label progressLabel;

    private MergeDefinition definition;
    private MergeBoard board;
    private final Map<String, MergeDefinition.LadderEntry> entryById = new HashMap<>();

    private Group grabbed;
    private int grabbedX;
    private int grabbedY;
    private boolean won;

    @Inject
    public MergeScreen(SpriteBatch batch, Skin skin, Fonts fonts, ColorTextures colorTextures,
                       ConfigLoader configLoader, ContentResolver content, OnCompleteApplier onComplete,
                       Provider<ScreenNavigator> nav) {
        super(batch, skin, fonts, colorTextures, configLoader, content, onComplete, nav);
    }

    @Override
    public void launch(String activityId) {
        definition = loadDefinition(TYPE, MergeDefinition.class, activityId);

        entryById.clear();
        Map<String, String> recipes = new HashMap<>();
        for (MergeDefinition.LadderEntry e : definition.ladder) {
            entryById.put(e.id, e);
            if (e.from != null && e.from.size() == 2) {
                recipes.put(MergeBoard.pairKey(e.from.get(0), e.from.get(1)), e.id);
            }
        }

        board = new MergeBoard(Math.max(1, definition.width), Math.max(1, definition.height), recipes);
        placeStartingInventory();

        grabbed = null;
        won = false;
        pendingDialogue = null;
    }

    @Override
    public Class<? extends Screen> screenKey() {
        return MergeScreen.class;
    }

    @Override
    protected float pieceScale() { return SWATCH_SCALE; }

    // The footer hint is a long, wrapping string, so reserve more vertical room than the default.
    @Override
    protected float reservedHeight() { return 220f + safeTopPadding(); }

    @Override
    protected int boardWidth() { return board == null ? 0 : board.width(); }

    @Override
    protected int boardHeight() { return board == null ? 0 : board.height(); }

    private void placeStartingInventory() {
        if (definition.starting_inventory == null) return;
        int idx = 0;
        int cells = board.width() * board.height();
        for (Map.Entry<String, Integer> e : definition.starting_inventory.entrySet()) {
            for (int n = 0; n < e.getValue() && idx < cells; n++, idx++) {
                int x = idx % board.width();
                int y = idx / board.width();
                board.set(x, y, e.getKey());
            }
        }
    }

    @Override
    public void show() {
        stage.clear();
        computeCellSize();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(safeTopPadding());

        titleLabel = new Label(definition.title, new Label.LabelStyle(fonts.dialogueSpeaker(), null));
        root.add(titleLabel).padBottom(6).row();

        progressLabel = new Label("", new Label.LabelStyle(fonts.dialogueBody(), null));
        root.add(progressLabel).padBottom(12).row();

        boardGroup = new Group();
        boardGroup.setSize(board.width() * cellSize(), board.height() * cellSize());
        root.add(boardGroup).size(board.width() * cellSize(), board.height() * cellSize()).row();

        Label hint = new Label(definition.hint + "  [Esc to leave]", new Label.LabelStyle(fonts.small(), null));
        hint.setWrap(true);
        hint.setAlignment(Align.center);
        root.add(hint).width(board.width() * cellSize()).padTop(14).row();

        stage.addActor(root);

        rebuildTiles();
        updateProgress();

        installInput();
    }

    private void rebuildTiles() {
        boardGroup.clearChildren();
        tiles = new Group[board.width()][board.height()];

        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                Image bg = new Image(new TextureRegionDrawable(colorTextures.swatch("1A1C18", cellSize())));
                bg.setBounds(cellX(x), cellY(y), cellSize(), cellSize());
                boardGroup.addActor(bg);
            }
        }

        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                String id = board.get(x, y);
                if (id != null) {
                    Group tile = makeTile(id);
                    tile.setPosition(cellX(x) + (cellSize() - pieceSize()) / 2f,
                            cellY(y) + (cellSize() - pieceSize()) / 2f);
                    tiles[x][y] = tile;
                    boardGroup.addActor(tile);
                }
            }
        }

        addDragLayer();
    }

    private Group makeTile(String id) {
        int swatch = pieceSize();
        Group g = new Group();
        g.setSize(swatch, swatch);

        MergeDefinition.LadderEntry entry = entryById.get(id);
        String color = entry != null && entry.color != null ? entry.color : "AAAAAA";
        int tier = entry != null ? entry.tier : 0;

        Image swatchImg = new Image(new TextureRegionDrawable(colorTextures.swatch(color, swatch)));
        swatchImg.setBounds(0, 0, swatch, swatch);
        g.addActor(swatchImg);

        Label rankLabel = new Label(String.valueOf(tier),
                new Label.LabelStyle(fonts.dialogueSpeaker(), null));
        rankLabel.setColor(0.1f, 0.1f, 0.1f, 1f);
        rankLabel.setAlignment(Align.center);
        rankLabel.setBounds(0, 0, swatch, swatch);
        g.addActor(rankLabel);

        return g;
    }

    private void addDragLayer() {
        Actor dragLayer = new Actor();
        dragLayer.setBounds(0, 0, boardGroup.getWidth(), boardGroup.getHeight());
        dragLayer.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float lx, float ly, int pointer, int button) {
                if (won) return false;
                int[] cell = cellAt(lx, ly);
                if (cell == null || board.get(cell[0], cell[1]) == null) return false;
                grabbedX = cell[0];
                grabbedY = cell[1];
                grabbed = tiles[grabbedX][grabbedY];
                if (grabbed != null) grabbed.toFront();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float lx, float ly, int pointer) {
                if (grabbed != null) {
                    grabbed.setPosition(lx - pieceSize() / 2f, ly - pieceSize() / 2f);
                }
            }

            @Override
            public void touchUp(InputEvent event, float lx, float ly, int pointer, int button) {
                if (grabbed == null) return;
                int[] cell = cellAt(lx, ly);
                if (cell != null) {
                    board.resolveDrag(grabbedX, grabbedY, cell[0], cell[1]);
                }
                grabbed = null;
                rebuildTiles();
                updateProgress();
                checkWin();
            }
        });
        boardGroup.addActor(dragLayer);
    }

    private void checkWin() {
        if (won || definition.win == null) return;
        if (board.count(definition.win.produce) < definition.win.count) return;
        won = true;
        pendingDialogue = onComplete.apply(activityId, definition.on_complete);
        titleLabel.setText("Done.");
        startWinPause(WIN_PAUSE_TIME);
    }

    private void updateProgress() {
        if (definition.win == null) return;
        MergeDefinition.LadderEntry goal = entryById.get(definition.win.produce);
        int tier = goal != null ? goal.tier : 0;
        progressLabel.setText("Goal: rank-" + tier + " " + definition.win.produce + "  ("
                + board.count(definition.win.produce) + " / " + definition.win.count + ")");
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.07f, 0.07f, 0.09f, 1f);
        tickWinPause(delta);
        stage.act(delta);
        stage.draw();
    }
}
