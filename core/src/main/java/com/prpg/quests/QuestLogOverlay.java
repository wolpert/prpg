package com.prpg.quests;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.prpg.quests.config.QuestDefinition;
import com.prpg.quests.config.QuestDefinition.QuestStep;
import com.prpg.ui.Fonts;
import com.prpg.ui.Strings;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class QuestLogOverlay {

    private final QuestLog questLog;
    private final Skin skin;
    private final Fonts fonts;
    private final Strings strings;

    private final Stage stage;
    private final Table root;
    private final Table content;
    private boolean open;

    @Inject
    public QuestLogOverlay(QuestLog questLog, Skin skin, Fonts fonts, Strings strings,
                           SpriteBatch batch) {
        this.questLog = questLog;
        this.skin = skin;
        this.fonts = fonts;
        this.strings = strings;

        ScreenViewport viewport = new ScreenViewport();
        viewport.setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage = new Stage(viewport, batch);

        root = new Table();
        root.setFillParent(true);
        root.center();

        Table panel = new Table(skin);
        panel.setBackground(skin.newDrawable("white", 0.08f, 0.07f, 0.10f, 0.95f));
        panel.pad(16);

        Label title = new Label("Journal", new Label.LabelStyle(fonts.dialogueSpeaker(), null));
        panel.add(title).left().padBottom(10).row();

        content = new Table();
        panel.add(content).left().width(280).row();

        Label hint = new Label("[Quests button or Q to close]", new Label.LabelStyle(fonts.small(), null));
        panel.add(hint).left().padTop(12).row();

        root.add(panel);
        // Hidden (and so not hit-testable) while closed, so it never eats world taps.
        root.setVisible(false);
        stage.addActor(root);
    }

    public boolean isOpen() {
        return open;
    }

    public Stage getStage() {
        return stage;
    }

    public void toggle() {
        open = !open;
        root.setVisible(open);
        if (open) rebuild();
    }

    public void close() {
        open = false;
        root.setVisible(false);
    }

    public void update(float delta) {
        if (!open) return;
        stage.act(delta);
    }

    public void draw() {
        if (!open) return;
        stage.draw();
    }

    public void resize(int width, int height) {
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1f / Gdx.graphics.getDensity());
        stage.getViewport().update(width, height, true);
    }

    private void rebuild() {
        content.clearChildren();

        java.util.List<QuestDefinition> visible = questLog.getVisibleQuests();
        if (visible.isEmpty()) {
            content.add(new Label("(no entries yet)", new Label.LabelStyle(fonts.small(), null))).left();
            return;
        }

        for (QuestDefinition quest : visible) {
            String titleText = quest.title != null ? strings.resolve(quest.title) : quest.id;
            if (questLog.state(quest) == QuestLog.State.COMPLETE) titleText += "  (done)";
            Label questTitle = new Label(titleText, new Label.LabelStyle(fonts.dialogueBody(), null));
            questTitle.setColor(0.95f, 0.9f, 0.7f, 1f);
            content.add(questTitle).left().padTop(6).row();

            int current = questLog.currentStepIndex(quest);
            for (int i = 0; i < quest.steps.size(); i++) {
                QuestStep step = quest.steps.get(i);
                Label stepLabel = buildStepLabel(step, i, current);
                content.add(stepLabel).left().padLeft(10).width(270).row();
            }
        }
    }

    private Label buildStepLabel(QuestStep step, int index, int current) {
        boolean complete = index < current;
        boolean isCurrent = index == current;

        // Bitmap fonts can't strike through, so completion is conveyed by prefix + dimming.
        String prefix = complete ? "x  " : isCurrent ? ">  " : "-  ";
        Label.LabelStyle style = new Label.LabelStyle(
                isCurrent ? fonts.dialogueSpeaker() : fonts.dialogueBody(), null);
        Label label = new Label(prefix + strings.resolve(step.text), style);
        label.setWrap(true);
        label.setAlignment(Align.topLeft);
        if (complete) {
            label.setColor(0.5f, 0.5f, 0.5f, 1f);
        } else if (isCurrent) {
            label.setColor(1f, 1f, 1f, 1f);
        } else {
            label.setColor(0.7f, 0.7f, 0.7f, 1f);
        }
        return label;
    }

    public void dispose() {
        stage.dispose();
    }
}
