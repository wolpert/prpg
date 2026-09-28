package com.prpg.world;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.prpg.activities.ActivityLauncher;
import com.prpg.config.GameConfig;
import com.prpg.debug.DebugOverlay;
import com.prpg.ecs.component.AnimationComponent;
import com.prpg.ecs.component.InteractableComponent;
import com.prpg.ecs.component.OrientationComponent;
import com.prpg.ecs.component.PlayerComponent;
import com.prpg.ecs.component.PortalComponent;
import com.prpg.ecs.component.PositionComponent;
import com.prpg.ecs.component.TriggerComponent;
import com.prpg.ecs.system.InteractionSystem;
import com.prpg.ecs.system.PlayerMovementSystem;
import com.prpg.ecs.system.TriggerSystem;
import com.prpg.input.PointerInput;
import com.prpg.items.Inventory;
import com.prpg.items.InventoryOverlay;
import com.prpg.lifecycle.LifecycleGate;
import com.prpg.narrative.ActProgression;
import com.prpg.narrative.NarrativeOverlay;
import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.StoryVariables;
import com.prpg.narrative.config.NarrativeManifest.ActEntry;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.quests.QuestLogOverlay;
import com.prpg.save.SaveData;
import com.prpg.save.SaveManager;
import com.prpg.screens.ScreenNavigator;
import com.prpg.ui.ColorTextures;
import com.prpg.ui.Fonts;
import com.prpg.util.Log;
import com.prpg.world.command.PlayCommand;
import com.prpg.world.scene.SceneDirector;
import com.prpg.world.stage.StageDirector;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * The gameplay screen: renders the active TMX map through an {@link ExtendViewport}, runs the Ashley
 * engine, and hosts the narrative/inventory/quest overlays, popup activities, the HUD and a fade veil
 * for cutscenes. Implements {@link Screen} directly (gameplay is ECS-driven, not Scene2D).
 *
 * <p>The story drives it. Interacting with a staged actor or thing, or walking into a staged zone,
 * runs a knot; the knot's command lines ({@code >>> play}, {@code >>> go}, ...) are carried out while
 * the story waits; whatever the story changed is re-staged. The act decides where the world begins:
 * {@link #newGame()} places the player at the first act's {@code # entry:}, and when
 * {@link ActProgression} advances the act the screen moves the player to the new act's entry. Either
 * way, the act's optional {@code act_start} knot then runs.
 */
@Singleton
public class WorldScreen implements Screen {

    private static final float COLLISION_OFFSET_X = WorldEntityFactory.COLLISION_OFFSET_X;
    private static final float COLLISION_OFFSET_Y = WorldEntityFactory.COLLISION_OFFSET_Y;

    /** The optional knot an act runs as its opening scene each time the act is entered. */
    public static final String ACT_START_KNOT = "act_start";

    private static final ComponentMapper<PositionComponent> POSITIONS =
            ComponentMapper.getFor(PositionComponent.class);

    private final Engine engine;
    private final SpriteBatch batch;
    private final MapManager mapManager;
    private final LifecycleGate lifecycleGate;
    private final DebugOverlay debugOverlay;
    private final PlayerMovementSystem playerMovementSystem;
    private final NarrativeRunner narrativeRunner;
    private final NarrativeOverlay narrativeOverlay;
    private final NarrativeState narrativeState;
    private final ActProgression progression;
    private final ActContentRegistry acts;
    private final StoryVariables variables;
    private final Inventory inventory;
    private final InventoryOverlay inventoryOverlay;
    private final ActivityLauncher activityLauncher;
    private final TriggerSystem triggerSystem;
    private final WorldTravel travel;
    private final QuestLogOverlay questLogOverlay;
    private final SaveManager saveManager;
    private final GameClock gameClock;
    private final ColorTextures colorTextures;
    private final Fonts fonts;
    private final WorldEntityFactory entityFactory;
    private final StageDirector stageDirector;
    private final PlayerSprites playerSprites;
    private final SceneDirector sceneDirector;
    private final Provider<ScreenNavigator> nav;
    private final PointerInput pointerInput;

    private final OrthographicCamera camera;
    private final ExtendViewport viewport;
    private final float sideGutterDp;

    // Touch/mouse gesture tracking: a press that never drags past TAP_SLOP (screen px) is a tap
    // (interact); otherwise it steers the player toward the pointer.
    private static final float TAP_SLOP = 12f;
    private static final float POINTER_DEADZONE = 6f;
    private final Vector2 pointerScratch = new Vector2();
    private int touchDownX;
    private int touchDownY;
    private boolean touchDragged;

    private OrthogonalTiledMapRenderer mapRenderer;
    private Entity playerEntity;
    private ImmutableArray<Entity> playerFamily;

    /** Spawn to use on the next build when there is no pending load (act entry / new game). */
    private String pendingSpawnName;

    /** An act was just entered: run its act_start knot once the world is built. */
    private boolean pendingActStart;
    private Image fadeImage;

    // When set, the next buildWorld places the player at this exact sprite position (a loaded save).
    private boolean pendingLoad;
    private float pendingLoadX;
    private float pendingLoadY;
    private String pendingLoadFacing;

    /** The act the current world was built for; a mismatch with the narrative state means "act changed". */
    private String builtActId;

    private Label toastLabel;
    private float toastTimer;

    private Stage hudStage;
    private Label stateLabel;
    private int lastStateVersion = -1;
    private int stagedRevision = -1;
    private Table hudTable;
    private Table controls;
    private Table toastTable;

    private int[] bgLayers = new int[0];
    private int[] fgLayers = new int[0];

    @Inject
    public WorldScreen(Engine engine,
                       SpriteBatch batch,
                       GameConfig config,
                       MapManager mapManager,
                       LifecycleGate lifecycleGate,
                       DebugOverlay debugOverlay,
                       InteractionSystem interactionSystem,
                       PlayerMovementSystem playerMovementSystem,
                       NarrativeRunner narrativeRunner,
                       NarrativeOverlay narrativeOverlay,
                       NarrativeState narrativeState,
                       ActProgression progression,
                       ActContentRegistry acts,
                       StoryVariables variables,
                       Inventory inventory,
                       InventoryOverlay inventoryOverlay,
                       ActivityLauncher activityLauncher,
                       TriggerSystem triggerSystem,
                       WorldTravel travel,
                       QuestLogOverlay questLogOverlay,
                       SaveManager saveManager,
                       GameClock gameClock,
                       ColorTextures colorTextures,
                       Fonts fonts,
                       WorldEntityFactory entityFactory,
                       PlayerSprites playerSprites,
                       SceneDirector sceneDirector,
                       StageDirector stageDirector,
                       PointerInput pointerInput,
                       Provider<ScreenNavigator> nav) {
        this.engine = engine;
        this.batch = batch;
        this.mapManager = mapManager;
        this.lifecycleGate = lifecycleGate;
        this.debugOverlay = debugOverlay;
        this.playerMovementSystem = playerMovementSystem;
        this.narrativeRunner = narrativeRunner;
        this.narrativeOverlay = narrativeOverlay;
        this.narrativeState = narrativeState;
        this.progression = progression;
        this.acts = acts;
        this.variables = variables;
        this.inventory = inventory;
        this.inventoryOverlay = inventoryOverlay;
        this.activityLauncher = activityLauncher;
        this.triggerSystem = triggerSystem;
        this.travel = travel;
        this.questLogOverlay = questLogOverlay;
        this.saveManager = saveManager;
        this.gameClock = gameClock;
        this.colorTextures = colorTextures;
        this.fonts = fonts;
        this.entityFactory = entityFactory;
        this.playerSprites = playerSprites;
        this.sceneDirector = sceneDirector;
        this.stageDirector = stageDirector;
        this.pointerInput = pointerInput;
        this.nav = nav;

        GameConfig.WorldConfig world = config.world != null ? config.world : new GameConfig.WorldConfig();
        sideGutterDp = world.sideGutterDp;
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(world.viewWidth, world.viewHeight, camera);

        ScreenViewport hudViewport = new ScreenViewport();
        hudViewport.setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        hudStage = new Stage(hudViewport, batch);
        stateLabel = new Label("", new Label.LabelStyle(fonts.small(), null));
        stateLabel.setColor(1f, 1f, 0.5f, 1f);
        stateLabel.setTouchable(Touchable.disabled);
        hudTable = new Table();
        hudTable.setFillParent(true);
        hudTable.setTouchable(Touchable.childrenOnly);
        hudTable.top().left();
        hudTable.add(stateLabel).left();
        hudStage.addActor(hudTable);

        // On-screen Items / Quests buttons: the touch equivalents of the I/TAB and Q keys.
        controls = new Table();
        controls.setFillParent(true);
        controls.setTouchable(Touchable.childrenOnly);
        controls.top().right();
        controls.add(hudButton("Items", this::toggleInventory)).padRight(6);
        controls.add(hudButton("Quests", this::toggleQuests));
        hudStage.addActor(controls);

        // Full-screen black veil for cutscene fades; alpha driven by the scene director.
        fadeImage = new Image(new TextureRegionDrawable(colorTextures.rect("000000", 8, 8)));
        fadeImage.setFillParent(true);
        fadeImage.getColor().a = 0f;
        fadeImage.setVisible(false);
        fadeImage.setTouchable(Touchable.disabled);
        hudStage.addActor(fadeImage);

        toastLabel = new Label("", new Label.LabelStyle(fonts.dialogueBody(), null));
        toastLabel.setColor(0.85f, 0.95f, 0.85f, 1f);
        toastLabel.setVisible(false);
        toastLabel.setTouchable(Touchable.disabled);
        toastTable = new Table();
        toastTable.setFillParent(true);
        toastTable.setTouchable(Touchable.childrenOnly);
        toastTable.top();
        toastTable.add(toastLabel);
        hudStage.addActor(toastTable);

        applyHudSafeInsets();

        // Everything the player can interact with was staged by the story, and runs one of its knots.
        interactionSystem.addListener(target -> {
            if (worldInputBlocked()) return;
            InteractableComponent inter = target.getComponent(InteractableComponent.class);
            if (inter != null && inter.knot != null) startDialogue(inter.knot);
        });

        triggerSystem.addListener(zone -> {
            TriggerComponent t = zone.getComponent(TriggerComponent.class);
            if (t != null) {
                // A staged zone: walking in runs its knot. The story decides whether it fires again
                // (usually by setting a variable that makes stage() stop placing it).
                if (t.knot != null && !narrativeRunner.isActive()) startDialogue(t.knot);
                return;
            }
            PortalComponent p = zone.getComponent(PortalComponent.class);
            if (p != null) {
                if (stageDirector.isPortalLocked(p.name)) {
                    Log.debug("WorldScreen", "portal '" + p.name + "' is locked by the story");
                    return;
                }
                travel.request(p.targetMap, p.targetSpawn); // carried out at the top of the next frame
            }
        });
    }

    /** Diverts into the knot {@code knot} of the current act's story, freezing the player meanwhile. */
    private void startDialogue(String knot) {
        String actId = narrativeState.getCurrentActId();
        playerMovementSystem.setFrozen(true);
        boolean started = narrativeRunner.start(actId, knot, () -> playerMovementSystem.setFrozen(false));
        if (!started) {
            playerMovementSystem.setFrozen(false);
            Log.debug("WorldScreen", "dialogue '" + knot + "' in act '" + actId
                    + "' did not start (act not loaded or knot unreachable)");
        }
    }

    private void savePlayerPosition() {
        if (playerEntity == null) return;
        PositionComponent pos = playerEntity.getComponent(PositionComponent.class);
        if (pos != null) {
            mapManager.savePlayerPosition(mapManager.getCurrentMapId(),
                    pos.x + COLLISION_OFFSET_X, pos.y + COLLISION_OFFSET_Y);
        }
    }

    /** Resets all session state for a fresh playthrough. Call before navigating to this screen. */
    public void newGame() {
        variables.clearRun(); // keep persistent meta_* profile state across playthroughs
        inventory.clear();
        gameClock.reset();
        narrativeRunner.clear();  // drop cached Ink stories so no state leaks across playthroughs
        mapManager.clearSavedPositions();
        progression.beginNewGame(); // first act: unlocked and current
        stageDirector.invalidate();
        pendingLoad = false;
        travel.clear();
        pointAtActEntry(narrativeState.getCurrentActId());
        pendingActStart = true;
    }

    /** Loads the save (applies inventory/flags/triggers/narrative) and queues the player's exact position. */
    public void prepareContinue() {
        SaveData data = saveManager.load();
        if (data == null) {
            newGame();
            return;
        }
        mapManager.clearSavedPositions();
        if (data.mapId != null) {
            mapManager.setCurrentMapId(data.mapId);
            pendingLoadX = data.playerX;
            pendingLoadY = data.playerY;
            pendingLoadFacing = data.facing;
            pendingLoad = true;
        } else {
            pendingLoad = false;
            pointAtActEntry(narrativeState.getCurrentActId());
        }
        builtActId = narrativeState.getCurrentActId();
        travel.clear();
        pendingActStart = false;
    }

    /** Sets the current map/spawn to an act's declared entry point (its pack.yaml provides:). */
    private void pointAtActEntry(String actId) {
        builtActId = actId;
        ActEntry entry = acts.entry(actId);
        if (entry == null || entry.entry_map == null) {
            Log.error("WorldScreen", "act '" + actId + "' declares no entry map; staying on '"
                    + mapManager.getCurrentMapId() + "'");
            if (mapManager.getCurrentMapId() == null && entry != null) {
                Log.error("WorldScreen", "no map at all; add '# entry: <map> <spawn>' to the top of the act's Ink");
            }
            return;
        }
        mapManager.setCurrentMapId(entry.entry_map);
        pendingSpawnName = entry.entry_spawn;
    }

    private void saveGame() {
        if (playerEntity == null) return;
        PositionComponent pos = playerEntity.getComponent(PositionComponent.class);
        OrientationComponent orient = playerEntity.getComponent(OrientationComponent.class);
        if (pos == null || orient == null) return;
        boolean saved = saveManager.save(mapManager.getCurrentMapId(), pos.x, pos.y, orient.direction.name());
        toast(saved ? "Saved." : "Save failed.");
    }

    private void toast(String text) {
        toastLabel.setText(text);
        toastLabel.setVisible(true);
        toastTimer = 1.5f;
    }

    @Override
    public void show() {
        buildWorld(pendingSpawnName);
        pendingSpawnName = null;

        inventoryOverlay.close();
        questLogOverlay.close();

        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(narrativeOverlay.getStage());
        for (Stage popup : activityLauncher.popupStages()) mux.addProcessor(popup);
        mux.addProcessor(inventoryOverlay.getStage());
        mux.addProcessor(questLogOverlay.getStage());
        mux.addProcessor(hudStage);
        mux.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (sceneDirector.isActive()) return true; // swallow input during a cutscene
                if (keycode == Input.Keys.F5) {
                    if (!narrativeRunner.isActive()) saveGame();
                    return true;
                }
                if (keycode == Input.Keys.I || keycode == Input.Keys.TAB) {
                    toggleInventory();
                    return true;
                }
                if (keycode == Input.Keys.Q) {
                    toggleQuests();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    if (activityLauncher.isPopupOpen()) {
                        activityLauncher.closePopup();
                    } else if (inventoryOverlay.isOpen()) {
                        inventoryOverlay.close();
                    } else if (questLogOverlay.isOpen()) {
                        questLogOverlay.close();
                    } else {
                        if (!narrativeRunner.isActive()) saveGame();
                        nav.get().goToMainMenu();
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (worldInputBlocked()) return false;
                touchDownX = screenX;
                touchDownY = screenY;
                touchDragged = false;
                steerToward(screenX, screenY);
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (worldInputBlocked()) return false;
                if (Math.abs(screenX - touchDownX) > TAP_SLOP || Math.abs(screenY - touchDownY) > TAP_SLOP) {
                    touchDragged = true;
                }
                steerToward(screenX, screenY);
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                pointerInput.clearMovement();
                if (!touchDragged && !worldInputBlocked()) {
                    pointerInput.requestInteract();
                }
                return true;
            }
        });
        Gdx.input.setInputProcessor(mux);
    }

    /** Pads the top-anchored HUD below the display cutout (camera notch/hole). */
    private void applyHudSafeInsets() {
        float density = Math.max(1f, Gdx.graphics.getDensity());
        float top = 8f + Gdx.graphics.getSafeInsetTop() / density;
        hudTable.pad(top, 8f, 8f, 8f);
        controls.pad(top, 8f, 8f, 8f);
        toastTable.padTop(20f + top);
        hudTable.invalidateHierarchy();
        controls.invalidateHierarchy();
        toastTable.invalidateHierarchy();
    }

    private TextButton hudButton(String text, Runnable onClick) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = fonts.small();
        style.up = new TextureRegionDrawable(colorTextures.rect("000000", 8, 8));
        style.down = new TextureRegionDrawable(colorTextures.rect("444444", 8, 8));
        TextButton button = new TextButton(text, style);
        button.pad(6, 12, 6, 12);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClick.run();
            }
        });
        return button;
    }

    private void toggleInventory() {
        if (!narrativeRunner.isActive() && !sceneDirector.isActive() && !questLogOverlay.isOpen()
                && !activityLauncher.isPopupOpen()) {
            inventoryOverlay.toggle();
        }
    }

    private void toggleQuests() {
        if (!narrativeRunner.isActive() && !sceneDirector.isActive() && !inventoryOverlay.isOpen()
                && !activityLauncher.isPopupOpen()) {
            questLogOverlay.toggle();
        }
    }

    /** World steering/interaction is suppressed while something else owns input, or there's no player. */
    private boolean worldInputBlocked() {
        return playerEntity == null
                || sceneDirector.isActive()
                || narrativeRunner.isActive()
                || inventoryOverlay.isOpen()
                || questLogOverlay.isOpen()
                || activityLauncher.isPopupOpen();
    }

    private void steerToward(int screenX, int screenY) {
        if (playerEntity == null) {
            pointerInput.clearMovement();
            return;
        }
        pointerScratch.set(screenX, screenY);
        viewport.unproject(pointerScratch);
        PositionComponent pos = playerEntity.getComponent(PositionComponent.class);
        float dx = pointerScratch.x - (pos.x + playerSprites.frameWidth() / 2f);
        float dy = pointerScratch.y - (pos.y + playerSprites.frameHeight() / 2f);
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < POINTER_DEADZONE) {
            pointerInput.clearMovement();
            return;
        }
        pointerInput.setMovement(dx / len, dy / len);
    }

    /**
     * (Re)builds the active map and all its entities. {@code spawnName} names a spawn object to
     * place the player at (portals, act entry); when null, the saved or default spawn is used.
     */
    private void buildWorld(String spawnName) {
        engine.removeAllEntities();

        playerSprites.load();
        mapManager.loadMap(mapManager.getCurrentMapId());
        if (mapRenderer != null) mapRenderer.dispose();
        mapRenderer = new OrthogonalTiledMapRenderer(mapManager.getMap(), 1f, batch);

        float spriteX;
        float spriteY;
        OrientationComponent.Direction facing = OrientationComponent.Direction.DOWN;
        if (pendingLoad) {
            spriteX = pendingLoadX;
            spriteY = pendingLoadY;
            facing = parseFacing(pendingLoadFacing);
            pendingLoad = false;
        } else {
            Vector2 spawn = null;
            if (spawnName != null) {
                spawn = mapManager.getNamedSpawn(spawnName);
            }
            if (spawn == null) {
                spawn = mapManager.getSpawnPoint();
            }
            // Spawn objects mark the desired collision-box (feet) position; offset to the sprite.
            spriteX = spawn.x - COLLISION_OFFSET_X;
            spriteY = spawn.y - COLLISION_OFFSET_Y;
        }

        playerEntity = entityFactory.createPlayer(spriteX, spriteY, facing);
        entityFactory.spawnMapContent(playerEntity);
        stagedRevision = stageDirector.revision();
        builtActId = narrativeState.getCurrentActId();

        playerFamily = engine.getEntitiesFor(
                Family.all(PlayerComponent.class, PositionComponent.class).get());

        bgLayers = getLayerIndices("ground", "walls");
        fgLayers = getLayerIndices("overlay");

        triggerSystem.reset();
    }

    /** Re-stages the world when the story's stage() now says something different. */
    private void refreshStagedIfChanged() {
        int revision = stageDirector.revision();
        if (revision == stagedRevision) return;
        stagedRevision = revision;
        entityFactory.refreshStaged(playerEntity);
    }

    private void releaseGracedBlockers() {
        if (playerEntity == null) return;
        PositionComponent pos = playerEntity.getComponent(PositionComponent.class);
        if (pos == null) return;
        mapManager.updateBlockers(pos.x + COLLISION_OFFSET_X, pos.y + COLLISION_OFFSET_Y,
                WorldEntityFactory.COLLISION_W, WorldEntityFactory.COLLISION_H);
    }

    private static OrientationComponent.Direction parseFacing(String name) {
        if (name == null) return OrientationComponent.Direction.DOWN;
        try {
            return OrientationComponent.Direction.valueOf(name);
        } catch (IllegalArgumentException e) {
            Log.debug("WorldScreen", "unknown saved facing '" + name + "', defaulting to DOWN");
            return OrientationComponent.Direction.DOWN;
        }
    }

    /**
     * When the act changed (Ink called {@code advance_act()}), rebuild on the new act's entry map.
     * Waits for the conversation that advanced it to end so the last lines are still readable.
     */
    private void enterActIfChanged() {
        String current = narrativeState.getCurrentActId();
        if (current == null || current.equals(builtActId) || narrativeRunner.isActive()) return;
        Log.info("WorldScreen", "act changed to '" + current + "'; entering its map");
        mapManager.clearSavedPositions();
        pointAtActEntry(current);
        buildWorld(pendingSpawnName);
        pendingSpawnName = null;
        autosave();
        ActEntry entry = acts.entry(current);
        toast(entry != null && entry.title != null ? entry.title : current);
        pendingActStart = true;
    }

    /** Runs the current act's optional {@code act_start} knot (its opening scene), once per entry. */
    private void runActStartIfPending() {
        if (!pendingActStart || narrativeRunner.isActive()) return;
        pendingActStart = false;
        String act = narrativeState.getCurrentActId();
        if (narrativeRunner.hasKnot(act, ACT_START_KNOT)) {
            startDialogue(ACT_START_KNOT);
        }
    }

    /** Carries out a pending portal or {@code >>> go}: rebuild on the target map at the target spawn. */
    private void travelIfRequested() {
        if (!travel.isPending()) return;
        String map = travel.map();
        String spawnName = travel.spawn();
        travel.clear();
        mapManager.setCurrentMapId(map);
        buildWorld(spawnName);
        Log.debug("WorldScreen", "entered map '" + map + "' (spawn '" + spawnName + "')");
        // A move the story makes mid-conversation autosaves once the conversation is over instead.
        if (!narrativeRunner.isActive()) autosave();
    }

    /** A story that ended while faded out would leave the player in the dark; lift the veil. */
    private void liftAbandonedFade() {
        if (narrativeRunner.isActive() || sceneDirector.isActive() || sceneDirector.fadeAlpha() <= 0f) return;
        Log.info("WorldScreen", "a conversation ended with the screen faded out (no '>>> fade in'); lifting it");
        sceneDirector.setFade(0f);
    }

    private void autosave() {
        if (playerEntity == null) return;
        saveManager.saveAsync(mapManager.getCurrentMapId(),
                playerEntity.getComponent(PositionComponent.class).x,
                playerEntity.getComponent(PositionComponent.class).y,
                playerEntity.getComponent(OrientationComponent.class).direction.name());
    }

    @Override
    public void render(float delta) {
        if (!lifecycleGate.isAppActive()) delta = 0f;

        travelIfRequested();
        enterActIfChanged();
        runActStartIfPending();

        // A command the story is waiting on (an activity, a fade, a cutscene) advances here, and the
        // story resumes the moment it finishes.
        narrativeRunner.update(delta);
        liftAbandonedFade();

        refreshStagedIfChanged();
        releaseGracedBlockers();

        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        playerMovementSystem.setFrozen(worldInputBlocked());

        updatePlayerAnimation();
        updateCamera();

        // Re-assert the inset world viewport each frame: the HUD stage's full-screen viewport leaves
        // the glViewport spanning the whole window after its draw.
        viewport.apply(false);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        mapRenderer.setView(camera);

        if (bgLayers.length > 0) mapRenderer.render(bgLayers);

        engine.update(delta);

        if (fgLayers.length > 0) mapRenderer.render(fgLayers);

        sceneDirector.update(delta);
        fadeImage.setVisible(sceneDirector.fadeAlpha() > 0f);
        fadeImage.getColor().a = sceneDirector.fadeAlpha();

        narrativeOverlay.update(delta);
        narrativeOverlay.draw();

        activityLauncher.updatePopup(delta);
        activityLauncher.drawPopup();

        inventoryOverlay.update(delta);
        inventoryOverlay.draw();

        questLogOverlay.update(delta);
        questLogOverlay.draw();

        updateStateHud();
        if (toastTimer > 0f) {
            toastTimer -= delta;
            if (toastTimer <= 0f) toastLabel.setVisible(false);
        }
        hudStage.act(delta);
        hudStage.draw();

        debugOverlay.render(delta);
    }

    /** Debug HUD: the story variables that currently hold a truthy value. */
    private void updateStateHud() {
        if (variables.version() == lastStateVersion) return;
        lastStateVersion = variables.version();
        StringBuilder sb = new StringBuilder();
        for (var e : variables.all().entrySet()) {
            if (!variables.isTrue(e.getKey()) || PlayCommand.RESULT_VARIABLE.equals(e.getKey())) continue;
            sb.append("\n  ").append(e.getKey());
            if (!(e.getValue() instanceof Boolean)) sb.append(" = ").append(e.getValue());
        }
        stateLabel.setText(sb.length() == 0 ? "(story just begun)" : "Story:" + sb);
    }

    // Identity is the point: animation(dirIdx, moving) hands back one cached Animation per
    // (direction, moving) pair, so a different instance means the clip actually changed and the
    // elapsed timer has to restart. Animation has no equals(), so value comparison would be
    // identity anyway, just less honest about the intent.
    @SuppressWarnings("ReferenceEquality")
    private void updatePlayerAnimation() {
        if (playerEntity == null) return;

        OrientationComponent orient = playerEntity.getComponent(OrientationComponent.class);
        AnimationComponent anim = playerEntity.getComponent(AnimationComponent.class);
        if (orient == null || anim == null) return;

        int dirIdx = PlayerSprites.directionIndex(orient.direction);
        boolean moving = playerMovementSystem.isMoving();
        Animation<TextureRegion> target = playerSprites.animation(dirIdx, moving);

        if (anim.animation != target) {
            anim.animation = target;
            anim.elapsed = 0f;
        }
    }

    private void updateCamera() {
        if (playerFamily == null || playerFamily.size() == 0) return;

        Entity player = playerFamily.first();
        PositionComponent pos = POSITIONS.get(player);

        float targetX = pos.x + playerSprites.frameWidth() / 2f;
        float targetY = pos.y + playerSprites.frameHeight() / 2f;

        float halfW = viewport.getWorldWidth() / 2f;
        float halfH = viewport.getWorldHeight() / 2f;

        float mapW = mapManager.getMapWidthPx();
        float mapH = mapManager.getMapHeightPx();

        if (mapW > viewport.getWorldWidth()) {
            targetX = MathUtils.clamp(targetX, halfW, mapW - halfW);
        } else {
            targetX = mapW / 2f;
        }

        if (mapH > viewport.getWorldHeight()) {
            targetY = MathUtils.clamp(targetY, halfH, mapH - halfH);
        } else {
            targetY = mapH / 2f;
        }

        // Snap to whole world-units so texels don't straddle pixel boundaries and shimmer.
        camera.position.set(MathUtils.round(targetX), MathUtils.round(targetY), 0f);
    }

    private int[] getLayerIndices(String... names) {
        int count = 0;
        int[] tmp = new int[names.length];
        for (String name : names) {
            int idx = mapManager.getMap().getLayers().getIndex(name);
            if (idx >= 0) {
                tmp[count++] = idx;
            }
        }
        int[] result = new int[count];
        System.arraycopy(tmp, 0, result, 0, count);
        return result;
    }

    @Override
    public void resize(int width, int height) {
        int buf = Math.round(sideGutterDp * Gdx.graphics.getDensity());
        int innerW = Math.max(1, width - 2 * buf);
        viewport.update(innerW, height, false);
        viewport.setScreenBounds(buf, 0, innerW, height);
        viewport.apply(false);
        ((ScreenViewport) hudStage.getViewport()).setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        hudStage.getViewport().update(width, height, true);
        applyHudSafeInsets();
        narrativeOverlay.resize(width, height);
        inventoryOverlay.resize(width, height);
        questLogOverlay.resize(width, height);
        activityLauncher.resize(width, height);
        debugOverlay.resize(width, height);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        // A full-screen activity is about to take over; remember where the player stood so the
        // world rebuilds around them when it returns.
        savePlayerPosition();
        Gdx.input.setInputProcessor(null);
        Gdx.input.setCatchKey(Input.Keys.BACK, false);
    }

    @Override
    public void dispose() {
        playerSprites.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        mapManager.dispose();
        narrativeOverlay.dispose();
        inventoryOverlay.dispose();
        questLogOverlay.dispose();
        activityLauncher.dispose();
        debugOverlay.dispose();
        hudStage.dispose();
    }
}
