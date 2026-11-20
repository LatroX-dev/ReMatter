package dev.latrox.rematter.research;

import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchLedgerTest {
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final SubstanceKey SILICA = SubstanceKey.of("rematter:silica", Phase.SOLID);
    static MaterialResearch family(String name, DiscoveryTier tier, String dimension) {
        return new MaterialResearch("rematter:" + name, name, Set.of("minecraft:" + name), Set.of(SILICA), true, false, tier, dimension, 4_000);
    }
    @Test void teamReadAndDepartureNeverCopySharedKnowledgeIntoPersonalState() {
        var ledger = new ResearchLedger(); var team = ResearchIdentity.team(UUID.randomUUID());
        var other = UUID.randomUUID(); var peer = new ResearchContext(other, team, List.of(ResearchIdentity.player(other), team));
        var context = new ResearchContext(OWNER, team, List.of(ResearchIdentity.player(OWNER), team));
        ledger.learn(peer, family("quartz", DiscoveryTier.I, ""), LearningPath.ANALYSIS);
        var before = ledger.toJson();
        assertTrue(ledger.knows(context, "rematter:quartz")); assertEquals(Set.of(SILICA), ledger.knownSubstances(context));
        assertEquals(before, ledger.toJson(), "Knowledge reads must be read-only");
        assertFalse(ledger.knows(ResearchContext.personal(OWNER), "rematter:quartz"));
        assertTrue(ledger.knows(peer, "rematter:quartz"));
    }
    @Test void ownTeamWorkRemainsPersonalAndTeamKeepsContribution() {
        var ledger = new ResearchLedger(); var team = ResearchIdentity.team(UUID.randomUUID());
        var ctx = new ResearchContext(OWNER, team, List.of(ResearchIdentity.player(OWNER), team));
        ledger.learn(ctx, family("sand", DiscoveryTier.I, ""), LearningPath.DECOMPOSITION);
        assertTrue(ledger.knows(ResearchContext.personal(OWNER), "rematter:sand"));
        var peer = UUID.randomUUID(); assertTrue(ledger.knows(new ResearchContext(peer, team, List.of(ResearchIdentity.player(peer), team)), "rematter:sand"));
    }
    @Test void membershipContributionCopiesOnlyPersonallyOwnedKnowledge() {
        var ledger = new ResearchLedger(); var ctx = ResearchContext.personal(OWNER);
        ledger.learn(ctx, family("sand", DiscoveryTier.I, ""), LearningPath.ANALYSIS);
        var first = ResearchIdentity.team(UUID.randomUUID()); ledger.contributePersonal(OWNER, first);
        var peer = UUID.randomUUID(); var peerContext = new ResearchContext(peer, first, List.of(ResearchIdentity.player(peer), first));
        ledger.learn(peerContext, family("quartz", DiscoveryTier.I, ""), LearningPath.ANALYSIS);
        var next = ResearchIdentity.team(UUID.randomUUID()); ledger.contributePersonal(OWNER, next);
        var nextContext = new ResearchContext(peer, next, List.of(ResearchIdentity.player(peer), next));
        assertTrue(ledger.knows(nextContext, "rematter:sand"));
        assertFalse(ledger.toJson().getAsJsonObject("knowledge").getAsJsonObject(next.key()).getAsJsonObject("families").has("rematter:quartz"));
    }
    @Test void giftsDoNotSatisfyEarnedMinimumAndMilestonesBelongToOwner() {
        var ledger = new ResearchLedger(); var ctx = ResearchContext.personal(OWNER);
        ledger.learn(ctx, family("sand", DiscoveryTier.I, ""), LearningPath.ADMIN);
        ledger.milestone(OWNER, ResearchLedger.Milestone.INDUSTRIAL_REACTOR); ledger.milestone(OWNER, ResearchLedger.Milestone.INDUSTRIAL_PREPARATOR);
        assertFalse(ledger.discoveryAllowed(ctx, 1));
        ledger.learn(ctx, family("sand", DiscoveryTier.I, ""), LearningPath.ANALYSIS);
        assertTrue(ledger.discoveryAllowed(ctx, 1)); assertEquals(1, ledger.earnedFamilies(ctx));
        assertFalse(ledger.discoveryAllowed(ResearchContext.personal(UUID.randomUUID()), 0));
    }
    @Test void stableVersionedRoundTripRetainsVisitsKnowledgeAndUnknownSchemas() {
        var ledger = new ResearchLedger(); var ctx = ResearchContext.personal(OWNER);
        ledger.visit(OWNER, "minecraft:the_nether"); ledger.learn(ctx, family("sand", DiscoveryTier.I, ""), LearningPath.ASSAY);
        var json = ledger.toJson(); var loaded = ResearchLedger.fromJson(json);
        assertEquals(json, loaded.toJson()); assertEquals(LearningPath.ASSAY, loaded.path(ctx, "rematter:sand"));
        var future = JsonParser.parseString("{\"schema\":12,\"unrecognized\":{\"amount\":9007199254740993}}").getAsJsonObject();
        var unknown = ResearchLedger.fromJson(future);
        assertFalse(unknown.writable()); assertEquals(future, unknown.toJson());
        assertThrows(IllegalStateException.class, () -> unknown.visit(OWNER, "minecraft:overworld"));
        assertEquals(future, unknown.toJson());
    }
    @Test void derivePriceUsesSafeClampBeforeOverflow() {
        assertEquals(4_000, MaterialResearch.derivePrice(1)); assertEquals(6_000, MaterialResearch.derivePrice(1_500));
        assertEquals(16_000, MaterialResearch.derivePrice(Long.MAX_VALUE)); assertThrows(IllegalArgumentException.class, () -> MaterialResearch.derivePrice(0));
    }
}
