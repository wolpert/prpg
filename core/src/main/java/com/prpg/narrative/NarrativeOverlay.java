package com.prpg.narrative;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.prpg.ui.Fonts;
import com.prpg.ui.Strings;
import com.prpg.ui.TextVars;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Scene2D overlay for Ink-driven narrative — the live-path replacement for {@code DialogueOverlay},
 * driven by {@link NarrativeRunner} instead of the YAML runner. Kept as a separate class (rather than
 * retrofitting the old overlay) so the YAML system stays intact and the swap is revertable.
 *
 * <p>Localization/variables still apply at the display boundary: {@code Strings.resolve} (i18n keys)
 * then {@code TextVars.apply} ({@code {day}}/{@code {investigator}}), exactly as before — so Ink lines
 * can carry the same keys/tokens the YAML lines did.
 */
@Singleton
public class NarrativeOverlay {

    private final NarrativeRunner runner;
    private final Skin skin;
    private final Fonts fonts;
    private final Strings strings;
    private final TextVars textVars;

    private final Stage stage;
    private final Table root;
    private final Label speakerLabel;
    private final Label textLabel;
    private final Table choiceTable;

    private int lastRenderedTurn = -1;

    @Inject
    public NarrativeOverlay(NarrativeRunner runner, Skin skin, Fonts fonts, Strings strings,
                            TextVars textVars, SpriteBatch batch) {
        this.runner = runner;
        this.skin = skin;
        this.fonts = fonts;
        this.strings = strings;
        this.textVars = textVars;

        ScreenViewport viewport = new ScreenViewport();
        viewport.setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage = new Stage(viewport, batch);

        root = new Table();
        root.setFillParent(true);
        root.bottom();
        // The full-screen root absorbs taps while a line shows (so a stray tap on the game area can't
        // steer the player), but only a tap on the dialogue box itself advances — the listener lives
        // on dialogueBox, not here. Touchability is toggled per-frame in update() so the overlay never
        // eats world taps when idle.
        root.setTouchable(Touchable.disabled);

        Table dialogueBox = new Table(skin);
        dialogueBox.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.85f));
        dialogueBox.pad(10);
        // Tapping the dialogue box over a no-choice line advances it (touch/mouse parity with
        // Space/E/Enter). Guarded by !hasChoices, so when choices are shown the box-level tap is a
        // no-op and the choice buttons handle their own clicks.
        dialogueBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runner.isActive() && !runner.hasChoices()) {
                    runner.advance();
                }
            }
        });

        speakerLabel = new Label("", new Label.LabelStyle(fonts.dialogueSpeaker(), null));
        dialogueBox.add(speakerLabel).left().padBottom(6).expandX().fillX().row();

        textLabel = new Label("", new Label.LabelStyle(fonts.dialogueBody(), null));
        textLabel.setWrap(true);
        textLabel.setAlignment(Align.topLeft);
        dialogueBox.add(textLabel).expandX().fillX().padBottom(8).row();

        choiceTable = new Table();
        dialogueBox.add(choiceTable).left().expandX().fillX().row();

        root.add(dialogueBox).expandX().fillX().pad(6);
        stage.addActor(root);
    }

    public boolean isActive() {
        return runner.isActive();
    }

    public Stage getStage() {
        return stage;
    }

    public void update(float delta) {
        // Capture touches only while a line is showing, so taps fall through to world steering otherwise.
        root.setTouchable(runner.isActive() ? Touchable.enabled : Touchable.disabled);
        if (!runner.isActive()) {
            lastRenderedTurn = -1;
            return;
        }

        if (runner.turn() != lastRenderedTurn) {
            lastRenderedTurn = runner.turn();
            render();
        }

        if (!runner.hasChoices() && (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.E)
                || Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
            runner.advance();
        }

        stage.act(delta);
    }

    public void draw() {
        if (!runner.isActive()) return;
        stage.draw();
    }

    public void resize(int width, int height) {
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage.getViewport().update(width, height, true);
    }

    private void render() {
        String speaker = runner.currentSpeaker();
        speakerLabel.setText(speaker == null || speaker.isEmpty()
                ? "" : textVars.apply(strings.resolve(speaker)));
        textLabel.setText(textVars.apply(strings.resolve(runner.currentText())));

        choiceTable.clearChildren();
        List<String> choices = runner.choiceTexts();
        if (choices.isEmpty()) {
            Label cont = new Label("[Tap or Space to continue]", new Label.LabelStyle(fonts.small(), null));
            choiceTable.add(cont).left();
        } else {
            for (int i = 0; i < choices.size(); i++) {
                final int idx = i;
                TextButton.TextButtonStyle baseStyle = skin.get(TextButton.TextButtonStyle.class);
                TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(baseStyle);
                style.font = fonts.dialogueBody();
                TextButton btn = new TextButton("> " + textVars.apply(strings.resolve(choices.get(i))), style);
                btn.getLabel().setWrap(true);
                btn.getLabelCell().expandX().fillX().pad(2);
                btn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        runner.selectChoice(idx);
                    }
                });
                choiceTable.add(btn).expandX().fillX().padTop(2).row();
            }
        }
    }

    public void dispose() {
        stage.dispose();
    }
}
