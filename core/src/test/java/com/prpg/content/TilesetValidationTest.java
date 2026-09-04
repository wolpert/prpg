package com.prpg.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Guard rail for tileset definitions in every pack. A {@code .tsx} whose declared geometry drifts
 * from its PNG doesn't error at load: Tiled and libGDX both index tiles by arithmetic, so a wrong
 * {@code columns} silently shifts every tile in every map that uses it. Pure JVM: reads the PNG
 * header directly rather than decoding the image.
 */
class TilesetValidationTest {

    private final List<String> errors = new ArrayList<>();

    @Test
    void everyTilesetMatchesItsImage() {
        int checked = 0;
        for (File pack : PackTestSupport.packDirs()) {
            File dir = new File(pack, "tilesets");
            File[] tsxFiles = dir.listFiles((d, n) -> n.endsWith(".tsx"));
            if (tsxFiles == null) continue;
            for (File tsx : tsxFiles) {
                checked++;
                check(dir, tsx);
            }
        }
        assertTrue(checked > 0, "expected at least one tileset across the packs");
        assertTrue(errors.isEmpty(), "tileset errors:\n  " + String.join("\n  ", errors));
    }

    private void check(File dir, File tsx) {
        String where = tsx.getName();
        Element ts = parse(tsx).getDocumentElement();
        String name = ts.getAttribute("name");
        int tileW = intAttr(ts, "tilewidth");
        int tileH = intAttr(ts, "tileheight");
        int tileCount = intAttr(ts, "tilecount");
        int columns = intAttr(ts, "columns");

        String base = where.substring(0, where.length() - ".tsx".length());
        if (!base.equals(name)) {
            errors.add(where + ": name '" + name + "' does not match the filename");
        }

        NodeList images = ts.getElementsByTagName("image");
        if (images.getLength() != 1) {
            errors.add(where + ": expected exactly one <image>");
            return;
        }
        Element image = (Element) images.item(0);
        File png = new File(dir, image.getAttribute("source"));
        if (!png.exists()) {
            errors.add(where + ": missing image " + image.getAttribute("source"));
            return;
        }
        int[] actual = pngSize(png);
        if (actual[0] != intAttr(image, "width") || actual[1] != intAttr(image, "height")) {
            errors.add(where + ": declares " + intAttr(image, "width") + "x" + intAttr(image, "height")
                    + " but " + png.getName() + " is " + actual[0] + "x" + actual[1]);
            return;
        }
        int cols = actual[0] / tileW;
        int rows = actual[1] / tileH;
        if (columns != cols) {
            errors.add(where + ": columns=" + columns + " but the image is " + cols + " tiles wide");
        }
        if (tileCount != cols * rows) {
            errors.add(where + ": tilecount=" + tileCount + " but the image holds " + (cols * rows));
        }

        // Animation frames must reference real tiles in this same tileset.
        NodeList tiles = ts.getElementsByTagName("tile");
        for (int i = 0; i < tiles.getLength(); i++) {
            Element tile = (Element) tiles.item(i);
            int id = intAttr(tile, "id");
            if (id >= cols * rows) {
                errors.add(where + ": <tile id=" + id + "> is outside the tileset");
            }
            NodeList frames = tile.getElementsByTagName("frame");
            for (int f = 0; f < frames.getLength(); f++) {
                int frameId = intAttr((Element) frames.item(f), "tileid");
                if (frameId >= cols * rows) {
                    errors.add(where + ": tile " + id + " animates to tileid " + frameId + ", outside the tileset");
                }
            }
        }
    }

    /** Width/height straight from the PNG IHDR chunk: no image decoding, no GL. */
    private static int[] pngSize(File f) {
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(f))) {
            in.skipBytes(16);
            return new int[]{in.readInt(), in.readInt()};
        } catch (Exception e) {
            throw new RuntimeException("failed to read PNG header of " + f, e);
        }
    }

    private static int intAttr(Element e, String name) {
        String v = e.getAttribute(name);
        return v.isEmpty() ? -1 : Integer.parseInt(v);
    }

    private static Document parse(File f) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
        } catch (Exception e) {
            throw new RuntimeException("failed to parse " + f, e);
        }
    }
}
