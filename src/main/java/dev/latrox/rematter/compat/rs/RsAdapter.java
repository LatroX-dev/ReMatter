package dev.latrox.rematter.compat.rs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.refinedmods.refinedstorage.api.autocrafting.task.*;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import dev.latrox.rematter.automation.*;
import dev.latrox.rematter.content.MachineBlockEntity;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Native container lifecycle and durable RS tasks are separate from the machine's transferred-input ledger. */
public final class RsAdapter implements AutomationAdapter {
    private final MachineBlockEntity host;
    private final AutomationPort port;
    private final RsBridgeNode node;
    private final NetworkNodeContainerProvider provider;
    private Dynamic<?> opaque;
    private boolean initialized;
    private boolean closed;
    public RsAdapter(MachineBlockEntity host,AutomationPort port) {
        this.host=host;this.port=port;node=new RsBridgeNode(port);
        node.setTaskValidator(this::validateTask);
        provider=RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
        provider.addContainer(RefinedStorageApi.INSTANCE.createNetworkNodeContainer(host,node).name("ReMatter Interface").build());
    }
    @Override public String id(){return "rs";}
    public NetworkNodeContainerProvider provider(){return opaque==null&&!closed?provider:null;}
    @Override public void load(ValueInput input) {
        var raw=input.read("state",Codec.PASSTHROUGH);
        if(raw.isEmpty())return;
        var state=input.read("state",RsTaskPersistence.CODEC);
        if(state.isEmpty()){opaque=raw.get();node.disable();return;}
        for(var task:state.get())node.addTask(new TaskImpl(task));
    }
    @Override public void save(ValueOutput output) {
        if(opaque!=null){output.store("state",Codec.PASSTHROUGH,opaque);return;}
        var tasks=node.getTasks().stream().map(task->{
            if(!(task instanceof TaskImpl impl))throw new IllegalStateException("Unserializable RS task");return impl.createSnapshot();
        }).toList();
        // ValueOutput reports codec errors without throwing; pre-encode before replacing saved state.
        var encoded=RsTaskPersistence.CODEC.encodeStart(host.getLevel().registryAccess()
                .createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),tasks).getOrThrow();
        output.store("state",Codec.PASSTHROUGH,new Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE,encoded));
    }
    private void validateTask(Task task) {
        if(!(task instanceof TaskImpl impl)||host.getLevel()==null)throw new IllegalArgumentException("Unsupported RS task");
        var next=new java.util.ArrayList<TaskSnapshot>();
        for(var existing:node.getTasks()) {
            if(!(existing instanceof TaskImpl nativeTask))throw new IllegalStateException("Unsupported stored RS task");
            next.add(nativeTask.createSnapshot());
        }
        next.add(impl.createSnapshot());
        RsTaskPersistence.CODEC.encodeStart(host.getLevel().registryAccess()
                .createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),next).getOrThrow();
    }
    @Override public void tick() {
        if(closed||opaque!=null||!(host.getLevel() instanceof ServerLevel level))return;
        if(!initialized){initialized=true;provider.initialize(level,port::changed);}
        // RS itself ticks the network node; do not run it a second time here.
        node.refreshCatalogue();
    }
    @Override public void close() {
        if(closed)return;closed=true;node.disable();
        if(initialized&&host.getLevel()!=null)provider.remove(host.getLevel());
    }
    @Override public boolean connected(){return !closed&&opaque==null&&node.isActive()&&node.getNetwork()!=null;}
    @Override public boolean hasTasks(){return opaque!=null||!node.getTasks().isEmpty();}
}
