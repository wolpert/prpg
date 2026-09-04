package com.prpg.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.prpg.config.GameConfig;
import com.prpg.save.SaveManager;
import com.prpg.util.Log;
import com.prpg.world.WorldScreen;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class MainMenuScreen extends BaseScreen {

    private final Skin skin;
    private final SaveManager saveManager;
    private final TextButton continueButton;
    private boolean quitDialogOpen;

    @Inject
    public MainMenuScreen(SpriteBatch batch,
                          Skin skin,
                          GameConfig config,
                          SaveManager saveManager,
                          Provider<WorldScreen> worldScreen,
                          Provider<ScreenNavigator> nav) {
        super(batch);
        this.skin = skin;
        this.saveManager = saveManager;
        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(6).width(220).height(48);

        table.add(new Label(config.title, skin)).padBottom(20).row();

        continueButton = new TextButton("Continue", skin);
        continueButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Log.info("MainMenuScreen", "Continue selected, restoring save");
                worldScreen.get().prepareContinue();
                nav.get().goTo(WorldScreen.class);
            }
        });
        table.add(continueButton).row();

        TextButton newGame = new TextButton("New Game", skin);
        newGame.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Log.info("MainMenuScreen", "New Game selected");
                worldScreen.get().newGame();
                nav.get().goTo(WorldScreen.class);
            }
        });
        table.add(newGame).row();

        TextButton prefs = new TextButton("Preferences", skin);
        prefs.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                nav.get().goToPreferences();
            }
        });
        table.add(prefs).row();

        TextButton quit = new TextButton("Quit", skin);
        quit.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });
        table.add(quit).row();

        stage.addActor(table);
    }

    @Override
    public void show() {
        super.show();
        // Continue is only offered when a save exists.
        continueButton.setVisible(saveManager.hasSave());
    }

    @Override
    protected void onBack() {
        if (quitDialogOpen) return;
        quitDialogOpen = true;
        Dialog dialog = new Dialog("Quit?", skin) {
            @Override
            protected void result(Object object) {
                quitDialogOpen = false;
                if (Boolean.TRUE.equals(object)) {
                    Gdx.app.exit();
                }
            }
        };
        dialog.text("Are you sure you want to quit?");
        dialog.button("Yes", Boolean.TRUE);
        dialog.button("No", Boolean.FALSE);
        dialog.key(Input.Keys.ENTER, Boolean.TRUE);
        dialog.key(Input.Keys.ESCAPE, Boolean.FALSE);
        dialog.key(Input.Keys.BACK, Boolean.FALSE);
        dialog.show(stage);
    }
}
