package com.prpg.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Disposable;
import com.prpg.content.ContentResolver;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Generates BitmapFonts on demand at the device's physical pixel density. The default skin
 * font is a low-resolution bitmap, which pixelates badly when scaled up on Hi-DPI phones.
 * FreeType rasterizes glyphs at exactly the size we ask for, which stays crisp.
 */
@Singleton
public class Fonts implements Disposable {

    private final FreeTypeFontGenerator regular;
    private final FreeTypeFontGenerator bold;

    private BitmapFont dialogueBody;
    private BitmapFont dialogueSpeaker;
    private BitmapFont small;

    @Inject
    public Fonts(ContentResolver content) {
        regular = new FreeTypeFontGenerator(content.resolve("fonts/dialogue.ttf"));
        bold = new FreeTypeFontGenerator(content.resolve("fonts/dialogue-bold.ttf"));
    }

    public BitmapFont dialogueBody() {
        if (dialogueBody == null) {
            dialogueBody = generate(regular, 16, Color.WHITE);
        }
        return dialogueBody;
    }

    public BitmapFont dialogueSpeaker() {
        if (dialogueSpeaker == null) {
            dialogueSpeaker = generate(bold, 18, new Color(1f, 0.85f, 0.6f, 1f));
        }
        return dialogueSpeaker;
    }

    public BitmapFont small() {
        if (small == null) {
            small = generate(regular, 12, Color.LIGHT_GRAY);
        }
        return small;
    }

    private BitmapFont generate(FreeTypeFontGenerator gen, int sizePt, Color color) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        // Multiply by density so the rasterized glyphs are crisp on high-DPI screens.
        // ScreenViewport with unitsPerPixel = 1/density makes 1 logical unit = 1 dp.
        float density = Math.max(1f, Gdx.graphics.getDensity());
        p.size = Math.round(sizePt * density);
        p.color = color;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        BitmapFont font = gen.generateFont(p);
        // Scale back down so the font renders at sizePt logical units, but with density-scaled
        // glyph resolution under the hood.
        font.getData().setScale(1f / density);
        font.setUseIntegerPositions(false);
        return font;
    }

    @Override
    public void dispose() {
        if (dialogueBody != null) dialogueBody.dispose();
        if (dialogueSpeaker != null) dialogueSpeaker.dispose();
        if (small != null) small.dispose();
        regular.dispose();
        bold.dispose();
    }
}
