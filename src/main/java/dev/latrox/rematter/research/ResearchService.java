package dev.latrox.rematter.research;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.compat.TeamAccess;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.primal.PrimalCells;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

/** Integration boundary for manual work and every automation adapter. All arguments come from server machine state. */
public final class ResearchService {
    private ResearchService() {}
    public static ResearchLedger ledger(MinecraftServer server) { return ResearchSavedData.get(server).ledger(); }
    public static boolean financialReady(MinecraftServer server) {
        return ledger(server).writable() && ResearchAuthority.healthy(server);
    }
    public static boolean available(MinecraftServer server) {
        return !ResearchConfig.enabled() || ledger(server).writable() && ResearchAuthority.healthy(server);
    }
    public static ResearchContext context(MinecraftServer server, UUID owner) {
        var own = ResearchIdentity.player(owner); var readable = new ArrayList<ResearchIdentity>(); readable.add(own);
        ResearchIdentity identity = own;
        var team = TeamAccess.currentTeam(owner);
        if (team.isPresent()) { identity = ResearchIdentity.team(team.get()); readable.add(identity); }
        if (ResearchConfig.sharing() == ResearchConfig.Sharing.SERVER) { identity = ResearchIdentity.SERVER; readable.add(identity); }
        return new ResearchContext(owner, identity, readable);
    }
    public static boolean realOwner(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null) return false;
        var player = server.getPlayerList().getPlayer(owner);
        return !(player instanceof FakePlayer) && (player != null || !ledger(server).visited(owner).isEmpty());
    }
    /** The explicit ordinary synthesis allowlist remains an independent mandatory permission. */
    public static boolean allowsSynthesis(MinecraftServer server, @Nullable UUID owner, String itemOrFluid) {
        var chemistry = ReMatter.chemistry();
        boolean item = chemistry.synthesisPolicy().allowsItem(itemOrFluid);
        boolean fluid = chemistry.synthesisPolicy().allowsFluid(itemOrFluid);
        if (!item && !fluid) return false;
        if (!ResearchConfig.enabled()) return true;
        if (!realOwner(server, owner) || !ledger(server).writable() || !ResearchAuthority.healthy(server)) return false;
        var context = context(server, owner);
        if (item) return ResearchData.current().forItem(itemOrFluid).map(f -> f.startingKnowledge() || ledger(server).knows(context, f.id())).orElse(false);
        return chemistry.fluidProfile(itemOrFluid).map(profile -> knownSubstances(server, owner).containsAll(profile.matter().keySet())).orElse(false);
    }
    public static boolean allowsSynthesis(ServerLevel level, @Nullable UUID owner, String itemOrFluid) { return allowsSynthesis(level.getServer(), owner, itemOrFluid); }
    public static Set<String> knownFamilies(MinecraftServer server, @Nullable UUID owner) {
        if (!realOwner(server, owner)) return Set.of();
        var result = new java.util.HashSet<>(ledger(server).knownFamilies(context(server, owner)));
        ResearchData.current().families().stream().filter(MaterialResearch::startingKnowledge).forEach(f -> result.add(f.id())); return Set.copyOf(result);
    }
    public static Set<SubstanceKey> knownSubstances(MinecraftServer server, @Nullable UUID owner) {
        if (!realOwner(server, owner)) return Set.of();
        var result = new java.util.HashSet<>(ledger(server).knownSubstances(context(server, owner)));
        ResearchData.current().families().stream().filter(MaterialResearch::startingKnowledge).forEach(f -> result.addAll(f.substances())); return Set.copyOf(result);
    }
    public static Set<SubstanceKey> knownSubstances(ServerLevel level, @Nullable UUID owner) { return knownSubstances(level.getServer(), owner); }
    public static boolean learn(MinecraftServer server, @Nullable UUID owner, String itemId, LearningPath path) {
        if (!realOwner(server, owner) || !ledger(server).writable()) return false;
        var family = ResearchData.current().forItem(itemId);
        if (family.isPresent()) {
            boolean learned = ledger(server).learn(context(server, owner), family.get(), path);
            if (learned && path.earned()) dev.latrox.rematter.network.ResearchNotifications.emitLearned(server, owner, family.get());
            return learned;
        }
        var fluid = ReMatter.chemistry().fluidProfile(itemId);
        if (fluid.isEmpty()) return false;
        var learned = fluid.get().matter().keySet(); boolean changed = !knownSubstances(server, owner).containsAll(learned);
        ledger(server).learnSubstances(context(server, owner), learned); return changed;
    }
    public static boolean learn(ServerLevel level, @Nullable UUID owner, String itemId, LearningPath path) { return learn(level.getServer(), owner, itemId, path); }
    /** Reactor outputs teach only when every exact input substance was already known; imported contents never call this. */
    public static boolean learnProcess(MinecraftServer server, @Nullable UUID owner, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        if (!realOwner(server, owner) || !ledger(server).writable() || !knownSubstances(server, owner).containsAll(inputs.keySet())
                || outputs.keySet().stream().anyMatch(key -> !ReMatter.chemistry().isKnown(key) || key.id().toString().equals("rematter:primal_matter"))) return false;
        ledger(server).learnSubstances(context(server, owner), outputs.keySet()); return true;
    }
    public static void recordIndustrial(MinecraftServer server, @Nullable UUID owner, ResearchLedger.Milestone milestone) {
        if (realOwner(server, owner) && ledger(server).writable()) ledger(server).milestone(owner, milestone);
    }
    public static void recordIndustrial(ServerLevel level, @Nullable UUID owner, ResearchLedger.Milestone milestone) { recordIndustrial(level.getServer(), owner, milestone); }
    public record MaterialView(String id, String name, Set<String> probeIds, Set<SubstanceKey> substances,
                               boolean known, boolean canDerive, String learningPath, long derivePrimal, int discoveryTier) {}
    public static java.util.List<MaterialView> materials(MinecraftServer server, UUID owner) {
        if (!realOwner(server, owner)) return java.util.List.of();
        var context = context(server, owner); var data = ledger(server); var known = knownSubstances(server, owner);
        return ResearchData.current().families().stream().map(f -> {
            var path = data.path(context, f.id());
            boolean researched = f.startingKnowledge() || data.knows(context, f.id());
            return new MaterialView(f.id(), f.name(), f.items(), f.substances(), researched,
                    !researched && f.derive() && known.containsAll(f.substances()), path == null ? "" : path.name().toLowerCase(java.util.Locale.ROOT),
                    f.derivePrimal(), f.discoveryTier() == null ? 0 : f.discoveryTier().ordinal() + 1);
        }).toList();
    }
    public static boolean known(MinecraftServer server, UUID owner, String itemOrFluid) {
        var family = ResearchData.current().forItem(itemOrFluid);
        if (family.isPresent()) return knownFamilies(server, owner).contains(family.get().id());
        return ReMatter.chemistry().fluidProfile(itemOrFluid).map(f -> knownSubstances(server, owner).containsAll(f.matter().keySet())).orElse(false);
    }
    public static long analysisEnergy(MaterialProfile profile) { return Math.multiplyExact(profile.decompositionEnergy(), 2); }
    public static int analysisTicks(MaterialProfile profile) { return Math.multiplyExact(profile.ticks(), 4); }
    /** Explicit administrative grant is allowed to establish an offline test/quest owner; it never counts as earned play. */
    public static boolean adminGrant(MinecraftServer server, UUID owner, String familyId) {
        var family = ResearchData.current().family(familyId);
        if (family.isEmpty() || !ledger(server).writable()) return false;
        ledger(server).visit(owner, "minecraft:overworld");
        return ledger(server).learn(context(server, owner), family.get(), LearningPath.ADMIN);
    }
    /** Mandatory inside a successful session Payment/Refund adapter, before applying its prepared physical cell write. */
    public static void recordCellPayment(MinecraftServer server, UUID owner, PrimalCells.Payment payment, long signedDelta, TransactionContext tx) {
        if (!server.isSameThread() || !realOwner(server, owner) || !ResearchAuthority.healthy(server)) throw new IllegalStateException("Research authority unavailable");
        ledger(server).recordCellChange(new ResearchLedger.CellChange(payment.cellId(), payment.operationId(), signedDelta, context(server, owner).identity()), tx);
    }
    public static java.util.List<ResearchLedger.CellChange> cellChanges(MinecraftServer server, UUID cellId) {
        return ledger(server).changesForCell(cellId);
    }
    public static Optional<ResearchLedger.CellChange> cellPayment(MinecraftServer server, UUID operation) {
        return Optional.ofNullable(ledger(server).cellChanges.get(operation));
    }
    public static void recordEnergyPayment(MinecraftServer server, UUID owner, UUID machine, UUID operation, long energy, TransactionContext tx) {
        if (!server.isSameThread() || !realOwner(server, owner) || !ResearchAuthority.healthy(server)) throw new IllegalStateException("Research authority unavailable");
        ledger(server).recordEnergyChange(new ResearchLedger.EnergyChange(machine, operation, owner, energy), tx);
    }
    public static java.util.List<ResearchLedger.EnergyChange> energyChanges(MinecraftServer server, UUID machine) {
        return ledger(server).energyChanges.values().stream().filter(change -> change.machine().equals(machine)).toList();
    }
    public static Map<UUID, Long> outstandingEnergy(MinecraftServer server, UUID machine, Map<UUID, Long> localSettledReceipts) {
        return ledger(server).outstandingEnergy(machine, localSettledReceipts);
    }
    public static Optional<ConversionClaims.View> commitConversion(MinecraftServer server, UUID owner, UUID machine, UUID operation,
            SubstanceKey target, long units, long actualEnergy, ResearchSessions.Payment payment) {
        if (!server.isSameThread() || !realOwner(server, owner) || !ledger(server).writable() || !ResearchAuthority.healthy(server)) return Optional.empty();
        var known = knownSubstances(server, owner);
        try {
            var quote = dev.latrox.rematter.primal.PrimalPricing.quote(ReMatter.chemistry(), target, units, id -> known.stream().anyMatch(key -> key.id().equals(id)));
            return new ConversionClaims(ledger(server)).commit(owner, machine, operation, quote, actualEnergy, payment);
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    public static java.util.List<ConversionClaims.View> conversionClaims(MinecraftServer server, UUID owner, UUID machine) {
        return new ConversionClaims(ledger(server)).claims(owner, machine);
    }
    public static long claimConversion(MinecraftServer server, UUID owner, UUID claim, long maximum, ConversionClaims.OrdinaryOutput output) {
        if (!server.isSameThread() || !realOwner(server, owner) || !ResearchAuthority.healthy(server)) return 0;
        return new ConversionClaims(ledger(server)).claim(owner, claim, maximum, output);
    }
    public static java.util.List<ConversionClaims.OrdinaryCredit> ordinaryCredits(MinecraftServer server, UUID cellId) {
        return new ConversionClaims(ledger(server)).credits(cellId);
    }
    public static Optional<ConversionClaims.OrdinaryState> reconcileOrdinaryCell(MinecraftServer server, UUID cellId,
            Map<SubstanceKey, Long> rawMatter, Map<UUID, ConversionClaims.OrdinaryReceipt> rawReceipts, long capacity, int typeLimit) {
        if (!ResearchAuthority.healthy(server)) return Optional.empty();
        return new ConversionClaims(ledger(server)).reconcile(cellId, rawMatter, rawReceipts, capacity, typeLimit);
    }
    public static ResearchSessions researchSession(MinecraftServer server) {
        return new ResearchSessions(ledger(server), ResearchData.current(), ResearchConfig.discoveryMinimum(), java.util.concurrent.ThreadLocalRandom.current(),
                (context, family) -> dev.latrox.rematter.network.ResearchNotifications.emitLearned(server, context.owner(), family));
    }
}
