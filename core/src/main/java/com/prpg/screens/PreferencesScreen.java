package com.prpg.screens;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.prpg.prefs.UserPreferences;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Screen-bound mirror of {@link UserPreferences}; toggles persist immediately on change. */
@Singleton
public class PreferencesScreen extends BaseScreen {

    private final UserPreferences prefs;
    private final Provider<ScreenNavigator> nav;
    private final CheckBox sound;
    private final CheckBox music;
    private final CheckBox debugOverlay;

    @Inject
    public PreferencesScreen(SpriteBatch batch, Skin skin, UserPreferences prefs, Provider<ScreenNavigator> nav) {
        super(batch);
        this.prefs = prefs;
        this.nav = nav;

        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(6);

        table.add(new Label("Preferences", skin)).padBottom(20).row();

        sound = new CheckBox(" Sound", skin);
        sound.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                prefs.setSoundEnabled(sound.isChecked());
            }
        });
        table.add(sound).left().row();

        music = new CheckBox(" Music", skin);
        music.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                prefs.setMusicEnabled(music.isChecked());
            }
        });
        table.add(music).left().row();

        debugOverlay = new CheckBox(" Debug Overlay", skin);
        debugOverlay.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                prefs.setDebugOverlayEnabled(debugOverlay.isChecked());
            }
        });
        table.add(debugOverlay).left().row();

        TextButton back = new TextButton("Back", skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                nav.get().goToMainMenu();
            }
        });
        table.add(back).width(150).height(40).padTop(20).row();

        stage.addActor(table);
    }

    @Override
    public void show() {
        super.show();
        // Sync widgets each entry — the user may have toggled values from another path,
        // and the @Singleton screen otherwise keeps stale checkbox state.
        sound.setChecked(prefs.isSoundEnabled());
        music.setChecked(prefs.isMusicEnabled());
        debugOverlay.setChecked(prefs.isDebugOverlayEnabled());
    }

    @Override
    protected void onBack() {
        nav.get().goToMainMenu();
    }
}
