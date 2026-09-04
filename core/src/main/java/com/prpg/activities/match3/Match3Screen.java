package com.prpg.activities.match3;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.prpg.activities.BaseActivityScreen;
import com.prpg.activities.OnCompleteApplier;
import com.prpg.activities.match3.config.Match3Definition;
import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.screens.ScreenNavigator;
import com.prpg.ui.ColorTextures;
import com.prpg.ui.Fonts;
import com.prpg.util.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Full-screen match-3: tap two neighbours (or press-and-slide) to swap; matches clear, pieces fall,
 * the board refills, cascades resolve; win when every {@code win.per_type} target is met. Pure board
 * logic lives in {@link Match3Board}; this class is the Scene2D presentation.
 */
@Singleton
public class Match3Screen extends BaseActivityScreen {

    public static final String TYPE = "match3";

    private static final float PIECE_SCALE = 30f / 38f;
    private static final float SWAP_TIME = 0.16f;
    private static final float CLEAR_TIME = 0.18f;
    private static final float FALL_TIME = 0.22f;
    private static final float WIN_PAUSE_TIME = 1.1f;
    // Hard cap on chained cascades resolved from a single swap, so a pathological board can't loop
    // the CLEARING -> FALLING -> REFILLING -> CLEARING state machine forever.
    private static final int MAX_CASCADES = 64;
    // Distinct placeholder colors keyed by piece-type index.
    private static final String[] PALETTE = {
            "8FA37E", "E8E4D0", "7E6B9E", "A9482E", "C9A24B", "6FA8C9"
    };

    private enum Phase { IDLE, SWAP_COMMIT, SWAP_REVERT, CLEARING, FALLING, REFILLING }

    private Group boardGroup;
    private Image selectionHighlight;
    private Image[][] pieces; // [x][y] actor for each cell, null if empty
    private Label progressLabel;
    private Label titleLabel;

    private Match3Definition definition;
    private List<Match3Definition.Piece> pieceDefs;
    private Map<String, Integer> nameToIndex;
    // Lazily-loaded gameplay atlases, keyed by logical path; paths that fail to load are remembered
    // in failedAtlases so a broken reference falls back to a swatch without retrying every piece.
    private final Map<String, TextureAtlas> atlasCache = new HashMap<>();
    private final Set<String> failedAtlases = new HashSet<>();
    private Match3Board board;
    private int selX = -1;
    private int selY = -1;
    private boolean won;
    private Phase phase = Phase.IDLE;
    private float timer;
    private int cascades;

    // Remembered swap for the revert animation.
    private int swapAX;
    private int swapAY;
    private int swapBX;
    private int swapBY;

    @Inject
    public Match3Screen(SpriteBatch batch, Skin skin, Fonts fonts, ColorTextures colorTextures,
                        ConfigLoader configLoader, ContentResolver content, OnCompleteApplier onComplete,
                        Provider<ScreenNavigator> nav) {
        super(batch, skin, fonts, colorTextures, configLoader, content, onComplete, nav);
    }

    @Override
    public void launch(String activityId) {
        definition = loadDefinition(TYPE, Match3Definition.class, activityId);
        pieceDefs = definition.pieceList();
        nameToIndex = new HashMap<>();
        for (int i = 0; i < pieceDefs.size(); i++) {
            nameToIndex.put(pieceDefs.get(i).name(), i);
        }
        int types = pieceDefs.isEmpty() ? 3 : pieceDefs.size();
        board = new Match3Board(definition.board.width, definition.board.height, types,
                System.nanoTime());
        selX = -1;
        selY = -1;
        won = false;
        pendingDialogue = null;
        phase = Phase.IDLE;
        timer = 0f;
        cascades = 0;
    }

    @Override
    public Class<? extends Screen> screenKey() {
        return Match3Screen.class;
    }

    @Override
    protected float pieceScale() { return PIECE_SCALE; }

    @Override
    protected int boardWidth() { return board == null ? 0 : board.width(); }

    @Override
    protected int boardHeight() { return board == null ? 0 : board.height(); }

