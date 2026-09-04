package com.prpg.activities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prpg.activities.config.OnCompleteConfig;
import com.prpg.items.Inventory;
import com.prpg.world.FlagStore;
import org.junit.jupiter.api.Test;

class OnCompleteApplierTest {

    @Test
    void firstCompletionGrantsItemSetsFlagAndBarks() {
        FlagStore flags = new FlagStore();
        Inventory inventory = mock(Inventory.class);
        when(inventory.add("brass_token", 1)).thenReturn(true);
        OnCompleteConfig oc = new OnCompleteConfig();
        oc.give_item = "brass_token";
        oc.set_flag = "act1.strongbox_opened";
        oc.dialogue = "strongbox_opened";

        OnCompleteApplier applier = new OnCompleteApplier(flags, inventory);
        assertEquals("strongbox_opened", applier.apply("strongbox", oc));
        assertTrue(flags.hasFlag("act1.strongbox_opened"));
        verify(inventory).add("brass_token", 1);

        // Second completion: no reward, no bark, flag stays.
        assertNull(applier.apply("strongbox", oc));
        verify(inventory, times(1)).add(anyString(), anyInt());
    }

    @Test
    void nullConfigIsANoOp() {
        Inventory inventory = mock(Inventory.class);
        assertNull(new OnCompleteApplier(new FlagStore(), inventory).apply("x", null));
        verify(inventory, never()).add(anyString(), anyInt());
    }
}
