package com.prpg.build;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the template's placeholder art with nothing but the JDK, so the repository can ship
 * free of any third-party sprite licence: a 16x16 tileset and 48x48 character sheets laid out the way
 * {@code art/tools/gen_character_aseprites.lua} expects. Replace the outputs with real art whenever;
 * every consumer (tilesets, atlas regions) is looked up by name, not by pixel.
 *
 * <p>Tile table ({@code basic.png}, 8 columns x 4 rows; gid = index + 1):
 * <pre>
 *  0 grass       1 grass_alt   2 dirt        3 path        4 sand        5 water       6 flowers     7 bush
 *  8 wall        9 wall_top   10 wall_dark  11 window     12 door       13 door_open  14 floor_wood 15 floor_stone
 * 16 tree_top   17 tree_trunk 18 rock       19 fence_h    20 fence_v    21 hedge      22 sign       23 crate
 * 24 table      25 chair      26 bed        27 chest      28 rug        29 barrel     30 carpet     31 void
 * </pre>
 */
public final class PlaceholderArt {

    public static final int TILE = 16;
    public static final int TILE_COLUMNS = 8;
    public static final int TILE_ROWS = 4;

    public static final int FRAME = 48;
    /** Row order of the character sheets; the Aseprite script tags rows in this order. */
    public static final String[] CHARACTER_ROWS = {
            "idle_down", "idle_right", "idle_up", "walk_down", "walk_right", "walk_up"};
    public static final int IDLE_FRAMES = 4;
    public static final int WALK_FRAMES = 6;

    private PlaceholderArt() {}

    /** Writes the tileset and the character sheets under {@code baselinePack}. */
    public static void generate(File baselinePack) throws IOException {
        File tilesets = new File(baselinePack, "tilesets");
        File art = new File(baselinePack, "art");
        tilesets.mkdirs();
        art.mkdirs();
        ImageIO.write(tileset(), "png", new File(tilesets, "basic.png"));
        ImageIO.write(characterSheet(new Color(0x3F, 0x7C, 0xC0), new Color(0xF2, 0xD3, 0xA7), 11L),
                "png", new File(art, "player.png"));
        ImageIO.write(characterSheet(new Color(0xC0, 0x6A, 0x2E), new Color(0xE8, 0xC9, 0x9A), 23L),
                "png", new File(art, "npc.png"));
    }

    // --- tileset -------------------------------------------------------------------------------

