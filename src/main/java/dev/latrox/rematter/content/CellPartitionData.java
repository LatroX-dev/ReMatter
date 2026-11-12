package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import io.netty.buffer.ByteBuf;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A Cell's insertion policy, independent of its exact contents. Unknown policies are preserved and locked. */
public final class CellPartitionData {
    public static final int SCHEMA=1;
    public static final int MAX_KEYS=MatterCellData.MAX_TYPES;
    public static final int MAX_ID_LENGTH=256;
    public static final Codec<CellPartitionData> CODEC=CompoundTag.CODEC.xmap(CellPartitionData::new,CellPartitionData::copyTag);
    public static final StreamCodec<ByteBuf,CellPartitionData> STREAM_CODEC=ByteBufCodecs.COMPOUND_TAG.map(CellPartitionData::new,CellPartitionData::copyTag);
    public static final java.util.function.Supplier<DataComponentType<CellPartitionData>> TYPE=()->Registration.TYPE.get();
    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS=DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,ReMatter.MOD_ID);
        private static final java.util.function.Supplier<DataComponentType<CellPartitionData>> TYPE=COMPONENTS.registerComponentType("cell_partition",b->b.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    }
    public static void register(IEventBus bus){Registration.COMPONENTS.register(bus);}
    private final CompoundTag raw;
    public CellPartitionData(CompoundTag raw){this.raw=Objects.requireNonNull(raw).copy();}
    public CompoundTag copyTag(){return raw.copy();}

    public record Spec(boolean restricted,Set<SubstanceKey> allowed) implements Predicate<SubstanceKey> {
        public Spec {
            allowed=Set.copyOf(allowed);
            if(allowed.size()>MAX_KEYS||!restricted&&!allowed.isEmpty()||allowed.stream().anyMatch(key->ReservedSubstances.reserved(key)||key.id().toString().length()>MAX_ID_LENGTH))
                throw new IllegalArgumentException("Invalid Cell partition");
        }
        public boolean accepts(SubstanceKey key){return !ReservedSubstances.reserved(key)&&(!restricted||allowed.contains(key));}
        @Override public boolean test(SubstanceKey key){return accepts(key);}
        public Spec withKey(SubstanceKey key,boolean selected){var next=new TreeSet<>(allowed);if(selected)next.add(key);else next.remove(key);return new Spec(true,next);}
    }
    public static final Spec ANY=new Spec(false,Set.of());
    public static CellPartitionData unrestricted(){return from(ANY);}
    public static CellPartitionData allowOnly(Collection<SubstanceKey> keys){return from(new Spec(true,new HashSet<>(keys)));}
    public static CellPartitionData from(Spec spec){
        var raw=new CompoundTag();raw.putInt("schema",SCHEMA);raw.putString("mode",spec.restricted()?"only":"any");
        var keys=new ListTag();
        for(var key:new TreeSet<>(spec.allowed())){var row=new CompoundTag();row.putString("substance",key.id().toString());row.putString("phase",key.phase().name());keys.add(row);}
        raw.put("keys",keys);return new CellPartitionData(raw);
    }
    public Optional<Spec> open(){
        try {
            if(!raw.keySet().equals(Set.of("schema","mode","keys"))||!(raw.get("schema") instanceof IntTag)||raw.getIntOr("schema",-1)!=SCHEMA)return Optional.empty();
            var mode=raw.getString("mode").orElseThrow();if(!mode.equals("any")&&!mode.equals("only"))return Optional.empty();
            var rows=raw.getList("keys").orElseThrow();if(rows.size()>MAX_KEYS)return Optional.empty();
            var keys=new TreeSet<SubstanceKey>();
            for(int i=0;i<rows.size();i++){
                var row=rows.getCompound(i).orElseThrow();if(!row.keySet().equals(Set.of("substance","phase")))return Optional.empty();
                String id=row.getString("substance").orElseThrow();if(id.length()>MAX_ID_LENGTH)return Optional.empty();
                var key=new SubstanceKey(SubstanceId.parse(id),Phase.valueOf(row.getString("phase").orElseThrow()));if(!keys.add(key))return Optional.empty();
            }
            return Optional.of(new Spec(mode.equals("only"),keys));
        }catch(RuntimeException invalid){return Optional.empty();}
    }
    public static Optional<Spec> read(ItemStack stack){
        if(!(stack.getItem() instanceof MatterCellItem)||stack.getCount()!=1)return Optional.empty();
        var data=stack.get(TYPE.get());return data==null?Optional.of(ANY):data.open();
    }
    @Override public boolean equals(Object value){return value instanceof CellPartitionData other&&raw.equals(other.raw);}
    @Override public int hashCode(){return raw.hashCode();}
}
