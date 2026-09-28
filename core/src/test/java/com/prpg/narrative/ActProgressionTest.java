package com.prpg.narrative;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prpg.config.ConfigLoader;
import com.prpg.content.ContentResolver;
import com.prpg.content.PackRegistry;
import com.prpg.content.config.PackManifest;
import com.prpg.narrative.config.NarrativeManifest;
import com.prpg.narrative.content.ActContentRegistry;
import com.prpg.narrative.content.ActContentSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The data-driven act spine. One transition distinguishes all the gate outcomes (narrative-not-ready,
 * not-owned, not-installed, advanced), proving narrative gating and entitlement/DLC gating share one
 * mechanism; and a pack's {@code provides:} feeds the catalog with no code change.
 */
class ActProgressionTest {

    /** A mutable in-memory content source so a DLC act can be "installed" mid-test. */
    private static final class FakeSource implements ActContentSource {
        final Set<String> installed = new HashSet<>();

        @Override
        public boolean has(String actId) {
            return installed.contains(actId);
        }

        @Override
        public String read(String actId) {
            return "{}";
        }
    }

    private NarrativeState state;
    private StoryVariables vars;
    private DefaultEntitlement entitlement;
    private FakeSource source;
    private ActContentRegistry registry;
    private ActProgression progression;

    @BeforeEach
    void setUp() {
        state = new NarrativeState();
        vars = new StoryVariables();
        source = new FakeSource();
        source.installed.add("act1"); // bundled; act2 and act3 are DLC
        registry = new ActContentRegistry(
                new ConfigLoader(), mock(ContentResolver.class), mock(PackRegistry.class), source);
        registry.useManifest(manifest());
        entitlement = new DefaultEntitlement(vars, source, registry);
        progression = new ActProgression(state, entitlement, registry);
    }

    private static NarrativeManifest manifest() {
        NarrativeManifest m = new NarrativeManifest();
        String[] ids = {"act3", "act1", "act2"}; // deliberately unordered: order decides, not position
        int[] orders = {3, 1, 2};
        for (int i = 0; i < ids.length; i++) {
            NarrativeManifest.ActEntry e = new NarrativeManifest.ActEntry();
            e.id = ids[i];
            e.order = orders[i];
            e.entitlement = "act1".equals(ids[i]) ? "FREE" : "PAID";
            m.acts.add(e);
        }
        return m;
    }

    @Test
    void newGameStartsAtTheLowestOrderedAct() {
        assertNull(state.getCurrentActId(), "no act until a game begins");
        progression.beginNewGame();
        assertEquals("act1", progression.currentActId());
        assertTrue(state.isActUnlocked("act1"));
        assertFalse(state.isActUnlocked("act2"));
    }

    @Test
    void advanceGoesThroughEveryGateOutcome() {
        progression.beginNewGame();

        // act2 not narratively unlocked yet.
        assertEquals(GateResult.BLOCKED_NARRATIVE, progression.evaluateAdvance());
        assertEquals(GateResult.BLOCKED_NARRATIVE, progression.requestAdvance());
        assertEquals("act1", progression.currentActId(), "a blocked advance changes nothing");

        // Unlocked narratively, but PAID, not mounted and not purchased: the paywall.
        state.unlockAct("act2");
        assertEquals(GateResult.BLOCKED_NOT_OWNED, progression.requestAdvance());

        // Purchase grants entitlement, but the DLC pack isn't installed yet.
        entitlement.grant("act2");
        assertEquals(GateResult.BLOCKED_NOT_INSTALLED, progression.requestAdvance());

        // Pack delivered -> advances.
        source.installed.add("act2");
        assertEquals(GateResult.ADVANCED, progression.requestAdvance());
        assertEquals("act2", progression.currentActId());

        // Mounting act3 makes it owned (no-enforcement policy) but it's still narratively locked.
        source.installed.add("act3");
        assertEquals(GateResult.BLOCKED_NARRATIVE, progression.requestAdvance());
        state.unlockAct("act3");
        assertEquals(GateResult.ADVANCED, progression.requestAdvance());
        assertEquals("act3", progression.currentActId());

        // End of the spine.
        assertEquals(GateResult.COMPLETE, progression.requestAdvance());
    }

    @Test
    void mountedActIsOwnedWithoutPurchaseFlag() {
        assertFalse(entitlement.owns("act2"), "not owned before its pack is mounted");
        source.installed.add("act2");
        assertTrue(entitlement.owns("act2"), "owned once mounted (no entitlement enforcement)");
        assertTrue(entitlement.owns("act1"), "catalogued FREE acts are always owned");
    }

    @Test
    void entitlementGrantIsAProfileVariableThatSurvivesANewGame() {
        entitlement.grant("act2");
        assertTrue(vars.isTrue(DefaultEntitlement.OWNS_PREFIX + "act2"));
        vars.clearRun();
        assertTrue(entitlement.owns("act2"), "meta_ state survives a new playthrough");
    }

    @Test
    void selfDeclaringPackIsFoldedIntoCatalog() {
        PackRegistry packReg = mock(PackRegistry.class);
        PackManifest pm = new PackManifest();
        PackManifest.Provided bonus = new PackManifest.Provided();
        bonus.id = "act7";
        bonus.title = "The Bonus";
        bonus.order = 7;
        pm.provides = List.of(bonus);
        when(packReg.manifests()).thenReturn(List.of(pm));

        ActContentRegistry reg = new ActContentRegistry(
                new ConfigLoader(), mock(ContentResolver.class), packReg, source);
        reg.useManifest(manifest()); // catalog declares act1/act2/act3 only

        assertTrue(reg.isDeclared("act7"), "pack provides: is merged into the catalog");
        assertEquals("The Bonus", reg.entry("act7").title);
        assertEquals("act7", reg.nextActId("act3"), "the folded act joins the ordered spine");
        assertNull(reg.nextActId("act7"));
    }

    @Test
    void anActBeginsWhereItsStorysEntryTagSays() {
        ActContentRegistry reg = InkTestSupport.registry(InkTestSupport.sourceFor("act1", "act2"), "act1", "act2");
        assertEquals("gatehouse_yard", reg.entry("act1").entry_map);
        assertEquals("start", reg.entry("act1").entry_spawn);
        assertEquals("road", reg.entry("act2").entry_map);
    }

    @Test
    void entryTagParsing() {
        assertArrayEquals(new String[]{"yard", "gate"}, ActContentRegistry.parseEntryTag(List.of("entry: yard gate")));
        assertArrayEquals(new String[]{"yard", null}, ActContentRegistry.parseEntryTag(List.of("title: x", "entry:yard")));
        assertNull(ActContentRegistry.parseEntryTag(List.of("entry:")));
        assertNull(ActContentRegistry.parseEntryTag(List.of("author: someone")));
    }
}
