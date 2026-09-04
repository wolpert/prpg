package com.prpg.activities.match3.config;

import com.prpg.activities.config.OnCompleteConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A match-3 board definition ({@code activities/match3/<id>.yaml}). */
public class Match3Definition {
    public String id;
    /** Header shown above the board. */
    public String title = "Match three";
    public BoardConfig board;
    public WinConfig win;
    public OnCompleteConfig on_complete;

    public static class BoardConfig {
        public int width;
        public int height;
        // Each entry is either a bare String (the piece name, rendered as a palette swatch) or a
        // Map with {name, color?, atlas?, region?}. SnakeYAML holds the mixed list as Object;
        // {@link #pieceList()} normalizes it.
        public List<Object> pieces;
    }

    public static class WinConfig {
        /** Per-piece-type clear targets, keyed by piece name. Won when every target is met. */
        public Map<String, Integer> per_type;
    }

    /**
     * A normalized piece definition. {@code name} is always present; {@code color} overrides the
     * built-in palette swatch; {@code atlas}+{@code region} render real art (with swatch fallback).
     */
    public record Piece(String name, String color, String atlas, String region) {}

    /** Normalizes {@link BoardConfig#pieces} into typed {@link Piece} records, in list order. */
    public List<Piece> pieceList() {
        List<Piece> out = new ArrayList<>();
        if (board == null || board.pieces == null) {
            return out;
        }
        for (Object entry : board.pieces) {
            if (entry instanceof Map<?, ?> map) {
                out.add(new Piece(
                        str(map.get("name")),
                        str(map.get("color")),
                        str(map.get("atlas")),
                        str(map.get("region"))));
            } else if (entry != null) {
                out.add(new Piece(entry.toString(), null, null, null));
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