    @Override
    public void show() {
        stage.clear();
        computeCellSize();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(safeTopPadding());

        titleLabel = new Label(definition.title, new Label.LabelStyle(fonts.dialogueSpeaker(), null));
        root.add(titleLabel).padBottom(8).row();

        progressLabel = new Label("", new Label.LabelStyle(fonts.dialogueBody(), null));
        root.add(progressLabel).padBottom(12).row();

        boardGroup = new Group();
        boardGroup.setSize(board.width() * cellSize(), board.height() * cellSize());
        root.add(boardGroup).size(board.width() * cellSize(), board.height() * cellSize()).row();

        Label hint = new Label("Tap two neighbours to swap.  [Esc to leave]",
                new Label.LabelStyle(fonts.small(), null));
        root.add(hint).padTop(14).row();

        stage.addActor(root);

        buildBoardActors();
        updateProgress();

        installInput();
    }

    private void buildBoardActors() {
        boardGroup.clearChildren();
        pieces = new Image[board.width()][board.height()];

        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                Image bg = new Image(new TextureRegionDrawable(colorTextures.swatch("16181A", cellSize())));
                bg.setBounds(cellX(x), cellY(y), cellSize(), cellSize());
                boardGroup.addActor(bg);
            }
        }

        selectionHighlight = new Image(new TextureRegionDrawable(
                colorTextures.swatch("FFE9A0", cellSize())));
        selectionHighlight.getColor().a = 0.35f;
        selectionHighlight.setSize(cellSize(), cellSize());
        selectionHighlight.setVisible(false);
        boardGroup.addActor(selectionHighlight);

        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                int type = board.get(x, y);
                if (type != Match3Board.EMPTY) {
                    pieces[x][y] = makePiece(x, y, type);
                }
            }
        }

        // Input layer on top: a transparent overlay sized to the board that maps pointer input to
        // cells. Two ways to swap: tap a cell then tap an adjacent one, OR press a cell and slide.
        Actor inputLayer = new Actor();
        inputLayer.setBounds(0, 0, boardGroup.getWidth(), boardGroup.getHeight());
        inputLayer.addListener(new InputListener() {
            private int startX = -1;
            private int startY = -1;
            private float downLX;
            private float downLY;
            private boolean swappedThisGesture;

            @Override
            public boolean touchDown(InputEvent event, float lx, float ly, int pointer, int button) {
                if (won || phase != Phase.IDLE) return false;
                int[] cell = cellAt(lx, ly);
                if (cell == null) return false;
                startX = cell[0];
                startY = cell[1];
                downLX = lx;
                downLY = ly;
                swappedThisGesture = false;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float lx, float ly, int pointer) {
                if (swappedThisGesture || startX < 0 || won || phase != Phase.IDLE) return;
                float dx = lx - downLX;
                float dy = ly - downLY;
                if (Math.max(Math.abs(dx), Math.abs(dy)) < cellSize() * 0.45f) return;
                int tx = startX;
                int ty = startY;
                if (Math.abs(dx) > Math.abs(dy)) {
                    tx += dx > 0 ? 1 : -1;
                } else {
                    // Stage y is up but board row 0 is the top row.
                    ty += dy > 0 ? -1 : 1;
                }
                if (tx < 0 || ty < 0 || tx >= board.width() || ty >= board.height()) return;
                clearSelection();
                beginSwap(startX, startY, tx, ty);
                swappedThisGesture = true;
            }

            @Override
            public void touchUp(InputEvent event, float lx, float ly, int pointer, int button) {
                if (!swappedThisGesture && startX >= 0) {
                    onCellTapped(startX, startY);
                }
                startX = -1;
                startY = -1;
                swappedThisGesture = false;
            }
        });
        boardGroup.addActor(inputLayer);
    }

    private Image makePiece(int x, int y, int type) {
        Image img = new Image(new TextureRegionDrawable(regionFor(type)));
        int piece = pieceSize();
        img.setSize(piece, piece);
        img.setOrigin(piece / 2f, piece / 2f);
        img.setPosition(pieceX(x), pieceY(y));
        // Pieces are purely visual; the tap layer handles all input.
        img.setTouchable(Touchable.disabled);
        boardGroup.addActor(img);
        return img;
    }

    private void onCellTapped(int x, int y) {
        if (won || phase != Phase.IDLE) return;

        if (selX < 0) {
            select(x, y);
            return;
        }
        if (selX == x && selY == y) {
            clearSelection();
            return;
        }
        if (board.areAdjacent(selX, selY, x, y)) {
            beginSwap(selX, selY, x, y);
        } else {
            select(x, y);
        }
    }

    private void beginSwap(int ax, int ay, int bx, int by) {
        clearSelection();
        swapAX = ax; swapAY = ay; swapBX = bx; swapBY = by;

        Image a = pieces[ax][ay];
        Image b = pieces[bx][by];
        if (a != null) a.addAction(Actions.moveTo(pieceX(bx), pieceY(by), SWAP_TIME, Interpolation.sine));
        if (b != null) b.addAction(Actions.moveTo(pieceX(ax), pieceY(ay), SWAP_TIME, Interpolation.sine));

        boolean matched = board.trySwap(ax, ay, bx, by);
        if (matched) {
            pieces[ax][ay] = b;
            pieces[bx][by] = a;
            cascades = 0;
            phase = Phase.SWAP_COMMIT;
        } else {
            phase = Phase.SWAP_REVERT;
        }
        timer = SWAP_TIME;
    }

    private void advance(float delta) {
        if (phase == Phase.IDLE) return;
        timer -= delta;
        if (timer > 0f) return;

        switch (phase) {
            case SWAP_COMMIT -> {
                phase = Phase.CLEARING;
                timer = 0f;
            }
            case SWAP_REVERT -> {
                Image a = pieces[swapAX][swapAY];
                Image b = pieces[swapBX][swapBY];
                if (a != null) {
                    a.addAction(Actions.moveTo(pieceX(swapAX), pieceY(swapAY), SWAP_TIME, Interpolation.sine));
                }
                if (b != null) {
                    b.addAction(Actions.moveTo(pieceX(swapBX), pieceY(swapBY), SWAP_TIME, Interpolation.sine));
                }
                phase = Phase.IDLE;
                timer = SWAP_TIME;
            }
            case CLEARING -> {
                Set<Long> matches = cascades >= MAX_CASCADES ? Set.of() : board.findMatches();
                if (matches.isEmpty()) {
                    if (isWinReached()) {
                        won = true;
                        pendingDialogue = onComplete.apply(activityId, definition.on_complete);
                        titleLabel.setText("Cleared.");
                        startWinPause(WIN_PAUSE_TIME);
                    }
                    phase = Phase.IDLE;
                } else {
                    cascades++;
                    board.clearMatches(matches);
                    for (long packed : matches) {
                        int x = (int) (packed >> 32);
                        int y = (int) (packed & 0xffffffffL);
                        Image a = pieces[x][y];
                        pieces[x][y] = null;
                        if (a != null) {
                            a.addAction(Actions.sequence(
                                    Actions.parallel(
                                            Actions.fadeOut(CLEAR_TIME),
                                            Actions.scaleTo(0.1f, 0.1f, CLEAR_TIME, Interpolation.sineIn)),
                                    Actions.removeActor()));
                        }
                    }
                    updateProgress();
                    phase = Phase.FALLING;
                    timer = CLEAR_TIME;
                }
            }
            case FALLING -> {
                List<int[]> moves = board.applyGravity();
                for (int[] m : moves) {
                    int x = m[0];
                    int fromY = m[1];
                    int toY = m[2];
                    Image a = pieces[x][fromY];
                    pieces[x][toY] = a;
                    pieces[x][fromY] = null;
                    if (a != null) {
                        a.addAction(Actions.moveTo(pieceX(x), pieceY(toY), FALL_TIME, Interpolation.pow2In));
                    }
                }
                phase = Phase.REFILLING;
                timer = moves.isEmpty() ? 0f : FALL_TIME;
            }
            case REFILLING -> {
                List<int[]> spawns = board.refill();
                float dropFrom = boardGroup.getHeight();
                for (int[] s : spawns) {
                    int x = s[0];
                    int y = s[1];
                    int type = s[2];
                    Image a = makePiece(x, y, type);
                    a.setPosition(pieceX(x), pieceY(y) + dropFrom);
                    pieces[x][y] = a;
                    a.addAction(Actions.moveTo(pieceX(x), pieceY(y), FALL_TIME, Interpolation.pow2In));
                }
                phase = Phase.CLEARING;
                timer = spawns.isEmpty() ? 0f : FALL_TIME;
            }
            default -> { }
        }
    }

    private void select(int x, int y) {
        selX = x;
        selY = y;
        selectionHighlight.setPosition(cellX(x), cellY(y));
        selectionHighlight.setVisible(true);
    }

    private void clearSelection() {
        selX = -1;
        selY = -1;
        if (selectionHighlight != null) selectionHighlight.setVisible(false);
    }

    private boolean isWinReached() {
        if (definition.win == null || definition.win.per_type == null
                || definition.win.per_type.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Integer> e : definition.win.per_type.entrySet()) {
            Integer idx = nameToIndex.get(e.getKey());
            int target = e.getValue() != null ? e.getValue() : 0;
            if (idx == null || board.clearedOf(idx) < target) {
                return false;
            }
        }
        return true;
    }

    private void updateProgress() {
        if (definition.win == null || definition.win.per_type == null) {
            progressLabel.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Match3Definition.Piece p : pieceDefs) {
            Integer target = definition.win.per_type.get(p.name());
            if (target == null) {
                continue;
            }
            int idx = nameToIndex.get(p.name());
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(p.name()).append(' ')
                    .append(Math.min(board.clearedOf(idx), target)).append('/').append(target);
        }
        progressLabel.setText(sb.toString());
    }

    /**
     * The texture for a piece type: a real atlas region when the piece declares {@code atlas} +
     * {@code region} and it loads, otherwise a color swatch (the piece's {@code color} override, or
     * the built-in palette by index).
     */
    private TextureRegion regionFor(int type) {
        Match3Definition.Piece p = (pieceDefs != null && type >= 0 && type < pieceDefs.size())
                ? pieceDefs.get(type) : null;
        if (p != null && p.atlas() != null && p.region() != null) {
            TextureRegion region = atlasRegion(p.atlas(), p.region());
            if (region != null) {
                return region;
            }
        }
        String color = (p != null && p.color() != null) ? p.color() : PALETTE[type % PALETTE.length];
        return colorTextures.circle(color, pieceSize());
    }

    /** Resolves a region from a (lazily loaded, cached) gameplay atlas; null on any failure. */
    private TextureRegion atlasRegion(String atlasPath, String region) {
        if (failedAtlases.contains(atlasPath)) {
            return null;
        }
        TextureAtlas atlas = atlasCache.get(atlasPath);
        if (atlas == null) {
            try {
                atlas = new TextureAtlas(content.resolve(atlasPath));
            } catch (Exception e) {
                Log.error("Match3Screen", "failed to load atlas " + atlasPath + "; using swatch", e);
                failedAtlases.add(atlasPath);
                return null;
            }
            atlasCache.put(atlasPath, atlas);
        }
        TextureRegion found = atlas.findRegion(region);
        if (found == null) {
            Log.debug("Match3Screen", "atlas " + atlasPath + " has no region '" + region + "'; using swatch");
        }
        return found;
    }

    private float pieceX(int x) {
        return cellX(x) + (cellSize() - pieceSize()) / 2f;
    }

    private float pieceY(int y) {
        return cellY(y) + (cellSize() - pieceSize()) / 2f;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.08f, 0.09f, 0.07f, 1f);
        advance(delta);
        tickWinPause(delta);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        for (TextureAtlas atlas : atlasCache.values()) {
            if (atlas != null) {
                atlas.dispose();
            }
        }
        atlasCache.clear();
        failedAtlases.clear();
        super.dispose();
    }
}
