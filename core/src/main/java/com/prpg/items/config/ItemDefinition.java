package com.prpg.items.config;

public class ItemDefinition {
    public String id;
    public String name;
    public String description;
    /** Atlas region name for real art. Unused while placeholders are in play. */
    public String spriteRegion;
    /** Hex RGB (e.g. "8B4513") for the placeholder swatch shown until real art lands. */
    public String color;
    public boolean stackable;
}
