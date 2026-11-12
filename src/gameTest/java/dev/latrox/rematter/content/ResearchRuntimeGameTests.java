package dev.latrox.rematter.content;

import com.google.gson.JsonObject;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.network.MachineActionPayload;
import dev.latrox.rematter.primal.*;
import dev.latrox.rematter.research.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Real machines and server actions in disposable GameTest batches; no production test-only bypasses. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ResearchRuntimeGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("research_runtime_analysis_unlocks_without_consuming_probe", ResearchRuntimeGameTests::analysis);
        TESTS.put("research_runtime_derive_pays_cell_and_energy", ResearchRuntimeGameTests::derive);
        TESTS.put("research_runtime_discovery_resumes_and_never_duplicates", ResearchRuntimeGameTests::discovery);
        TESTS.put("research_runtime_exhausted_pool_refunds_once", ResearchRuntimeGameTests::refund);
        TESTS.put("research_runtime_converter_claim_and_cell_watermark", ResearchRuntimeGameTests::converter);
        TESTS.put("research_runtime_physical_checkpoint_and_energy_debt", ResearchRuntimeGameTests::checkpointDebt);
        TESTS.put("research_runtime_post_publication_failure_recovers_payment", ResearchRuntimeGameTests::postPublicationFailure);
        TESTS.put("research_runtime_malformed_financial_ids_preserved", ResearchRuntimeGameTests::malformedIdentity);
    }
    private ResearchRuntimeGameTests() {}
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> TESTS.forEach((name, body) -> registry.register(id(name), body)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        TESTS.forEach((name, body) -> {
            // A separate environment per function gives each manual clock simulation its own serial batch.
            var environment = event.registerEnvironment(id(name + "_batch"));
            event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                    new TestData<>(environment, id("empty"), 1_200, 0, true, Rotation.NONE, false, 1, 1, true, 2)));
        });
    }
    private record Rig(GameTestHelper helper, Player owner, MachineBlockEntity drive, MachineBlockEntity machine) {
        UUID uuid() { return owner.getUUID(); }
        net.minecraft.server.MinecraftServer server() { return helper.getLevel().getServer(); }
        ResearchContext context() { return ResearchService.context(server(), uuid()); }
    }
    private static Rig rig(GameTestHelper h, MachineKind kind) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        // Explicit QA ownership evidence; an imported cell is deliberately not a learning hook.
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        var drive = place(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        var machine = place(h, new BlockPos(3, 2, 2), kind, owner);
        machine.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        return new Rig(h, owner, drive, machine);
    }
    private static MachineBlockEntity place(GameTestHelper h, BlockPos relative, MachineKind kind, Player owner) {
        h.setBlock(relative, ModContent.MACHINES.get(kind).get());
        var machine = h.getBlockEntity(relative, MachineBlockEntity.class); machine.placedBy(owner); return machine;
    }
    private static void malformedIdentity(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        int index=0;
        for(String key:List.of("extension_id","research_session","conversion_id","checkpointed_cells")){
            var machine=place(h,new BlockPos(2+index++,2,2),MachineKind.PRIMAL_CONVERTER,owner);
            var raw=machine.saveCustomOnly(h.getLevel().registryAccess());
            if(key.equals("checkpointed_cells")){
                var ids=new net.minecraft.nbt.ListTag();ids.add(net.minecraft.nbt.StringTag.valueOf("1-1-1-1-1"));raw.put(key,ids);
            }else raw.putString(key,"1-1-1-1-1");
            machine.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),raw));
            h.assertTrue(!machine.networkLive(),"Malformed financial identity did not lock: "+key);
            h.assertTrue(machine.saveCustomOnly(h.getLevel().registryAccess()).equals(raw),"Unknown financial data was rewritten: "+key);
        }
        h.succeed();
    }
    private static void fuel(MachineBlockEntity machine, long amount, boolean lens) {
        var stack = new ItemStack(PrimalContent.PRIMAL_CELL_64K.get());
        stack.set(PrimalCellData.TYPE.get(), PrimalCellData.from(new PrimalCellData.State(UUID.randomUUID(), amount, 0, Map.of())));
        machine.setItem(MachineCellAccess.PRIMAL_SLOT, stack);
        if (lens) machine.setItem(MachineCellAccess.DISCOVERY_SLOT, new ItemStack(PrimalContent.DISCOVERY_LENS.get()));
    }
    private static boolean action(MachineBlockEntity machine, MachineActionPayload.Action action, String target, long amount) {
        return machine.extensions().action(new MachineActionPayload(0, action, target, amount));
    }
    private static void tick(GameTestHelper h, MachineBlockEntity... machines) {
        var level = h.getLevel();
        ((ServerLevelData) level.getLevelData()).setGameTime(Math.incrementExact(level.getGameTime()));
        for (var machine : machines) machine.serverTick();
    }
    private static void ticks(GameTestHelper h, int count, MachineBlockEntity... machines) { for (int i = 0; i < count; i++) tick(h, machines); }
    private static ResearchSessions.View session(Rig r, MachineBlockEntity machine) {
        var json = machine.extensions().view().getAsJsonObject("research");
        return ResearchService.researchSession(r.server()).session(r.context(), UUID.fromString(json.get("id").getAsString())).orElseThrow();
    }
    private static long primal(MachineBlockEntity machine) { return PrimalCells.view(machine.getItem(MachineCellAccess.PRIMAL_SLOT)).amount(); }
    private static void reload(MachineBlockEntity machine, CompoundTag state) {
        machine.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, machine.getLevel().registryAccess(), state));
    }
    private static void assertTrue(GameTestHelper h, boolean value, String message) { h.assertTrue(value, message); }
    private static void analysis(GameTestHelper h) {
        var r = rig(h, MachineKind.ANALYZER);
        assertTrue(h, r.drive.matterStore().apply(Map.of(), Map.of(BuiltinChemistry.SILICA, 1_000L)), "Stock fixture did not fit");
        var synth = place(h, new BlockPos(1, 2, 2), MachineKind.SYNTHESIZER, r.owner); synth.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        synth.selectRecipe("minecraft:glass"); synth.requestBatch(); ticks(h, 120, synth);
        assertTrue(h, !ResearchService.allowsSynthesis(r.server(), r.uuid(), "minecraft:glass") && synth.getItem(1).isEmpty(), "Imported stock unlocked unresearched synthesis");
        assertTrue(h, synth.energy().getAmountAsLong() == MachineBlockEntity.ENERGY_CAPACITY && r.drive.matterStore().amount(BuiltinChemistry.SILICA) == 1_000, "A locked synthesis consumed resources");
        var profile = ReMatter.chemistry().profile("minecraft:sand").orElseThrow();
        r.machine.setItem(0, new ItemStack(Items.SAND, 3)); long before = r.machine.energy().getAmountAsLong();
        ticks(h, ResearchService.analysisTicks(profile), r.machine);
        assertTrue(h, r.machine.getItem(0).getCount() == 3 && r.machine.getItem(0).is(Items.SAND), "Analysis consumed its probe");
        assertTrue(h, before - r.machine.energy().getAmountAsLong() == ResearchService.analysisEnergy(profile), "Analysis did not pay its exact price");
        assertTrue(h, ResearchService.allowsSynthesis(r.server(), r.uuid(), "minecraft:glass"), "Sand analysis failed to unlock its glass family");
        long paid = r.machine.energy().getAmountAsLong(); tick(h, r.machine);
        assertTrue(h, r.machine.energy().getAmountAsLong() == paid, "Known analysis charged again");
        ticks(h, ReMatter.chemistry().profile("minecraft:glass").orElseThrow().ticks() + 2, synth);
        assertTrue(h, synth.getItem(1).is(Items.GLASS) && r.drive.matterStore().amount(BuiltinChemistry.SILICA) == 0, "Researched synthesis did not complete exactly once"); h.succeed();
    }
    private static Rig deriveRig(GameTestHelper h) {
        var r = rig(h, MachineKind.ANALYZER);
        ResearchService.adminGrant(r.server(), r.uuid(), "rematter:stone"); fuel(r.machine, 16_000, false);
        assertTrue(h, !ResearchService.knownFamilies(r.server(), r.uuid()).contains("rematter:granite"), "Derive target is already known");
        assertTrue(h, action(r.machine, MachineActionPayload.Action.DERIVE, "rematter:granite", 0), "Server rejected legitimate derivation"); return r;
    }
    private static void derive(GameTestHelper h) {
        var r = deriveRig(h); var family = ResearchData.current().family("rematter:granite").orElseThrow(); long energy = r.machine.energy().getAmountAsLong();
        ticks(h, ResearchSessions.DERIVE_TICKS, r.machine); var view = session(r, r.machine);
        assertTrue(h, view.state() == ResearchSessions.State.COMPLETE && view.resultFamily().equals(family.id()), "Derivation did not finish the selected family");
        assertTrue(h, primal(r.machine) == 16_000 - family.derivePrimal(), "Derivation charged the wrong Primal amount");
        assertTrue(h, energy - r.machine.energy().getAmountAsLong() == ResearchSessions.DERIVE_ENERGY, "Derivation charged the wrong FE amount");
        assertTrue(h, !action(r.machine, MachineActionPayload.Action.DERIVE, family.id(), 0), "Known family accepted another paid derivation"); h.succeed();
    }
    private static long discoveryTicks(GameTestHelper h, MachineBlockEntity machine, int ticks) {
        long spent = 0;
        for (int i = 0; i < ticks; i++) {
            if (machine.energy().getAmountAsLong() < DiscoveryTier.STEP_ENERGY) machine.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
            long before = machine.energy().getAmountAsLong(); tick(h, machine); spent = Math.addExact(spent, before - machine.energy().getAmountAsLong());
        }
        return spent;
    }
    private static void discovery(GameTestHelper h) {
        var r = rig(h, MachineKind.ANALYZER); fuel(r.machine, 64_000, true);
        assertTrue(h, !action(r.machine, MachineActionPayload.Action.DISCOVER, "", 1), "Discovery ignored its real progression gate");
        // The production operator override explicitly supports test worlds and quest rewards; no hidden test bypass.
        ResearchService.ledger(r.server()).overrideDiscovery(r.uuid(), true);
        var engine = ResearchService.researchSession(r.server()); int possible = engine.available(r.context(), DiscoveryTier.I);
        assertTrue(h, possible >= 2 && action(r.machine, MachineActionPayload.Action.DISCOVER, "", 1), "Eligible Discovery did not start");
        long charged = discoveryTicks(h, r.machine, 1); var first = session(r, r.machine);
        assertTrue(h, first.paidSteps() == 1 && first.resultFamily().isEmpty(), "Unpaid/surprise result was exposed");
        assertTrue(h, action(r.machine, MachineActionPayload.Action.RESEARCH_PAUSE, "", 0), "Pause failed");
        var saved = r.machine.saveCustomOnly(h.getLevel().registryAccess()); reload(r.machine, saved);
        var secondAnalyzer = place(h, new BlockPos(2, 2, 3), MachineKind.ANALYZER, r.owner); secondAnalyzer.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        var paymentCell = r.machine.getItem(MachineCellAccess.PRIMAL_SLOT); r.machine.setItem(MachineCellAccess.PRIMAL_SLOT, ItemStack.EMPTY);
        secondAnalyzer.setItem(MachineCellAccess.PRIMAL_SLOT, paymentCell); secondAnalyzer.setItem(MachineCellAccess.DISCOVERY_SLOT, new ItemStack(PrimalContent.DISCOVERY_LENS.get()));
        assertTrue(h, action(secondAnalyzer, MachineActionPayload.Action.RESEARCH_RESUME, first.id().toString(), 0), "Different Analyzer could not resume its identity's session");
        charged += discoveryTicks(h, secondAnalyzer, DiscoveryTier.I.minimumTicks() - 1);
        var complete = session(r, secondAnalyzer);
        assertTrue(h, complete.id().equals(first.id()) && complete.state() == ResearchSessions.State.COMPLETE, "Reload/device change rerolled or failed the session");
        assertTrue(h, complete.paidSteps() == 4 && charged == DiscoveryTier.I.energy() && primal(secondAnalyzer) == 48_000, "6000 active ticks did not pay exactly2.4MFE/16kPrimal");
        assertTrue(h, ResearchService.knownFamilies(r.server(), r.uuid()).contains(complete.resultFamily()), "Discovery result was not learned");
        assertTrue(h, engine.available(r.context(), DiscoveryTier.I) == possible - 1, "Known Discovery result remained in its pool");
        assertTrue(h, action(secondAnalyzer, MachineActionPayload.Action.DISCOVER, "", 1), "Second Discovery did not start");
        long nextEnergy = discoveryTicks(h, secondAnalyzer, DiscoveryTier.I.minimumTicks()); var next = session(r, secondAnalyzer);
        assertTrue(h, next.state() == ResearchSessions.State.COMPLETE && !next.resultFamily().equals(complete.resultFamily()), "Discovery awarded a duplicate");
        assertTrue(h, nextEnergy == DiscoveryTier.I.energy() && primal(secondAnalyzer) == 32_000, "Second Discovery did not pay its independent price"); h.succeed();
    }
    private static void refund(GameTestHelper h) {
        var r = rig(h, MachineKind.ANALYZER); fuel(r.machine, 64_000, true); ResearchService.ledger(r.server()).overrideDiscovery(r.uuid(), true);
        assertTrue(h, action(r.machine, MachineActionPayload.Action.DISCOVER, "", 1), "Discovery fixture did not start"); discoveryTicks(h, r.machine, 1);
        for (var family : ResearchData.current().families()) if (family.discoveryTier() == DiscoveryTier.I) ResearchService.adminGrant(r.server(), r.uuid(), family.id());
        tick(h, r.machine); var engine = ResearchService.researchSession(r.server());
        assertTrue(h, engine.refundDue(r.context()) == 4_000, "Exhausted pool did not create exact paid refund");
        var oldPaymentCell = r.machine.getItem(MachineCellAccess.PRIMAL_SLOT); fuel(r.machine, 64_000, true);
        assertTrue(h, !action(r.machine, MachineActionPayload.Action.RESEARCH_REFUND, "", 0), "Full cell accepted refund");
        assertTrue(h, engine.refundDue(r.context()) == 4_000, "Full cell lost its refund claim");
        r.machine.setItem(MachineCellAccess.PRIMAL_SLOT, oldPaymentCell);
        assertTrue(h, action(r.machine, MachineActionPayload.Action.RESEARCH_REFUND, "", 0), "Valid return cell did not receive refund");
        assertTrue(h, primal(r.machine) == 64_000 && engine.refundDue(r.context()) == 0, "Refund amount was wrong");
        assertTrue(h, !action(r.machine, MachineActionPayload.Action.RESEARCH_REFUND, "", 0) && primal(r.machine) == 64_000, "Refund paid twice");
        assertTrue(h, r.machine.energy().getAmountAsLong() == MachineBlockEntity.ENERGY_CAPACITY - DiscoveryTier.STEP_ENERGY, "Refund restored spent FE"); h.succeed();
    }
    private static void converter(GameTestHelper h) {
        var r = rig(h, MachineKind.PRIMAL_CONVERTER); fuel(r.machine, 16_000, false); ResearchService.adminGrant(r.server(), r.uuid(), "rematter:iron");
        assertTrue(h, action(r.machine, MachineActionPayload.Action.CONVERT_SELECT, "rematter:iron", 1_000) && action(r.machine, MachineActionPayload.Action.CONVERT_START, "", 0), "Converter request rejected");
        ticks(h, 400, r.machine);
        var claims = r.machine.extensions().view().getAsJsonArray("conversionClaims"); assertTrue(h, claims.size() == 1, "Converter did not create exactly one result owner");
        var claimId = UUID.fromString(claims.get(0).getAsJsonObject().get("id").getAsString()); var machineId = UUID.fromString(claims.get(0).getAsJsonObject().get("machine").getAsString());
        assertTrue(h, primal(r.machine) == 15_000 && r.machine.energy().getAmountAsLong() == MachineBlockEntity.ENERGY_CAPACITY - 64_000, "Converter payment was not exact");
        assertTrue(h, r.drive.matterStore().apply(Map.of(), Map.of(BuiltinChemistry.SOIL, 64_000L)), "Could not block output"); r.drive.setChanged();
        ticks(h, 20, r.machine);
        assertTrue(h, ResearchService.conversionClaims(r.server(), r.uuid(), machineId).getFirst().remaining() == 1_000 && primal(r.machine) == 15_000, "Blocked output lost result or paid twice");
        reload(r.machine, r.machine.saveCustomOnly(h.getLevel().registryAccess()));
        assertTrue(h, r.drive.matterStore().apply(Map.of(BuiltinChemistry.SOIL, 1_000L), Map.of()), "Could not make output room"); r.drive.setChanged();
        var stale = r.drive.getItem(r.drive.cellSlotForBay(0)).copy(); tick(h, r.machine);
        assertTrue(h, r.drive.matterStore().amount(BuiltinChemistry.IRON) == 1_000 && r.drive.matterStore().total() == 64_000, "Claim payout was not exact");
        assertTrue(h, ResearchService.conversionClaims(r.server(), r.uuid(), machineId).getFirst().remaining() == 0, "Paid output retained a live claim");
        var replay = ResearchService.commitConversion(r.server(), r.uuid(), machineId, claimId, BuiltinChemistry.IRON, 1_000, 64_000, (op, primal, energy, tx) -> { throw new IllegalStateException("Replay attempted another debit"); });
        assertTrue(h, replay.isPresent(), "Valid operation replay lost its original claim");
        r.drive.removeItemNoUpdate(r.drive.cellSlotForBay(0)); r.drive.setItem(r.drive.cellSlotForBay(0), stale);
        assertTrue(h, r.drive.matterStore().amount(BuiltinChemistry.IRON) == 1_000 && r.drive.matterStore().total() == 64_000, "Stale physical output did not reconcile once");
        var resolved = r.drive.getItem(r.drive.cellSlotForBay(0)).get(MatterCellData.TYPE.get()).resolve(64_000).orElseThrow();
        assertTrue(h, resolved.creditRevision() == 1, "Output cell did not keep its compact credit watermark");
        ticks(h, 20, r.machine); assertTrue(h, primal(r.machine) == 15_000 && r.drive.matterStore().total() == 64_000, "Idle Converter repeated payment or output"); h.succeed();
    }
    private static void checkpointDebt(GameTestHelper h) {
        var r = deriveRig(h); tick(h, r.machine); var paid = session(r, r.machine);
        assertTrue(h, paid.paidSteps() == 1, "Initial derivation did not pay");
        try {
            var disk = IdentityCheckpointReader.blockEntity(h.getLevel(), r.machine.getBlockPos()).orElseThrow();
            assertTrue(h, !disk.getStringOr("extension_id", "").isEmpty() && disk.getBooleanOr("identity_checkpoint", false), "Identity checkpoint never reached physical chunk NBT");
            long afterEnergy = r.machine.energy().getAmountAsLong(), afterPrimal = primal(r.machine);
            reload(r.machine, disk); tick(h, r.machine);
            assertTrue(h, r.machine.energy().getAmountAsLong() == afterEnergy, "Authority-new/machine-old FE was not reconciled exactly");
            assertTrue(h, primal(r.machine) == afterPrimal && session(r, r.machine).paidSteps() == 1, "Stale payment cell was charged twice or rolled back");
            assertTrue(h, session(r, r.machine).elapsedTicks() == paid.elapsedTicks() + 1, "Reload changed paid progress or rerolled the operation");
        } catch (java.io.IOException problem) { throw new IllegalStateException("Physical payment checkpoint readback failed", problem); }
        h.succeed();
    }
    private static void postPublicationFailure(GameTestHelper h){
        var r=rig(h,MachineKind.ANALYZER);fuel(r.machine,64_000,true);
        var ledger=ResearchService.ledger(r.server());ledger.overrideDiscovery(r.uuid(),true);
        assertTrue(h,action(r.machine,MachineActionPayload.Action.DISCOVER,"",3),"Discovery III fixture did not start");
        var initial=session(r,r.machine);var beforeLedger=ledger.toJson();
        var beforeCell=r.machine.getItem(MachineCellAccess.PRIMAL_SLOT).get(PrimalCellData.TYPE.get()).copyTag();
        var beforeMachine=r.machine.saveCustomOnly(h.getLevel().registryAccess());
        long beforeEnergy=r.machine.energy().getAmountAsLong();
        try(var fault=ResearchAuthorityIoFault.install(r.server())){
            RuntimeException failure=null;
            try{tick(h,r.machine);}catch(RuntimeException problem){failure=problem;}
            assertTrue(h,failure!=null&&ResearchAuthorityIoFault.MESSAGE.equals(failure.getMessage()),"The real post-publication error did not reach the caller");
            assertTrue(h,failure.getSuppressed().length==0,"FE rollback added a transaction-closing exception");
            assertTrue(h,beforeLedger.equals(ledger.toJson()),"Failed payment changed in-memory authority/session state");
            assertTrue(h,r.machine.energy().getAmountAsLong()==beforeEnergy,"Failed payment did not roll back physical FE");
            var afterMachine=r.machine.saveCustomOnly(h.getLevel().registryAccess());
            assertTrue(h,beforeMachine.getLongOr("energy_receipt_revision",0)==afterMachine.getLongOr("energy_receipt_revision",-1)
                    &&beforeMachine.getLongOr("partial_energy_debt",0)==afterMachine.getLongOr("partial_energy_debt",-1),
                    "Failed payment restored FE without restoring its receipt watermark and partial debt");
            assertTrue(h,beforeCell.equals(r.machine.getItem(MachineCellAccess.PRIMAL_SLOT).get(PrimalCellData.TYPE.get()).copyTag()),
                    "Failed payment changed physical Primal identity, amount, revision or receipts");
            assertTrue(h,session(r,r.machine).paidSteps()==0,"Aborted payment remained paid in the in-memory session");
            var durable=fault.publishedState();
            var published=new ResearchSessions(durable,ResearchData.current(),0,new Random(99)).session(r.context(),initial.id()).orElseThrow();
            assertTrue(h,published.paidSteps()==1&&published.elapsedTicks()==1&&published.resultFamily().isEmpty(),
                    "Already published authority lost the first paid hidden Discovery step");
            var stale=IdentityCheckpointReader.blockEntity(h.getLevel(),r.machine.getBlockPos()).orElseThrow();
            var machineId=UUID.fromString(stale.getStringOr("extension_id",""));
            var cellId=UUID.fromString(beforeCell.getStringOr("cell_id",""));
            assertTrue(h,durable.changesForCell(cellId).size()==1&&durable.outstandingEnergy(machineId,Map.of()).values().stream().mapToLong(Long::longValue).sum()==600_000,
                    "Published Cell/FE journals do not describe exactly one real payment");
            // Startup selects the newer durable ledger; the checkpointed controller and Cell can still be old.
            fault.recoverPublishedState();reload(r.machine,stale);tick(h,r.machine);
            var resumed=session(r,r.machine);
            assertTrue(h,resumed.id().equals(initial.id())&&resumed.paidSteps()==1&&resumed.elapsedTicks()==2,
                    "Recovered authority rerolled or repaid the existing Discovery step");
            assertTrue(h,primal(r.machine)==60_000&&r.machine.energy().getAmountAsLong()==beforeEnergy-600_000,
                    "Authority-new/controller-old restart did not settle exactly4kPrimal/600kFE");
            tick(h,r.machine);
            assertTrue(h,primal(r.machine)==60_000&&r.machine.energy().getAmountAsLong()==beforeEnergy-600_000
                    &&ResearchService.cellChanges(r.server(),cellId).size()==1&&ResearchService.energyChanges(r.server(),machineId).size()==1,
                    "Recovered paid step replayed its resource debit");
            action(r.machine,MachineActionPayload.Action.RESEARCH_PAUSE,"",0);
        }catch(java.io.IOException problem){throw new IllegalStateException("Published payment recovery readback failed",problem);}
        h.succeed();
    }
}
