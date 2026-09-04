package com.prpg.items;

import com.prpg.items.config.ItemDefinition;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Single-player inventory. The player is the only holder, so a singleton store is simpler than an
 * ECS component here — dialogue, the overlay, pickups, and save/load all need access without an
 * entity lookup.
 */
@Singleton
public class Inventory {

    public static final int MAX_SLOTS = 20;

    public static class Slot {
        public final String itemId;
        public int count;

        public Slot(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    private final ItemRegistry registry;
    private final List<Slot> slots = new ArrayList<>();

    @Inject
    public Inventory(ItemRegistry registry) {
        this.registry = registry;
    }

    /** Adds count of itemId. Returns false if there's no room for a new non-stacking slot. */
    public boolean add(String itemId, int count) {
        if (count <= 0) return true;
        ItemDefinition def;
        try {
            def = registry.get(itemId);
        } catch (IllegalArgumentException e) {
            // Tolerate unknown ids (e.g. a stale save, or a renamed/typo'd item in YAML) rather
            // than crashing the load or a dialogue grant. Drop the item and carry on.
            Log.error("Inventory", "ignoring unknown item id: " + itemId);
            return false;
        }

        if (def.stackable) {
            Slot existing = findSlot(itemId);
            if (existing != null) {
                existing.count += count;
                return true;
            }
        }
        if (slots.size() >= MAX_SLOTS) {
            Log.info("Inventory", "no room for item \"" + itemId + "\" (inventory full, MAX_SLOTS="
                    + MAX_SLOTS + "); dropping grant");
            return false;
        }
        slots.add(new Slot(itemId, def.stackable ? count : 1));
        // For non-stackable items, add one slot per unit.
        if (!def.stackable) {
            for (int i = 1; i < count && slots.size() < MAX_SLOTS; i++) {
                slots.add(new Slot(itemId, 1));
            }
        }
        return true;
    }

    public boolean remove(String itemId, int count) {
        int remaining = count;
        for (int i = slots.size() - 1; i >= 0 && remaining > 0; i--) {
            Slot slot = slots.get(i);
            if (!slot.itemId.equals(itemId)) continue;
            int take = Math.min(slot.count, remaining);
            slot.count -= take;
            remaining -= take;
            if (slot.count <= 0) {
                slots.remove(i);
            }
        }
        return remaining == 0;
    }

    public int count(String itemId) {
        int total = 0;
        for (Slot slot : slots) {
            if (slot.itemId.equals(itemId)) total += slot.count;
        }
        return total;
    }

    public boolean has(String itemId, int count) {
        return count(itemId) >= count;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public void clear() {
        slots.clear();
    }

    private Slot findSlot(String itemId) {
        for (Slot slot : slots) {
            if (slot.itemId.equals(itemId)) return slot;
        }
        return null;
    }
}
