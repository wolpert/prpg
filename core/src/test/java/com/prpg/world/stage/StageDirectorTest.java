package com.prpg.world.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.bladecoder.ink.compiler.Compiler;
import com.prpg.items.Inventory;
import com.prpg.narrative.InkTestSupport;
import com.prpg.narrative.NarrativeRunner;
import com.prpg.narrative.NarrativeState;
import com.prpg.narrative.StoryVariables;
import com.prpg.narrative.content.ActContentSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Staging is the act's Ink {@code stage()} and {@code cast()} evaluated against story state. Drives
 * the real sample act through its beats, headless: no GL, no TMX.
 */
class StageDirectorTest {

    private StoryVariables vars;
    private NarrativeState state;
    private StageDirector stage;

    @BeforeEach
    void setUp() {
        vars = new StoryVariables();
        state = new NarrativeState();
        state.setCurrentActId("act1");
        state.unlockAct("act1");
        NarrativeRunner runner = InkTestSupport.runner(InkTestSupport.sourceFor("act1", "act2"), state,
                vars, mock(Inventory.class), id -> true);
        stage = new StageDirector(runner, state, vars);
    }

    private StagedPlacement find(String id) {
        for (StagedPlacement p : stage.all()) {
            if (id.equals(p.id())) return p;
        }
        return null;
    }

    @Test
    void theOpeningTableau() {
        StagedPlacement keeper = find("keeper");
        assertNotNull(keeper);
        assertEquals(StagedPlacement.Kind.ACTOR, keeper.kind());
        assertEquals("gatehouse_yard", keeper.mapId());
        assertEquals("keeper_post", keeper.marker());
        assertEquals("keeper_greeting", keeper.knot());
        assertEquals("npc", keeper.sprite(), "the look comes from cast()");
        assertEquals("C06A2E", keeper.color());

        StagedPlacement hedge = find("hedge");
        assertNotNull(hedge);
        assertTrue(hedge.solid(), "the hedge bars the door");
        assertEquals("hedge", hedge.knot());

        assertNull(find("workbench"), "the workbench waits for the strongbox");
        assertNull(find("gate_arch"), "the arch zone waits for the keeper");
        assertNotNull(find("travel_ration"));
        assertEquals(List.of("keeper", "hedge", "bench"),
                stage.stagedFor("gatehouse_yard").stream().map(StagedPlacement::id).toList());
    }

    @Test
    void clearingTheHedgeMovesTheKeeperInsideAndOpensTheDoor() {
        vars.set("hedge_cleared", true);
        StagedPlacement keeper = find("keeper");
        assertEquals("gatehouse_hall", keeper.mapId());
        assertEquals("keeper_desk", keeper.marker());
        assertEquals("keeper_inside", keeper.knot());
        assertNull(find("hedge"));
    }

    @Test
    void aZoneAppearsAndGoesAsTheStoryMoves() {
        vars.set("met_keeper", true);
        StagedPlacement arch = find("gate_arch");
        assertNotNull(arch);
        assertEquals(StagedPlacement.Kind.ZONE, arch.kind());
        vars.set("crossed_gate", true);
        assertNull(find("gate_arch"), "fire-once is just a variable in stage()");
    }

    @Test
    void aLookCanDependOnTheStoryToo() {
        assertEquals("6E4A2A", find("strongbox").color());
        vars.set("strongbox_opened", true);
        assertEquals("4A3A28", find("strongbox").color());
        assertNotNull(find("workbench"));
    }

    @Test
    void revisionMovesOnlyWhenTheWorldChanges() {
        int r0 = stage.revision();
        vars.set("rested", true); // stage() doesn't read it
        assertEquals(r0, stage.revision(), "an irrelevant change doesn't rebuild the world");
        vars.set("hedge_cleared", true);
        assertTrue(stage.revision() > r0);
    }

    @Test
    void theCurrentActDecidesWhichStoryIsAsked() {
        state.setCurrentActId("act2");
        assertEquals(List.of("traveller"), stage.all().stream().map(StagedPlacement::id).toList());
        assertEquals("7A6E9B", find("traveller").color());
    }

    @Test
    void locksDuplicatesAndBadCalls() throws Exception {
        String ink = """
                EXTERNAL actor(id, map, marker, knot)
                EXTERNAL thing(id, map, marker, knot, solid)
                EXTERNAL lock(portal)
                EXTERNAL define(id, sprite, color)
                EXTERNAL quest(title)
                -> DONE
                === function stage() ===
                ~ actor("a", "m", "first", -> talk)
                ~ actor("a", "m", "second", -> talk)
                ~ thing("rock", "m", "spot", "", false)
                ~ thing("broken", "m", "", -> talk, true)
                ~ lock("back_door")
                ~ quest("not here")
                === function cast() ===
                ~ define("a", "", "112233")
                === talk ===
                Hi.
                -> END
                """;
        Compiler.Options options = new Compiler.Options();
        options.sourceFilename = "stage.ink";
        String json = new Compiler(ink, options).compile().toJson();
        ActContentSource source = new ActContentSource() {
            @Override
            public boolean has(String actId) {
                return "t".equals(actId);
            }

            @Override
            public String read(String actId) {
                return json;
            }
        };
        state.setCurrentActId("t");
        NarrativeRunner runner = InkTestSupport.runner(source, state, vars, mock(Inventory.class), id -> true);
        stage = new StageDirector(runner, state, vars);

        StagedPlacement a = find("a");
        assertEquals("second", a.marker(), "the last call for an id wins");
        assertEquals("112233", a.color());
        StagedPlacement rock = find("rock");
        assertNull(rock.knot(), "\"\" means nothing to interact with");
        assertFalse(rock.solid());
        assertNull(find("broken"), "a placement without a marker is dropped, not half-built");
        assertTrue(stage.isPortalLocked("back_door"));
        assertFalse(stage.isPortalLocked("front_door"));
        assertEquals(2, stage.all().size(), "quest() in stage() is ignored");
    }
}
