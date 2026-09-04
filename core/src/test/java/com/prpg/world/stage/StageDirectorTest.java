package com.prpg.world.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.world.FlagStore;
import com.prpg.world.stage.config.ActorDef;
import com.prpg.world.stage.config.StagingConfig;
import com.prpg.world.stage.config.StagingConfig.At;
import com.prpg.world.stage.config.StagingConfig.CastEntry;
import com.prpg.world.stage.config.StagingConfig.ObstacleEntry;
import com.prpg.world.stage.config.StagingConfig.PropEntry;
import com.prpg.world.stage.config.StagingConfig.ActivityRef;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Headless coverage of the staging resolver. Placement is a pure function of the flags plus the
 * imperative overrides, so the whole thing is testable with no GL, no TMX and no content files —
 * staging is injected via {@link StageDirector#useStaging}.
 */
class StageDirectorTest {

    private static final String SQUARE = "village_square";
    private static final String PATH = "boundary_path";

    private FlagStore flags;
    private StageDirector stage;

    @BeforeEach
    void setUp() {
        flags = new FlagStore();
        ActorRegistry actors = mock(ActorRegistry.class);
        when(actors.get("rowan")).thenReturn(actor("rowan", "5E8B7E"));
        when(actors.get("wren")).thenReturn(actor("wren", "E0C060"));
        stage = new StageDirector(new ConfigLoader(), mock(ContentResolver.class), flags, actors);
    }

    private static ActorDef actor(String id, String color) {
        ActorDef def = new ActorDef();
        def.id = id;
        def.color = color;
        return def;
    }

    private static At at(String map, String marker) {
        At a = new At();
        a.map = map;
        a.marker = marker;
        return a;
    }

    private static CastEntry cast(String actor, String map, String marker, String dialogue) {
        CastEntry e = new CastEntry();
        e.actor = actor;
        e.at = at(map, marker);
        e.dialogue = dialogue;
        return e;
    }

    private StagedPlacement only(String mapId) {
        List<StagedPlacement> staged = stage.stagedFor(mapId);
        assertEquals(1, staged.size(), "expected exactly one staged thing on " + mapId + ", got " + staged);
        return staged.get(0);
    }

    // --- declarative placement ----------------------------------------------------------------

    @Test
    void lastMatchingCastEntryWins() {
        StagingConfig config = new StagingConfig();
        config.cast.add(cast("rowan", SQUARE, "well_side", "rowan_square"));
        CastEntry moved = cast("rowan", PATH, "stone_row", "rowan_worried");
        moved.when = List.of("a1.learned_to_bundle");
        config.cast.add(moved);
        stage.useStaging("act1", config);

        // Story-early: the first entry is the only match.
        assertEquals(SQUARE, only(SQUARE).mapId());
        assertTrue(stage.stagedFor(PATH).isEmpty(), "he hasn't moved yet");

        flags.setFlag("a1.learned_to_bundle");

        // Story-late: the later entry wins and he is gone from the square entirely.
        assertTrue(stage.stagedFor(SQUARE).isEmpty(), "he left the square");
        StagedPlacement now = only(PATH);
        assertEquals("stone_row", now.marker());
        assertEquals("rowan_worried", now.dialogue(), "the later entry re-voices him too");
    }

    @Test
    void unlessRemovesAnActorFromTheWorld() {
        StagingConfig config = new StagingConfig();
        CastEntry wren = cast("wren", SQUARE, "wren_fence", "wren_rhyme");
        wren.unless = List.of("a1.saw_margaret");
        config.cast.add(wren);
        stage.useStaging("act1", config);

        assertEquals("wren", only(SQUARE).id());
        flags.setFlag("a1.saw_margaret");
        assertTrue(stage.stagedFor(SQUARE).isEmpty(), "she is gone once the flag is set");
    }

    @Test
    void anActorWithNoMatchingEntryIsNotInTheWorld() {
        StagingConfig config = new StagingConfig();
        CastEntry later = cast("rowan", SQUARE, "well_side", "rowan_square");
        later.when = List.of("a1.never_set");
        config.cast.add(later);
        stage.useStaging("act1", config);

        assertTrue(stage.stagedFor(SQUARE).isEmpty());
    }

    // --- solidity -----------------------------------------------------------------------------

    @Test
    void solidityIsRederivedFromFlags() {
        StagingConfig config = new StagingConfig();
        CastEntry guard = cast("rowan", PATH, "stone_row", "rowan_worried");
        guard.solid = true;
        guard.solid_unless = List.of("a1.rowan_stood_aside");
        config.cast.add(guard);
        stage.useStaging("act1", config);

        assertTrue(only(PATH).solid(), "he bars the path");
        flags.setFlag("a1.rowan_stood_aside");
        assertFalse(only(PATH).solid(), "the same entry now lets you by — no reload, no second entry");
    }

    @Test
    void clearedObstacleStaysCleared() {
        StagingConfig config = new StagingConfig();
        ObstacleEntry herbs = new ObstacleEntry();
        herbs.id = "hall_overgrowth";
        herbs.at = at(SQUARE, "hall_north");
        herbs.solid = true;
        herbs.cleared_when = "a1.hall_cleared";
        herbs.activity = new ActivityRef();
        herbs.activity.type = "match3";
        herbs.activity.id = "house_hall_overgrowth";
        config.obstacles.add(herbs);
        stage.useStaging("act1", config);

        StagedPlacement blocking = only(SQUARE);
        assertTrue(blocking.solid());
        assertTrue(blocking.hasActivity());
        assertEquals("match3", blocking.activityType());

        flags.setFlag("a1.hall_cleared");
        assertTrue(stage.stagedFor(SQUARE).isEmpty(), "solving it opens the way for good");
    }

    // --- imperative overrides (the Ink seam) --------------------------------------------------

    @Test
    void overrideBeatsTheDeclarativeRule() {
        StagingConfig config = new StagingConfig();
        config.cast.add(cast("rowan", SQUARE, "well_side", "rowan_square"));
        stage.useStaging("act1", config);

        int before = stage.revision();
        stage.place("rowan", PATH, "stone_row");
        assertTrue(stage.revision() != before, "an override bumps the revision so the world refreshes");

        assertTrue(stage.stagedFor(SQUARE).isEmpty());
        assertEquals("stone_row", only(PATH).marker());
        assertEquals("rowan_square", only(PATH).dialogue(), "moving him doesn't change his lines");
    }

    @Test
    void removeAndRevoiceAndSetSolid() {
        StagingConfig config = new StagingConfig();
        config.cast.add(cast("wren", SQUARE, "wren_fence", "wren_rhyme"));
        stage.useStaging("act1", config);

        stage.setDialogue("wren", "wren_afraid");
        assertEquals("wren_afraid", only(SQUARE).dialogue());

        stage.setSolid("wren", true);
        assertTrue(only(SQUARE).solid());

        stage.remove("wren");
        assertTrue(stage.stagedFor(SQUARE).isEmpty());
    }

    @Test
    void anOverrideRelocatesAnObstacleRatherThanGhostingIt() {
        StagingConfig config = new StagingConfig();
        ObstacleEntry debris = new ObstacleEntry();
        debris.id = "debris";
        debris.at = at(SQUARE, "gate");
        debris.solid = true;
        config.obstacles.add(debris);
        stage.useStaging("act1", config);
        assertEquals("debris", only(SQUARE).id());

        stage.place("debris", PATH, "stone_row");
        assertTrue(stage.stagedFor(SQUARE).isEmpty(), "it must not linger on the map it left");
        assertEquals("stone_row", only(PATH).marker());
    }

    @Test
    void inkCanIntroduceAnActorTheStagingFileNeverMentions() {
        stage.useStaging("act1", new StagingConfig());
        stage.place("rowan", PATH, "stone_row");

        StagedPlacement introduced = only(PATH);
        assertEquals("rowan", introduced.id());
        assertEquals("5E8B7E", introduced.color(), "identity still comes from the actor registry");
    }

    @Test
    void isOnAnswersTheInkRead() {
        StagingConfig config = new StagingConfig();
        config.cast.add(cast("wren", SQUARE, "wren_fence", "wren_rhyme"));
        stage.useStaging("act1", config);

        assertTrue(stage.isOn("wren", SQUARE));
        assertFalse(stage.isOn("wren", PATH));
        assertFalse(stage.isOn("rowan", SQUARE));
    }

    // --- act boundaries -----------------------------------------------------------------------

    @Test
    void enteringAnActDropsThePreviousActsOverrides() {
        StagingConfig config = new StagingConfig();
        config.cast.add(cast("rowan", SQUARE, "well_side", "rowan_square"));
        stage.useStaging("act1", config);
        stage.remove("rowan");
        assertTrue(stage.stagedFor(SQUARE).isEmpty());

        // Act II's own staging is authoritative: the Act I removal does not follow him across.
        StagingConfig actTwo = new StagingConfig();
        actTwo.cast.add(cast("rowan", SQUARE, "well_side", "rowan_afterward"));
        stage.useStaging("act2", actTwo);

        assertEquals("rowan_afterward", only(SQUARE).dialogue());
    }

    // --- props --------------------------------------------------------------------------------

    @Test
    void propOverrideRevoicesMapOwnedFurniture() {
        StagingConfig config = new StagingConfig();
        PropEntry dresser = new PropEntry();
        dresser.prop = "dresser";
        dresser.dialogue = "dresser_act_two";
        config.props.add(dresser);
        stage.useStaging("act2", config);

        StagedPlacement override = stage.propOverride("dresser");
        assertNotNull(override);
        assertEquals("dresser_act_two", override.dialogue());
        assertNull(stage.propOverride("hearth"), "a prop this act says nothing about keeps its map value");
    }
}
