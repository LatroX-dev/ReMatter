package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Immutable raw contents retain unknown schemas unchanged instead of becoming an empty cell. */
public final class MatterCellData {
    public static final int SCHEMA = 2;
    public static final int MAX_TYPES = MatterLimits.CELL_TYPES;
    public static final Codec<MatterCellData> CODEC = CompoundTag.CODEC.xmap(MatterCellData::new, MatterCellData::copyTag);
    public static final StreamCodec<ByteBuf, MatterCellData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(MatterCellData::new, MatterCellData::copyTag);
    public static final java.util.function.Supplier<DataComponentType<MatterCellData>> TYPE = () -> Registration.TYPE.get();
    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final java.util.function.Supplier<DataComponentType<MatterCellData>> TYPE = COMPONENTS.registerComponentType("matter_cell",
                builder -> builder.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    }
    private final CompoundTag raw;

    public MatterCellData(CompoundTag raw) { this.raw = Objects.requireNonNull(raw).copy(); }
    public CompoundTag copyTag() { return raw.copy(); }
    public static void register(IEventBus bus) { Registration.COMPONENTS.register(bus); }

    public record State(UUID cellId,List<SubstanceStack> contents,long creditRevision) {
        public State {
            Objects.requireNonNull(cellId);contents=List.copyOf(contents);
            if(creditRevision<0||contents.size()>MAX_TYPES)throw new IllegalArgumentException("Invalid ordinary cell identity");
        }
        public State withCredit(SubstanceKey target,long amount){
            if(amount<=0||ReservedSubstances.reserved(target))throw new IllegalArgumentException("Invalid ordinary credit");
            var values=new TreeMap<SubstanceKey,Long>();for(var row:contents)values.put(row.key(),row.quantity());
            values.merge(target,amount,Math::addExact);
            return new State(cellId,values.entrySet().stream().map(e->new SubstanceStack(e.getKey(),e.getValue())).toList(),Math.incrementExact(creditRevision));
        }
    }
    private static volatile java.util.function.BiFunction<State,Long,Optional<State>> authority=(state,capacity)->Optional.of(state);
    public static void installAuthority(java.util.function.BiFunction<State,Long,Optional<State>> resolver){authority=Objects.requireNonNull(resolver);}
    public static MatterCellData from(MatterInventory inventory){return from(inventory,UUID.randomUUID(),0);}
    public static MatterCellData from(MatterInventory inventory,UUID cellId,long creditRevision){return from(new State(cellId,inventory.snapshot().contents(),creditRevision));}
    public static MatterCellData from(State state) {
        var raw=new CompoundTag();raw.putInt("schema",SCHEMA);raw.putString("cell",state.cellId().toString());raw.putLong("credits",state.creditRevision());
        var contents=new ListTag();
        for(var stack:state.contents()){
            var row=new CompoundTag();row.putString("substance",stack.key().id().toString());row.putString("phase",stack.key().phase().name());row.putLong("amount",stack.quantity());contents.add(row);
        }
        raw.put("contents",contents);return new MatterCellData(raw);
    }
    public Optional<State> resolve(long capacity){
        try{
            if(!(raw.get("schema") instanceof IntTag)||raw.getIntOr("schema",-1)!=SCHEMA
                    ||!raw.keySet().equals(Set.of("schema","cell","credits","contents"))||!(raw.get("credits") instanceof LongTag))return Optional.empty();
            UUID id=UUID.fromString(raw.getString("cell").orElseThrow());
            if(!id.toString().equals(raw.getString("cell").orElseThrow()))return Optional.empty();
            var rows=raw.getList("contents").orElseThrow();if(rows.size()>MAX_TYPES)return Optional.empty();
            var contents=new ArrayList<SubstanceStack>();
            for(int i=0;i<rows.size();i++){
                var row=rows.getCompound(i).orElseThrow();if(!row.keySet().equals(Set.of("substance","phase","amount")))return Optional.empty();
                var key=new SubstanceKey(SubstanceId.parse(row.getString("substance").orElseThrow()),Phase.valueOf(row.getString("phase").orElseThrow()));
                if(ReservedSubstances.reserved(key))return Optional.empty();contents.add(new SubstanceStack(key,exactAmount(row)));
            }
            var resolved=authority.apply(new State(id,contents,raw.getLongOr("credits",-1)),capacity);
            if(resolved.isPresent())MatterInventory.restore(new MatterInventory.Snapshot(capacity,resolved.get().contents()),MAX_TYPES);
            return resolved;
        }catch(RuntimeException invalid){return Optional.empty();}
    }
    /** Capacity always comes from the physical cell item; unknown data remains untouched and locked. */
    public Optional<MatterInventory> open(long capacity){return resolve(capacity).map(state->MatterInventory.restore(new MatterInventory.Snapshot(capacity,state.contents()),MAX_TYPES));}
    private static long exactAmount(CompoundTag row) {
        if (!(row.get("amount") instanceof LongTag)) throw new IllegalArgumentException("Expected exact long quantity");
        return row.getLong("amount").orElseThrow();
    }
    @Override public boolean equals(Object value) { return value instanceof MatterCellData other && raw.equals(other.raw); }
    @Override public int hashCode() { return raw.hashCode(); }
}
