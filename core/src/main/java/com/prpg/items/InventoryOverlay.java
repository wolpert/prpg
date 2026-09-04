package com.prpg.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.prpg.items.config.ItemDefinition;
import com.prpg.ui.Fonts;
import com.prpg.ui.Strings;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class InventoryOverlay {

    private static final int COLUMNS = 4;
    private static final int CELL = 36;

    private final Inventory inventory;
    private final ItemRegistry registry;
    private final Skin skin;
    private final Fonts fonts;
    private final Strings strings;

    private final Stage stage;
    private final Table root;
    private final Table grid;
    private final Label nameLabel;
    private final Label descLabel;

    private boolean open;

    @Inject
    public InventoryOverlay(Inventory inventory, ItemRegistry registry, Skin skin, Fonts fonts,
                            Strings strings, SpriteBatch batch) {
        this.inventory = inventory;
        this.registry = registry;
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
        panel.pad(14);

        Label title = new Label("Inventory", new Label.LabelStyle(fonts.dialogueSpeaker(), null));
        panel.add(title).left().padBottom(8).row();

        grid = new Table();
        panel.add(grid).row();

        nameLabel = new Label("", new Label.LabelStyle(fonts.dialogueBody(), null));
        panel.add(nameLabel).left().padTop(10).row();

        descLabel = new Label("", new Label.LabelStyle(fonts.small(), null));
        descLabel.setWrap(true);
        descLabel.setAlignment(Align.topLeft);
        panel.add(descLabel).width(220).left().padTop(2).row();

        Label hint = new Label("[Items button or I to close]", new Label.LabelStyle(fonts.small(), null));
        panel.add(hint).left().padTop(10).row();

        root.add(panel);
        // Hidden (and so not hit-testable) while closed, so its item cells never eat world taps.
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
        if (open) {
            rebuild();
        } else {
            clearSelection();
        }
    }

    public void close() {
        open = false;
        root.setVisible(false);
        clearSelection();
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
        grid.clearChildren();
        var slots = inventory.getSlots();
        for (int i = 0; i < slots.size(); i++) {
            Inventory.Slot slot = slots.get(i);
            ItemDefinition def = registry.get(slot.itemId);

            Stack cell = new Stack();
            Image icon = new Image(new TextureRegionDrawable(registry.icon(slot.itemId)));
            cell.add(icon);

            if (slot.count > 1) {
                Table countWrap = new Table();
                countWrap.bottom().right();
                Label countLabel = new Label(String.valueOf(slot.count),
                        new Label.LabelStyle(fonts.small(), null));
                countWrap.add(countLabel).bottom().right();
                cell.add(countWrap);
            }

            cell.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    nameLabel.setText(def.name != null ? strings.resolve(def.name) : def.id);
                    descLabel.setText(def.description != null ? strings.resolve(def.description) : "");
                }
            });

            grid.add(cell).size(CELL, CELL).pad(3);
            if ((i + 1) % COLUMNS == 0) grid.row();
        }

        if (slots.isEmpty()) {
            grid.add(new Label("(empty)", new Label.LabelStyle(fonts.small(), null)));
        }
    }

    private void clearSelection() {
        nameLabel.setText("");
        descLabel.setText("");
    }

    public void dispose() {
        stage.dispose();
    }
}