    static BufferedImage tileset() {
        BufferedImage img = new BufferedImage(TILE_COLUMNS * TILE, TILE_ROWS * TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Random rng = new Random(7);
        for (int i = 0; i < TILE_COLUMNS * TILE_ROWS; i++) {
            int ox = (i % TILE_COLUMNS) * TILE;
            int oy = (i / TILE_COLUMNS) * TILE;
            drawTile(g, i, ox, oy, rng);
        }
        g.dispose();
        return img;
    }

    private static void drawTile(Graphics2D g, int index, int ox, int oy, Random rng) {
        switch (index) {
            case 0 -> speckled(g, ox, oy, new Color(0x5B, 0x8C, 0x3E), new Color(0x66, 0x9A, 0x46), rng, 10);
            case 1 -> speckled(g, ox, oy, new Color(0x56, 0x85, 0x3A), new Color(0x4C, 0x78, 0x33), rng, 14);
            case 2 -> speckled(g, ox, oy, new Color(0x8A, 0x6A, 0x45), new Color(0x7A, 0x5C, 0x3B), rng, 8);
            case 3 -> speckled(g, ox, oy, new Color(0xB5, 0x9E, 0x76), new Color(0xA5, 0x8F, 0x68), rng, 12);
            case 4 -> speckled(g, ox, oy, new Color(0xD8, 0xC8, 0x8E), new Color(0xC9, 0xB9, 0x80), rng, 8);
            case 5 -> {
                fill(g, ox, oy, new Color(0x2F, 0x6E, 0xA8));
                g.setColor(new Color(0x4C, 0x8C, 0xC6));
                g.drawLine(ox + 2, oy + 5, ox + 6, oy + 5);
                g.drawLine(ox + 8, oy + 11, ox + 13, oy + 11);
            }
            case 6 -> {
                speckled(g, ox, oy, new Color(0x5B, 0x8C, 0x3E), new Color(0x66, 0x9A, 0x46), rng, 6);
                dot(g, ox + 3, oy + 4, new Color(0xE8, 0x5C, 0x7A));
                dot(g, ox + 10, oy + 9, new Color(0xF5, 0xE0, 0x6A));
                dot(g, ox + 6, oy + 12, new Color(0xFF, 0xFF, 0xFF));
            }
            case 7 -> {
                fill(g, ox, oy, new Color(0x5B, 0x8C, 0x3E));
                g.setColor(new Color(0x2E, 0x5A, 0x24));
                g.fillOval(ox + 1, oy + 2, 14, 13);
                g.setColor(new Color(0x3F, 0x73, 0x30));
                g.fillOval(ox + 3, oy + 3, 8, 7);
            }
            case 8 -> bricks(g, ox, oy, new Color(0x8C, 0x8A, 0x84), new Color(0x6E, 0x6C, 0x66));
            case 9 -> {
                bricks(g, ox, oy, new Color(0x9C, 0x9A, 0x94), new Color(0x7A, 0x78, 0x72));
                g.setColor(new Color(0xB8, 0xB6, 0xB0));
                g.fillRect(ox, oy, TILE, 3);
            }
            case 10 -> bricks(g, ox, oy, new Color(0x5C, 0x5A, 0x56), new Color(0x44, 0x42, 0x3E));
            case 11 -> {
                bricks(g, ox, oy, new Color(0x8C, 0x8A, 0x84), new Color(0x6E, 0x6C, 0x66));
                g.setColor(new Color(0x9F, 0xD3, 0xE8));
                g.fillRect(ox + 4, oy + 3, 8, 9);
                g.setColor(new Color(0x4A, 0x3A, 0x28));
                g.drawRect(ox + 3, oy + 2, 9, 10);
                g.drawLine(ox + 8, oy + 3, ox + 8, oy + 11);
            }
            case 12 -> {
                fill(g, ox, oy, new Color(0x6E, 0x4A, 0x2A));
                g.setColor(new Color(0x8A, 0x60, 0x38));
                g.fillRect(ox + 2, oy + 1, 12, 15);
                g.setColor(new Color(0x4A, 0x30, 0x18));
                g.drawRect(ox + 2, oy + 1, 11, 14);
                dot(g, ox + 11, oy + 8, new Color(0xE8, 0xC0, 0x50));
            }
            case 13 -> {
                fill(g, ox, oy, new Color(0x14, 0x12, 0x10));
                g.setColor(new Color(0x6E, 0x4A, 0x2A));
                g.fillRect(ox, oy, 3, TILE);
                g.fillRect(ox + 13, oy, 3, TILE);
            }
            case 14 -> planks(g, ox, oy, new Color(0x9A, 0x6E, 0x44), new Color(0x7E, 0x58, 0x36));
            case 15 -> bricks(g, ox, oy, new Color(0x7E, 0x7C, 0x80), new Color(0x66, 0x64, 0x68));
            case 16 -> {
                fill(g, ox, oy, new Color(0x5B, 0x8C, 0x3E));
                g.setColor(new Color(0x24, 0x52, 0x1E));
                g.fillOval(ox, oy, 16, 16);
                g.setColor(new Color(0x36, 0x6E, 0x2C));
                g.fillOval(ox + 3, oy + 2, 8, 8);
            }
            case 17 -> {
                fill(g, ox, oy, new Color(0x5B, 0x8C, 0x3E));
                g.setColor(new Color(0x5A, 0x3C, 0x20));
                g.fillRect(ox + 6, oy, 4, 16);
                g.setColor(new Color(0x24, 0x52, 0x1E));
                g.fillOval(ox, oy - 8, 16, 14);
            }
            case 18 -> {
                fill(g, ox, oy, new Color(0x5B, 0x8C, 0x3E));
                g.setColor(new Color(0x78, 0x78, 0x7A));
                g.fillOval(ox + 2, oy + 4, 12, 10);
                g.setColor(new Color(0xA0, 0xA0, 0xA4));
                g.fillOval(ox + 4, oy + 5, 5, 4);
            }
            case 19 -> {
                fill(g, ox, oy, new Color(0x5B, 0x8C, 0x3E));
                g.setColor(new Color(0x8A, 0x60, 0x38));
                g.fillRect(ox, oy + 5, 16, 2);
                g.fillRect(ox, oy + 10, 16, 2);
                g.fillRect(ox + 2, oy + 3, 2, 11);
                g.fillRect(ox + 12, oy + 3, 2, 11);
            }
            case 20 -> {
                fill(g, ox, oy, new Color(0x5B, 0x8C, 0x3E));
                g.setColor(new Color(0x8A, 0x60, 0x38));
                g.fillRect(ox + 5, oy, 2, 16);
                g.fillRect(ox + 10, oy, 2, 16);
                g.fillRect(ox + 3, oy + 2, 11, 2);
                g.fillRect(ox + 3, oy + 12, 11, 2);
            }
            case 21 -> {
                fill(g, ox, oy, new Color(0x2E, 0x5A, 0x24));
                for (int k = 0; k < 6; k++) {
                    dot(g, ox + rng.nextInt(14) + 1, oy + rng.nextInt(14) + 1, new Color(0x49, 0x80, 0x36));
                }
            }
            case 22 -> {
                fill(g, ox, oy, new Color(0x5B, 0x8C, 0x3E));
                g.setColor(new Color(0x8A, 0x60, 0x38));
                g.fillRect(ox + 7, oy + 6, 2, 10);
                g.setColor(new Color(0xC9, 0xA2, 0x6A));
                g.fillRect(ox + 2, oy + 2, 12, 6);
            }
            case 23 -> {
                fill(g, ox, oy, new Color(0x7E, 0x58, 0x36));
                g.setColor(new Color(0xA0, 0x74, 0x48));
                g.fillRect(ox + 1, oy + 1, 14, 14);
                g.setColor(new Color(0x5A, 0x3C, 0x20));
                g.drawRect(ox + 1, oy + 1, 13, 13);
                g.drawLine(ox + 1, oy + 1, ox + 14, oy + 14);
            }
            case 24 -> {
                planks(g, ox, oy, new Color(0x9A, 0x6E, 0x44), new Color(0x7E, 0x58, 0x36));
                g.setColor(new Color(0xB8, 0x8C, 0x5A));
                g.fillRect(ox + 1, oy + 3, 14, 8);
                g.setColor(new Color(0x5A, 0x3C, 0x20));
                g.drawRect(ox + 1, oy + 3, 13, 7);
            }
            case 25 -> {
                planks(g, ox, oy, new Color(0x9A, 0x6E, 0x44), new Color(0x7E, 0x58, 0x36));
                g.setColor(new Color(0x5A, 0x3C, 0x20));
                g.fillRect(ox + 4, oy + 2, 8, 3);
                g.fillRect(ox + 4, oy + 7, 8, 5);
                g.fillRect(ox + 4, oy + 12, 2, 3);
                g.fillRect(ox + 10, oy + 12, 2, 3);
            }
            case 26 -> {
                planks(g, ox, oy, new Color(0x9A, 0x6E, 0x44), new Color(0x7E, 0x58, 0x36));
                g.setColor(new Color(0x8E, 0x3A, 0x48));
                g.fillRect(ox + 2, oy + 1, 12, 14);
                g.setColor(new Color(0xF0, 0xEC, 0xE0));
                g.fillRect(ox + 3, oy + 2, 10, 4);
            }
            case 27 -> {
                planks(g, ox, oy, new Color(0x9A, 0x6E, 0x44), new Color(0x7E, 0x58, 0x36));
                g.setColor(new Color(0x6E, 0x4A, 0x2A));
                g.fillRect(ox + 2, oy + 4, 12, 9);
                g.setColor(new Color(0xE8, 0xC0, 0x50));
                g.drawRect(ox + 2, oy + 4, 11, 8);
                dot(g, ox + 7, oy + 8, new Color(0xE8, 0xC0, 0x50));
            }
            case 28 -> {
                fill(g, ox, oy, new Color(0x7A, 0x2E, 0x3A));
                g.setColor(new Color(0xC9, 0xA2, 0x6A));
                g.drawRect(ox + 1, oy + 1, 13, 13);
            }
            case 29 -> {
                planks(g, ox, oy, new Color(0x9A, 0x6E, 0x44), new Color(0x7E, 0x58, 0x36));
                g.setColor(new Color(0x6E, 0x4A, 0x2A));
                g.fillOval(ox + 3, oy + 1, 10, 14);
                g.setColor(new Color(0x44, 0x44, 0x48));
                g.drawLine(ox + 3, oy + 5, ox + 12, oy + 5);
                g.drawLine(ox + 3, oy + 11, ox + 12, oy + 11);
            }
            case 30 -> {
                fill(g, ox, oy, new Color(0x2E, 0x3A, 0x7A));
                g.setColor(new Color(0x6A, 0x7A, 0xC9));
                g.drawRect(ox + 1, oy + 1, 13, 13);
            }
            default -> fill(g, ox, oy, new Color(0x08, 0x08, 0x0A));
        }
    }

    private static void fill(Graphics2D g, int ox, int oy, Color c) {
        g.setColor(c);
        g.fillRect(ox, oy, TILE, TILE);
    }

    private static void dot(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fillRect(x, y, 2, 2);
    }

    private static void speckled(Graphics2D g, int ox, int oy, Color base, Color speck, Random rng, int count) {
        fill(g, ox, oy, base);
        g.setColor(speck);
        for (int k = 0; k < count; k++) {
            g.fillRect(ox + rng.nextInt(TILE), oy + rng.nextInt(TILE), 1, 1);
        }
    }

    private static void bricks(Graphics2D g, int ox, int oy, Color face, Color mortar) {
        fill(g, ox, oy, mortar);
        g.setColor(face);
        for (int row = 0; row < 4; row++) {
            int shift = (row % 2) * 4;
            for (int col = -1; col < 3; col++) {
                int x = ox + col * 8 + shift + 1;
                int y = oy + row * 4 + 1;
                g.fillRect(Math.max(ox, x), y, Math.min(6, ox + TILE - Math.max(ox, x)), 2);
            }
        }
    }

    private static void planks(Graphics2D g, int ox, int oy, Color face, Color seam) {
        fill(g, ox, oy, face);
        g.setColor(seam);
        for (int y = 0; y < TILE; y += 4) {
            g.drawLine(ox, oy + y, ox + TILE - 1, oy + y);
        }
    }

    // --- character sheets ------------------------------------------------------------------------

    /**
     * A 6-row sheet: idle down/right/up (4 frames), walk down/right/up (6 frames). Frames are 48x48;
     * unused cells stay transparent so the Aseprite script's per-row frame count works.
     */
    static BufferedImage characterSheet(Color body, Color skin, long seed) {
        int cols = Math.max(IDLE_FRAMES, WALK_FRAMES);
        BufferedImage img = new BufferedImage(cols * FRAME, CHARACTER_ROWS.length * FRAME, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int row = 0; row < CHARACTER_ROWS.length; row++) {
            String tag = CHARACTER_ROWS[row];
            boolean walk = tag.startsWith("walk");
            String dir = tag.substring(tag.indexOf('_') + 1);
            int frames = walk ? WALK_FRAMES : IDLE_FRAMES;
            for (int f = 0; f < frames; f++) {
                drawCharacter(g, f * FRAME, row * FRAME, dir, walk, f, frames, body, skin);
            }
        }
        g.dispose();
        return img;
    }

    private static void drawCharacter(Graphics2D g, int ox, int oy, String dir, boolean walk, int frame,
                                      int frames, Color body, Color skin) {
        // Feet sit at the bottom-centre of the frame, matching the engine's collision-box offsets.
        int cx = ox + FRAME / 2;
        int feetY = oy + FRAME - 4;
        double phase = (double) frame / frames * Math.PI * 2;
        int bob = walk ? (int) Math.round(Math.sin(phase) * 1.5) : (frame % 2 == 0 ? 0 : 1);
        int legSwing = walk ? (int) Math.round(Math.sin(phase) * 4) : 0;

        Color dark = body.darker();
        Color outline = new Color(0x1A, 0x14, 0x10);

        // legs
        g.setColor(dark);
        g.fillRect(cx - 6, feetY - 10 + Math.max(0, legSwing), 5, 10 - Math.max(0, legSwing));
        g.fillRect(cx + 1, feetY - 10 + Math.max(0, -legSwing), 5, 10 - Math.max(0, -legSwing));
        // body
        int top = feetY - 24 - bob;
        g.setColor(body);
        g.fillRoundRect(cx - 9, top, 18, 16, 6, 6);
        g.setColor(outline);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(cx - 9, top, 18, 16, 6, 6);
        // head
        int headY = top - 14;
        g.setColor(skin);
        g.fillOval(cx - 8, headY, 16, 16);
        g.setColor(outline);
        g.drawOval(cx - 8, headY, 16, 16);
        // hair cap (darker on the back of the head so "up" reads as facing away)
        g.setColor(dark);
        if ("up".equals(dir)) {
            g.fillArc(cx - 8, headY, 16, 16, 0, 360);
        } else {
            g.fillArc(cx - 8, headY, 16, 16, 20, 140);
        }
        // eyes: down = both, right = one on the right side, up = none
        g.setColor(outline);
        if ("down".equals(dir)) {
            g.fillRect(cx - 4, headY + 7, 2, 3);
            g.fillRect(cx + 2, headY + 7, 2, 3);
        } else if ("right".equals(dir)) {
            g.fillRect(cx + 3, headY + 7, 2, 3);
            g.setColor(body);
            g.fillRect(cx + 5, top + 6, 4, 6); // leading arm
        }
    }
}
