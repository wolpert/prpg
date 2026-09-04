package com.prpg.activities.lightsout.config;

import com.prpg.activities.config.OnCompleteConfig;

/** A lights-out popup definition ({@code activities/lightsout/<id>.yaml}). */
public class LightsOutDefinition {
    public String id;
    public String title = "Light every tile";
    public String hint = "Tap a tile to flip it and its neighbours. Light them all.";
    public BoardConfig board = new BoardConfig();
    /** How many random presses scramble the board (difficulty). */
    public int scramble = 5;
    /** Swatch colours for lit / unlit tiles (RRGGBB, no '#'). */
    public String lit_color = "E8C860";
    public String unlit_color = "2A2E38";
    public OnCompleteConfig on_complete;

    public static class BoardConfig {
        public int width = 3;
        public int height = 3;
    }
}
