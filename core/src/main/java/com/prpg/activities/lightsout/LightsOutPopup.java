package com.prpg.activities.lightsout;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.prpg.activities.BasePopupActivity;
import com.prpg.activities.OnCompleteApplier;
import com.prpg.activities.lightsout.config.LightsOutDefinition;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.ui.ColorTextures;
import com.prpg.ui.Fonts;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The sample <b>popup</b> activity: a small lights-out grid in a panel over the world. Shows the
 * minimum a popup needs: load a definition, build a body, react to taps, apply on_complete, and
 * close itself after a short pause.
 */
@Singleton
public class LightsOutPopup extends BasePopupActivity {

    public static final String TYPE = "lightsout";
    private static final float WIN_PAUSE_TIME = 0.9f;

    private LightsOutDefinition definition;
    private LightsOutBoard board;
    private Image[][] cells;
    private boolean won;

    @Inject
    public LightsOutPopup(SpriteBatch batch, Skin skin, Fonts fonts, ColorTextures colorTextures,
                          ConfigLoader configLoader, ContentResolver content, OnCompleteApplier onComplete) {
        super(batch, skin, fonts, colorTextures, configLoader, content, onComplete);
    }

    @Override
    public void launch(String activityId) {
        definition = loadDefinition(TYPE, LightsOutDefinition.class, activityId);
        board = new LightsOutBoard(definition.board.width, definition.board.height);
        board.scramble(definition.scramble, System.nanoTime());
        won = false;
        pendingDialogue = null;
        setTitle(definition.title);
        setHint(definition.hint);
    }

    @Override
    protected int boardWidth() { return board == null ? 0 : board.width(); }

    @Override
    protected int boardHeight() { return board == null ? 0 : board.height(); }

    @Override
    protected void buildBody(Table body) {
        if (board == null) return;
        Group boardGroup = new Group();
        boardGroup.setSize(grid.boardWidth(), grid.boardHeight());
        cells = new Image[board.width()][board.height()];
        int pad = Math.max(1, cellSize() / 10);
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                Image cell = new Image();
                cell.setBounds(cellX(x) + pad, cellY(y) + pad, cellSize() - 2 * pad, cellSize() - 2 * pad);
                cell.setTouchable(Touchable.disabled);
                cells[x][y] = cell;
                boardGroup.addActor(cell);
            }
        }
        refreshCells();

        Actor tapLayer = new Actor();
        tapLayer.setBounds(0, 0, boardGroup.getWidth(), boardGroup.getHeight());
        tapLayer.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float lx, float ly, int pointer, int button) {
                if (won) return false;
                int[] cell = cellAt(lx, ly);
                if (cell == null) return false;
                board.press(cell[0], cell[1]);
                refreshCells();
                if (board.isSolved()) {
                    won = true;
                    pendingDialogue = onComplete.apply(activityId, definition.on_complete);
                    setTitle("Opened.");
                    startWinPause(WIN_PAUSE_TIME);
                }
                return true;
            }
        });
        boardGroup.addActor(tapLayer);
        body.add(boardGroup).size(grid.boardWidth(), grid.boardHeight());
    }

    private void refreshCells() {
        int size = Math.max(4, cellSize() - 2 * Math.max(1, cellSize() / 10));
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                String color = board.isLit(x, y) ? definition.lit_color : definition.unlit_color;
                cells[x][y].setDrawable(new TextureRegionDrawable(colorTextures.swatch(color, size)));
            }
        }
    }
}
