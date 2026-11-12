package dev.latrox.rematter.content;

import com.google.gson.*;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.generation.*;
import dev.latrox.rematter.network.MachineActionPayload;
import dev.latrox.rematter.primal.*;
import dev.latrox.rematter.research.*;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.*;

/** Explicit non-chemistry operations. Ordinary recipes and automation never acquire this payment reserve. */
final class MachineExtensions {
    private static final Gson JSON=new Gson();
    private final MachineBlockEntity machine;
    private UUID machineId=UUID.randomUUID(), researchId, conversionId;
    private String conversionTarget="";
    private long conversionAmount=1000;
    private int conversionProgress;
    private boolean converting, assay, researchRunning;
    private boolean identityCheckpoint;
    private final Set<UUID> checkpointedCells=new HashSet<>();
    private long energyReceiptRevision,partialEnergyDebt;
    private long checkpointRetryTick;
    private final PrimalCells.Journal primal;
    private final PrimalCells.Journal sourceCell;
    private final CrystalBurner burner=new CrystalBurner();
    private final ControllerEnergyBudget outputBudget=new ControllerEnergyBudget();
    private record EnergyState(int amount,long revision,long partial){}
    private final SnapshotJournal<EnergyState> energyJournal=new SnapshotJournal<>(){
        @Override protected EnergyState createSnapshot(){return new EnergyState((int)machine.energy().getAmountAsLong(),energyReceiptRevision,partialEnergyDebt);}
        @Override protected void revertToSnapshot(EnergyState state){machine.energy().set(state.amount());energyReceiptRevision=state.revision();partialEnergyDebt=state.partial();}
    };
    MachineExtensions(MachineBlockEntity machine){this.machine=machine;primal=new PrimalCells.Journal(()->machine.stack(MachineCellAccess.PRIMAL_SLOT));sourceCell=new PrimalCells.Journal(()->machine.stack(0));}
    boolean assayMode(){return assay&&machine.kind()==MachineKind.INDUSTRIAL_PREPARATOR;}
    private ServerLevel level(){return (ServerLevel)machine.getLevel();}
    private UUID owner(){return machine.networkOwner();}
    private boolean owned(){return machine.networkLive()&&ResearchService.realOwner(level().getServer(),owner());}
    private ResearchSessions engine(){return ResearchService.researchSession(level().getServer());}
    private ResearchContext context(){return ResearchService.context(level().getServer(),owner());}
    private boolean lens(){return machine.stack(MachineCellAccess.DISCOVERY_SLOT).is(PrimalContent.DISCOVERY_LENS.get());}
    private boolean network(){return machine.matterNetwork(false).ready();}
    private int deriveTicks(){return Math.max(1,(ResearchSessions.DERIVE_TICKS+machine.speed()-1)/Math.max(1,machine.speed()));}
    private long deriveEnergy(){return machine.cost(ResearchSessions.DERIVE_ENERGY);}
    private long discoveryEnergy(){return machine.cost(DiscoveryTier.STEP_ENERGY);}
    private boolean pay(UUID operation,long units,long energy,TransactionContext tx){
        if(!owned()||energy>machine.energy().getAmountAsLong())return false;
        var debit=primal.prepareDebit(operation,units);if(debit.isEmpty())return false;
        ResearchService.recordCellPayment(level().getServer(),owner(),debit.get(),-units,tx);
        ResearchService.recordEnergyPayment(level().getServer(),owner(),machineId,operation,energy,tx);
        energyJournal.updateSnapshots(tx);
        if(!debit.get().apply(tx))return false;
        machine.energy().set(Math.toIntExact(machine.energy().getAmountAsLong()-energy));energyReceiptRevision=ResearchService.energyChanges(level().getServer(),machineId).size();partialEnergyDebt=0;machine.setChanged();return true;
    }
    /** Reconcile authority-new/BE-old energy before that buffer can run work or emit power. */
    private boolean energyDebt(){
        var debts=ResearchService.energyChanges(level().getServer(),machineId);
        if(energyReceiptRevision>debts.size())return true;
        while(energyReceiptRevision<debts.size()){
            long remaining=debts.get(Math.toIntExact(energyReceiptRevision)).energy()-partialEnergyDebt;
            if(remaining<0)return true;
            long paid=Math.min(remaining,machine.energy().getAmountAsLong());
            if(paid>0){machine.energy().set((int)(machine.energy().getAmountAsLong()-paid));partialEnergyDebt+=paid;machine.setChanged();}
            if(paid<remaining)return true;
            energyReceiptRevision++;partialEnergyDebt=0;
        }
        return false;
    }
    private boolean checkpoint(boolean ordinaryOutput){
        if(level().getGameTime()<checkpointRetryTick)return false;
        if(Transaction.getCurrentOpenedTransaction()!=null)throw new IllegalStateException("Identity checkpoint inside transfer");
        var containers=new LinkedHashSet<MachineBlockEntity>();containers.add(machine);
        if(ordinaryOutput)containers.addAll(machine.matterNetwork(true).checkpointStores());
        boolean changed=false;
        for(var container:containers){
            var extension=container.extensions();var ids=new HashSet<>(container.establishCellIdentities());
            if(container==machine){
                var stack=primal.stack();var state=PrimalCells.state(stack);
                if(state.isPresent()){
                    var value=state.get();
                    if(value.cellId().equals(new UUID(0,0))){value=new PrimalCellData.State(UUID.randomUUID(),value.amount(),value.authorityRevision(),value.receipts());stack.set(PrimalCellData.TYPE.get(),PrimalCellData.from(value));}
                    ids.add(value.cellId());
                }
            }
            if(!extension.identityCheckpoint||!extension.checkpointedCells.containsAll(ids)){extension.identityCheckpoint=true;extension.checkpointedCells.clear();extension.checkpointedCells.addAll(ids);container.setChanged();changed=true;}
        }
        if(!changed)return true;
        level().getServer().saveEverything(true,true,true);
        try{
            for(var container:containers){
                var saved=IdentityCheckpointReader.blockEntity(level(),container.getBlockPos()).orElseThrow(()->new java.io.IOException("Missing saved payment container"));
                var extension=container.extensions();
                if(!saved.getStringOr("extension_id","").equals(extension.machineId.toString())||!saved.getBooleanOr("identity_checkpoint",false))throw new java.io.IOException("Controller identity was not saved");
                var items=ItemStack.OPTIONAL_CODEC.listOf().parse(level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),saved.get("items")).getOrThrow();
                for(int slot=0;slot<MachineCellAccess.INVENTORY_SIZE;slot++){
                    var current=container.stack(slot);
                    if(current.getItem() instanceof MatterCellItem||current.getItem() instanceof PrimalCellItem)
                        if(items.size()<=slot||!ItemStack.matches(current,items.get(slot)))throw new java.io.IOException("Cell identity was not saved");
                }
            }
            return true;
        }catch(java.io.IOException|RuntimeException failure){
            for(var container:containers){container.extensions().identityCheckpoint=false;container.extensions().checkpointedCells.clear();container.setChanged();}
            checkpointRetryTick=level().getGameTime()+100;
            ReMatter.LOGGER.error("Research/conversion paused: payment identities failed physical save readback",failure);return false;
        }
    }
    boolean tick(){
        if(!machine.networkLive())return false;
        if((machine.kind()==MachineKind.ANALYZER||machine.kind()==MachineKind.PRIMAL_CONVERTER)&&!ResearchService.financialReady(level().getServer())){machine.work().status="unsupported_data";return true;}
        if((machine.kind()==MachineKind.ANALYZER||machine.kind()==MachineKind.PRIMAL_CONVERTER||energyReceiptRevision>0||partialEnergyDebt>0)&&energyDebt()){
            machine.work().status="research_energy_debt";return true;
        }
        if(machine.kind().generator()){tickGenerator();return true;}
        if(machine.kind()==MachineKind.PRIMAL_CONVERTER){tickConverter();return true;}
        if(machine.kind()!=MachineKind.ANALYZER||!researchRunning||researchId==null)return false;
        if(!owned()){machine.work().status="research_owner";return true;}
        var view=engine().session(context(),researchId).orElse(null);
        if(view==null){machine.work().status="research_identity_changed";return true;}
        long energy=view.mode()==ResearchSessions.Mode.DISCOVERY?discoveryEnergy():deriveEnergy();
        int ticks=view.mode()==ResearchSessions.Mode.DISCOVERY?DiscoveryTier.STEP_TICKS:deriveTicks();
        if(!checkpoint(false)){machine.work().status="identity_checkpoint";return true;}
        var result=engine().advance(context(),researchId,level().getGameTime(),lens(),network(),energy,ticks,this::pay);
        if(result==ResearchSessions.Result.LEARNED&&view.mode()==ResearchSessions.Mode.DERIVE)machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.ON_PAPER);
        var updated=engine().session(context(),researchId).orElse(view);
        machine.work().progress=updated.completedSteps()*updated.stepTicks()+updated.elapsedTicks();
        machine.work().duration=updated.totalSteps()*updated.stepTicks();
        machine.work().processedThisTick=result==ResearchSessions.Result.PAID||result==ResearchSessions.Result.WORKING||result==ResearchSessions.Result.LEARNED;
        machine.work().status=switch(result){case LEARNED->"research_complete";case REFUND_DUE->"research_refund";case PAUSED,UNAVAILABLE->"research_paused";case WAITING->"research_payment";default->"researching";};
        if(result==ResearchSessions.Result.LEARNED||result==ResearchSessions.Result.REFUND_DUE)researchRunning=false;
        machine.setChanged();return true;
    }
    private PrimalPricing.Quote quote(){
        var known=ResearchService.knownSubstances(level(),owner());
        return PrimalPricing.quote(ReMatter.chemistry(),SubstanceKey.of(conversionTarget,Phase.SOLID),conversionAmount,id->known.stream().anyMatch(k->k.id().equals(id)));
    }
    private void tickConverter(){
        var work=machine.work();
        if(!owned()){work.status="research_owner";return;}
        var claims=ResearchService.conversionClaims(level().getServer(),owner(),machineId);
        for(var claim:claims)if(claim.remaining()>0){
            var network=machine.matterNetwork(true);
            if(!network.ready()){work.status=network.status();return;}
            if(!checkpoint(true)){work.status="identity_checkpoint";return;}
            long paid=ResearchService.claimConversion(level().getServer(),owner(),claim.id(),claim.remaining(),
                    (op,target,amount,tx)->network.credit(machine,op,target,amount,tx));
            if(paid==0){work.status="output_full";return;}
            network.markChanged();work.status="ready";machine.setChanged();return;
        }
        if(!converting){work.status="awaiting_request";return;}
        PrimalPricing.Quote quote;
        try{quote=quote();}catch(IllegalArgumentException|ArithmeticException invalid){converting=false;work.status="requires_research";machine.setChanged();return;}
        var network=machine.matterNetwork(true);
        if(!network.ready()){work.status=network.status();return;}
        if(!network.inventory().canApply(Map.of(),quote.outputs())){work.status="output_full";return;}
        if(machine.energy().getAmountAsLong()<quote.energy()||PrimalCells.view(primal.stack()).amount()<quote.primal()){work.status="research_payment";return;}
        conversionProgress=Math.min(quote.ticks(),conversionProgress+1);work.progress=conversionProgress;work.duration=quote.ticks();work.processedThisTick=true;work.status="working";
        boolean transferred=machine.advanceNetworkTransfer(conversionId.toString(),quote.output());
        if(conversionProgress<quote.ticks()){machine.setChanged();return;}
        if(!transferred){work.status="cable_limited";return;}
        if(!checkpoint(true)){work.status="identity_checkpoint";return;}
        var result=ResearchService.commitConversion(level().getServer(),owner(),machineId,conversionId,quote.target(),quote.output(),quote.energy(),this::pay);
        if(result.isPresent()){converting=false;conversionProgress=0;machine.completeNetworkTransfer();work.status="ready";machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.ONE_WAY);}
        else work.status="research_payment";
        machine.setChanged();
    }
    boolean action(MachineActionPayload request){
        if(!owned()||!ResearchService.financialReady(level().getServer()))return false;
        try{
            switch(request.action()){
                case LOAD_PRIMAL->{
                    if(machine.kind()!=MachineKind.ANALYZER&&machine.kind()!=MachineKind.PRIMAL_CONVERTER)return false;
                    try(var tx=Transaction.openRoot()){
                        long moved=machine.stack(0).getItem() instanceof PrimalCellItem?PrimalCells.transfer(sourceCell,primal,Long.MAX_VALUE,tx):PrimalCells.load(primal,machine.stack(0),64,tx);
                        if(moved==0)return false;tx.commit();machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.SOMETHING_OLDER);machine.setChanged();return true;
                    }
                }
                case CONVERT_SELECT->{
                    if(machine.kind()!=MachineKind.PRIMAL_CONVERTER||converting||request.amount()<250||request.amount()>8000)return false;
                    String previous=conversionTarget;long oldAmount=conversionAmount;conversionTarget=request.target();conversionAmount=request.amount();
                    try{quote();}catch(RuntimeException invalid){conversionTarget=previous;conversionAmount=oldAmount;return false;}
                    conversionProgress=0;conversionId=null;
                }
                case CONVERT_START->{
                    if(machine.kind()!=MachineKind.PRIMAL_CONVERTER||converting||!ResearchService.conversionClaims(level().getServer(),owner(),machineId).stream().noneMatch(c->c.remaining()>0))return false;
                    quote();conversionId=UUID.randomUUID();conversionProgress=0;converting=true;
                }
                case CONVERT_CANCEL->{if(machine.kind()!=MachineKind.PRIMAL_CONVERTER)return false;converting=false;conversionProgress=0;machine.completeNetworkTransfer();}
                case ASSAY->{if(machine.kind()!=MachineKind.INDUSTRIAL_PREPARATOR||machine.networkJobs())return false;assay=!assay;machine.resetProgress();}
                case DERIVE->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    var begun=engine().beginDerive(context(),request.target(),network(),deriveEnergy(),deriveTicks());if(begun.isEmpty())return false;
                    researchId=begun.get().id();researchRunning=true;machine.resetProgress();
                }
                case DISCOVER->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    var begun=engine().beginDiscovery(context(),DiscoveryTier.of(Math.toIntExact(request.amount())),lens(),discoveryEnergy());if(begun.isEmpty())return false;
                    researchId=begun.get().id();researchRunning=true;machine.resetProgress();
                }
                case RESEARCH_PAUSE->{if(machine.kind()!=MachineKind.ANALYZER||researchId==null)return false;engine().pause(context(),researchId);researchRunning=false;}
                case RESEARCH_RESUME->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    UUID id=request.target().isEmpty()?researchId:UUID.fromString(request.target());if(id==null||engine().resume(context(),id).isEmpty())return false;researchId=id;researchRunning=true;
                }
                case RESEARCH_REFUND->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    if(!checkpoint(false))return false;
                    long total=0;
                    for(var claim:engine().refundClaims(context()))total+=engine().claimRefund(context(),claim,PrimalCells.view(primal.stack()).capacity()-PrimalCells.view(primal.stack()).amount(),
                            (op,amount,tx)->{var prepared=primal.prepareRefund(op,amount);if(prepared.isEmpty())return 0;ResearchService.recordCellPayment(level().getServer(),owner(),prepared.get(),amount,tx);return prepared.get().apply(tx)?amount:0;});
                    if(total==0)return false;
                }
            }
            machine.setChanged();return true;
        }catch(IllegalArgumentException|ArithmeticException invalid){return false;}
    }
    JsonObject view(){
        var out=new JsonObject();out.addProperty("matterRate",network()?machine.matterTransferRate():128);out.addProperty("assay",assayMode());out.addProperty("recoveryMask",machine.recoveryMask());
        var reserve=PrimalCells.view(primal.stack());out.addProperty("primal",reserve.amount());out.addProperty("primalCapacity",reserve.capacity());out.addProperty("primalReadable",reserve.readable());
        if(machine.kind()==MachineKind.DRIVE){out.addProperty("primal",machine.drivePrimalAmount());out.addProperty("primalCapacity",machine.drivePrimalCapacity());}
        out.addProperty("fuelRemaining",burner.remaining());out.addProperty("generationRate",generationRate());out.addProperty("fuelEnabled",!CrystalFuelData.fuels().isEmpty());out.add("fuelIds",JSON.toJsonTree(CrystalFuelData.fuels().keySet()));
        out.addProperty("conversionTarget",conversionTarget);out.addProperty("conversionAmount",conversionAmount);out.addProperty("converting",converting);out.addProperty("researchRunning",researchRunning);
        out.addProperty("researchEnabled",ResearchConfig.enabled());
        if(!owned())return out;
        if(machine.kind().synthesis()){
            var knownTargets=new JsonArray();
            for(var choice:MachineChoices.forKind(machine.kind(),ReMatter.chemistry())){String target=choice.id().startsWith(MachineChoices.FLUID_PREFIX)?choice.id().substring(MachineChoices.FLUID_PREFIX.length()):choice.id();if(machine.researchAllows(target))knownTargets.add(target);}
            out.add("knownTargets",knownTargets);
        }
        var known=ResearchService.knownSubstances(level(),owner());var targets=new JsonArray();
        if(machine.kind()==MachineKind.PRIMAL_CONVERTER)out.add("conversionClaims",JSON.toJsonTree(ResearchService.conversionClaims(level().getServer(),owner(),machineId)));
        if(machine.kind()==MachineKind.PRIMAL_CONVERTER)for(var key:PrimalPricing.targets(ReMatter.chemistry(),id->known.stream().anyMatch(k->k.id().equals(id)))){
            var quote=PrimalPricing.quote(ReMatter.chemistry(),key,conversionAmount,id->true);var row=new JsonObject();row.addProperty("id",key.id().toString());row.addProperty("primal",quote.primal());row.addProperty("energy",quote.energy());row.addProperty("ticks",quote.ticks());targets.add(row);
        }
        out.add("targets",targets);
        if(machine.kind()==MachineKind.ANALYZER){
            var materials=new JsonArray();
            for(var material:ResearchService.materials(level().getServer(),owner())){
                var row=JSON.toJsonTree(material).getAsJsonObject();var substances=new JsonArray();
                for(var key:material.substances()){var substance=new JsonObject();substance.addProperty("id",key.id().toString());substance.addProperty("phase",key.phase().name().toLowerCase(Locale.ROOT));substance.addProperty("known",known.contains(key));substances.add(substance);}
                row.add("substances",substances);materials.add(row);
            }
            out.add("materials",materials);out.addProperty("knownCount",ResearchService.knownFamilies(level().getServer(),owner()).size());
            var discovery=new JsonArray();for(var tier:DiscoveryTier.values())discovery.add(engine().available(context(),tier));out.add("discovery",discovery);
            out.addProperty("deriveEnergy",deriveEnergy());out.addProperty("deriveTicks",deriveTicks());out.addProperty("discoveryStepEnergy",discoveryEnergy());out.addProperty("lens",lens());
            out.addProperty("discoveryAllowed",lens()&&ResearchService.ledger(level().getServer()).discoveryAllowed(context(),ResearchConfig.discoveryMinimum()));
            out.addProperty("deriveReady",network()&&reserve.readable());out.addProperty("researchEnabled",ResearchConfig.enabled());out.addProperty("refundDue",engine().refundDue(context()));
            if(researchId!=null)engine().session(context(),researchId).ifPresent(session->out.add("research",JSON.toJsonTree(session)));
            out.add("sessions",JSON.toJsonTree(engine().sessions(context())));
        }
        if(IndustrialStructure.isIndustrial(machine.kind())){var f=IndustrialStructure.inspect(machine);out.addProperty("field",f.performance().field());out.addProperty("precision",f.performance().precision());out.addProperty("processRoom",f.performance().processRoom());}
        return out;
    }
    int generationRate(){return machine.kind()==MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR?256:64;}
    private void tickGenerator(){
        pushEnergy();var work=machine.work();
        if(!machine.running()){work.status="paused";return;}
        var plan=burner.tick(MachineBlockEntity.ENERGY_CAPACITY-machine.energy().getAmountAsLong(),generationRate(),CrystalFuelData.fuel(machine.stack(0)).orElse(null),machine.stack(0).getCount());
        if(!burner.apply(plan)){work.status="unsupported_data";return;}
        if(plan.changesState()){machine.stack(0).shrink(plan.consumedItems());machine.energy().set((int)(machine.energy().getAmountAsLong()+plan.producedEnergy()));work.processedThisTick=plan.producedEnergy()>0;work.status="generating";machine.setChanged();}
        else work.status=!burner.readable()?"unsupported_data":machine.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY?"energy_full":"generator_fuel";
    }
    private final EnergyHandler output=new EnergyHandler(){
        public long getAmountAsLong(){return machine.energy().getAmountAsLong();}
        public long getCapacityAsLong(){return MachineBlockEntity.ENERGY_CAPACITY;}
        public int insert(int amount,TransactionContext tx){return 0;}
        public int extract(int amount,TransactionContext tx){
            if(amount<0)throw new IllegalArgumentException("Negative energy");if(!machine.networkLive()||!machine.kind().generator())return 0;
            int rate=machine.kind()==MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR?16384:2048;
            return outputBudget.insert(level().getGameTime(),Math.min(amount,(int)machine.energy().getAmountAsLong()),rate,tx,allowed->{energyJournal.updateSnapshots(tx);machine.energy().set((int)machine.energy().getAmountAsLong()-allowed);machine.setChanged();return allowed;});
        }
    };
    EnergyHandler output(){return output;}
    private void pushEnergy(){
        if(machine.energy().getAmountAsLong()==0)return;
        for(var side:Direction.values()){
            if(!machine.allowsPowerOutput(side))continue;var pos=machine.getBlockPos().relative(side);
            if(level().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)continue;
            var handler=level().getCapability(Capabilities.Energy.BLOCK,pos,side.getOpposite());if(handler==null||handler==output)continue;
            try(var tx=Transaction.openRoot()){
                int available=Math.min((int)machine.energy().getAmountAsLong(),machine.kind()==MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR?16384:2048);
                int moved=handler.insert(available,tx);if(moved>0&&output.extract(moved,tx)==moved)tx.commit();
            }
        }
    }
    boolean hasResources(){return burner.remaining()>0||!burner.readable();}
    void save(ValueOutput out){
        if(machine.kind()==MachineKind.ANALYZER||machine.kind()==MachineKind.PRIMAL_CONVERTER||energyReceiptRevision>0||partialEnergyDebt>0||identityCheckpoint||!checkpointedCells.isEmpty()){
            out.putString("extension_id",machineId.toString());out.putBoolean("identity_checkpoint",identityCheckpoint);out.store("checkpointed_cells",com.mojang.serialization.Codec.STRING.listOf(),checkpointedCells.stream().map(UUID::toString).sorted().toList());out.putLong("energy_receipt_revision",energyReceiptRevision);out.putLong("partial_energy_debt",partialEnergyDebt);
        }
        if(researchId!=null){out.putString("research_session",researchId.toString());out.putBoolean("research_running",researchRunning);}
        if(machine.kind()==MachineKind.PRIMAL_CONVERTER){out.putString("conversion_target",conversionTarget);out.putLong("conversion_amount",conversionAmount);out.putBoolean("converting",converting);out.putInt("conversion_progress",conversionProgress);if(conversionId!=null)out.putString("conversion_id",conversionId.toString());}
        if(assay)out.putBoolean("assay",true);
        if(machine.kind().generator())out.store("crystal_burner",CompoundTag.CODEC,burner.save());
    }
    private static UUID storedIdentity(String raw){
        UUID id=UUID.fromString(raw);
        if(!id.toString().equals(raw))throw new IllegalArgumentException("Noncanonical payment identity");
        return id;
    }
    void load(ValueInput in){
        String rawId=in.getStringOr("extension_id","");
        machineId=rawId.isEmpty()?UUID.randomUUID():storedIdentity(rawId);
        if(!rawId.isEmpty()&&!rawId.equals(machineId.toString()))throw new IllegalArgumentException("Invalid extension identity");
        identityCheckpoint=in.getBooleanOr("identity_checkpoint",false);checkpointedCells.clear();
        var cellIds=in.read("checkpointed_cells",com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of());
        if(cellIds.size()>MachineCellAccess.INVENTORY_SIZE)throw new IllegalArgumentException("Too many cell identities");
        for(var id:cellIds)if(!checkpointedCells.add(storedIdentity(id)))throw new IllegalArgumentException("Duplicate cell identity");
        energyReceiptRevision=in.getLongOr("energy_receipt_revision",0);partialEnergyDebt=in.getLongOr("partial_energy_debt",0);
        if(energyReceiptRevision<0||energyReceiptRevision>262144||partialEnergyDebt<0||partialEnergyDebt>1200000||rawId.isEmpty()&&(identityCheckpoint||energyReceiptRevision>0||partialEnergyDebt>0))throw new IllegalArgumentException("Invalid energy payment watermark");
        researchId=null;conversionId=null;
        if(in.getString("research_session").isPresent())researchId=storedIdentity(in.getString("research_session").orElseThrow());
        researchRunning=in.getBooleanOr("research_running",false);assay=in.getBooleanOr("assay",false);
        conversionTarget=in.getStringOr("conversion_target","");conversionAmount=in.getLongOr("conversion_amount",1000);converting=in.getBooleanOr("converting",false);conversionProgress=in.getIntOr("conversion_progress",0);
        if(in.getString("conversion_id").isPresent())conversionId=storedIdentity(in.getString("conversion_id").orElseThrow());
        if(converting&&conversionId==null||conversionAmount<250||conversionAmount>8000||conversionAmount%250!=0||conversionProgress<0||conversionProgress>12000||researchRunning&&researchId==null)throw new IllegalArgumentException("Invalid retained research or conversion job");
        if(machine.kind().generator())in.read("crystal_burner",CompoundTag.CODEC).ifPresent(burner::load);
    }
}
