package dev.latrox.rematter.compat.rs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.refinedmods.refinedstorage.api.autocrafting.task.*;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.*;
import dev.latrox.rematter.content.MachineBlockEntity;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Native container lifecycle and durable RS tasks are separate from the machine's transferred-input ledger. */
public final class RsAdapter implements AutomationAdapter {
    private final MachineBlockEntity host;
    private final AutomationPort port;
    private final RsBridgeNode node;
    private final NetworkNodeContainerProvider provider;
    private static final long REFUSAL_STATUS_TICKS=200;
    private final NativeTicker ticker=new NativeTicker();
    private Dynamic<?> opaque;
    private net.minecraft.nbt.Tag saved;
    private long refusedUntil;
    private boolean initializationRequested;
    private boolean initialized;
    private boolean closed;
    public RsAdapter(MachineBlockEntity host,AutomationPort port) {
        this.host=host;this.port=port;node=new RsBridgeNode(port);
        node.setTaskAdmission(this::admits);
        provider=RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
        var container=RefinedStorageApi.INSTANCE.createNetworkNodeContainer(host,node).name("ReMatter Interface").build();
        provider.addContainer(whileOpen(container,()->closed));
    }
    /** Native queued initialization checks this before it can attach an already revoked adapter. */
    static InWorldNetworkNodeContainer whileOpen(InWorldNetworkNodeContainer delegate,BooleanSupplier closed) {
        return new InWorldNetworkNodeContainer() {
            @Override public boolean isRemoved(){return closed.getAsBoolean()||delegate.isRemoved();}
            @Override public com.refinedmods.refinedstorage.api.network.node.NetworkNode getNode(){return delegate.getNode();}
            @Override public net.minecraft.core.GlobalPos getPosition(){return delegate.getPosition();}
            @Override public net.minecraft.core.BlockPos getLocalPosition(){return delegate.getLocalPosition();}
            @Override public net.minecraft.world.level.block.state.BlockState getBlockState(){return delegate.getBlockState();}
            @Override public String getName(){return delegate.getName();}
            @Override public int getPriority(){return delegate.getPriority();}
            @Override public Object createKey(){return delegate.createKey();}
            @Override public void addOutgoingConnections(com.refinedmods.refinedstorage.common.api.support.network.ConnectionSink sink){delegate.addOutgoingConnections(sink);}
            @Override public boolean canAcceptIncomingConnection(net.minecraft.core.Direction direction,net.minecraft.world.level.block.state.BlockState state){return delegate.canAcceptIncomingConnection(direction,state);}
        };
    }
    @Override public String id(){return "rs";}
    public NetworkNodeContainerProvider provider(){
        // RS denies adjacent placement when an exposed container has no native network yet.
        return opaque==null&&!closed&&initialized&&node.getNetwork()!=null?provider:null;
    }
    @Override public void load(ValueInput input) {
        var raw=input.read("state",Codec.PASSTHROUGH);
        if(raw.isEmpty())return;
        var state=input.read("state",RsTaskPersistence.CODEC);
        if(state.isEmpty()){opaque=raw.get();node.disable();return;}
        saved=raw.get().convert(NbtOps.INSTANCE).getValue();
        for(var task:state.get())node.restoreTask(new TaskImpl(task));
    }
    /** Never throws inside a save: an encoding failure keeps the last state that was written successfully. */
    @Override public void save(ValueOutput output) {
        if(opaque!=null){output.store("state",Codec.PASSTHROUGH,opaque);return;}
        var encoded=encode();
        if(encoded.isPresent())saved=encoded.get();
        else ReMatter.LOGGER.error("Refined Storage jobs of the ReMatter interface at {} could not be encoded; keeping their last saved state",host.getBlockPos());
        if(saved!=null)output.store("state",Codec.PASSTHROUGH,new Dynamic<>(NbtOps.INSTANCE,saved.copy()));
    }
    private java.util.Optional<net.minecraft.nbt.Tag> encode() {
        if(host.getLevel()==null)return java.util.Optional.empty();
        return RsTaskPersistence.encode(RsTaskPersistence.CODEC,host.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),node.getTasks());
    }
    /** Called after RS has added the task. Every admitted task list stays persistable. */
    private boolean admits(Task task) {
        boolean admitted=task instanceof TaskImpl&&node.getTasks().size()<=RsTaskPersistence.MAX_TASKS&&encode().isPresent();
        if(!admitted)refused();
        return admitted;
    }
    private void refused() {
        if(host.getLevel()==null)return;
        long now=host.getLevel().getGameTime();
        if(now>=refusedUntil)ReMatter.LOGGER.warn("ReMatter interface at {} refused a Refined Storage job",host.getBlockPos());
        refusedUntil=now+REFUSAL_STATUS_TICKS;
    }
    @Override public String status() {
        return host.getLevel()!=null&&host.getLevel().getGameTime()<refusedUntil?"automation_job_refused":"";
    }
    @Override public void tick() {
        if(closed||opaque!=null||!(host.getLevel() instanceof ServerLevel level))return;
        if(!initializationRequested){
            initializationRequested=true;
            // RS queues initialization; returning from initialize does not mean its node is ready.
            provider.initialize(level,()->initialized(level));
        }
        if(!initialized)return;
        // Native RS block entities own both activeness and node ticks; this host must do the same.
        ticker.tick(level.getGameTime(),node,liveHost(level)&&port.active(),RefinedStorageApi.INSTANCE.isEnergyRequired());
    }
    static final class NativeTicker {
        private long lastTick=Long.MIN_VALUE;
        void tick(long gameTime,AbstractNetworkNode node,boolean enabled,boolean energyRequired) {
            if(lastTick==gameTime)return;
            lastTick=gameTime;
            var network=node.getNetwork();
            boolean active=enabled&&network!=null&&(!energyRequired
                    ||network.getComponent(EnergyNetworkComponent.class).getStored()>=node.getEnergyUsage());
            if(node.isActive()!=active)node.setActive(active);
            // AbstractNetworkNode.doWork debits actual RS energy; PatternProviderNetworkNode advances native tasks.
            node.doWork();
        }
    }
    private void initialized(ServerLevel level) {
        if(closed||opaque!=null||!liveHost(level)) {
            // A queued native callback can outlive backend revocation while the block still exists.
            provider.remove(level);
            return;
        }
        initialized=node.getNetwork()!=null;
        if(!initialized)initializationRequested=false;
        port.changed();
        if(!closed&&liveHost(level))level.invalidateCapabilities(host.getBlockPos());
    }
    private boolean liveHost(ServerLevel level) {
        return host.getLevel()==level&&!host.isRemoved()&&level.hasChunkAt(host.getBlockPos())
                &&level.getBlockEntity(host.getBlockPos())==host;
    }
    @Override public void close() {
        if(closed)return;closed=true;initialized=false;node.disable();
        if(initializationRequested&&host.getLevel()!=null)provider.remove(host.getLevel());
    }
    @Override public boolean connected(){return !closed&&opaque==null&&node.isActive()&&node.getNetwork()!=null;}
    @Override public boolean hasTasks(){return opaque!=null||!node.getTasks().isEmpty();}
}
