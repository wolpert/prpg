package com.prpg.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.prpg.items.config.ItemDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InventoryTest {

    private ItemRegistry registry;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        registry = Mockito.mock(ItemRegistry.class);
        when(registry.get("wormwood_sprig")).thenReturn(stackable("wormwood_sprig"));
        when(registry.get("thimble")).thenReturn(nonStackable("thimble"));
        inventory = new Inventory(registry);
    }

    private static ItemDefinition stackable(String id) {
        ItemDefinition d = new ItemDefinition();
        d.id = id;
        d.stackable = true;
        return d;
    }

    private static ItemDefinition nonStackable(String id) {
        ItemDefinition d = new ItemDefinition();
        d.id = id;
        d.stackable = false;
        return d;
    }

    @Test
    void stackableItemsCombineIntoOneSlot() {
        inventory.add("wormwood_sprig", 3);
        inventory.add("wormwood_sprig", 2);

        assertEquals(5, inventory.count("wormwood_sprig"));
        assertEquals(1, inventory.getSlots().size());
    }

    @Test
    void nonStackableItemsTakeSeparateSlots() {
        inventory.add("thimble", 2);

        assertEquals(2, inventory.count("thimble"));
        assertEquals(2, inventory.getSlots().size());
    }

    @Test
    void removeDecrementsAndClearsEmptySlots() {
        inventory.add("wormwood_sprig", 4);
        assertTrue(inventory.remove("wormwood_sprig", 4));
        assertEquals(0, inventory.count("wormwood_sprig"));
        assertTrue(inventory.getSlots().isEmpty());
    }

    @Test
    void unknownItemIdIsDroppedNotThrown() {
        when(registry.get("ghost")).thenThrow(new IllegalArgumentException("unknown item id: ghost"));
        assertFalse(inventory.add("ghost", 1));
        assertEquals(0, inventory.count("ghost"));
        assertTrue(inventory.getSlots().isEmpty());
    }

    @Test
    void hasChecksThreshold() {
        inventory.add("wormwood_sprig", 2);
        assertTrue(inventory.has("wormwood_sprig", 2));
        assertFalse(inventory.has("wormwood_sprig", 3));
    }
}
