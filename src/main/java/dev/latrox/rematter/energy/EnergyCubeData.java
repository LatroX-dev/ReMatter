package dev.latrox.rematter.energy;

import com.mojang.serialization.*;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.nbt.*;
import org.jspecify.annotations.Nullable;

/** Structural validation precedes reading any stored energy or items. Rejected data is carried unchanged. */
final class EnergyCubeData {
    static final int SCHEMA=1;
    private static final Set<String> METADATA=Set.of("id","x","y","z","components");
    private static final Set<String> KEYS=Set.of("schema","energy","items","side_modes","owner");
    static final MapCodec<CompoundTag> RAW=new MapCodec<>() {
        @Override public <T> Stream<T> keys(DynamicOps<T> ops){return Stream.empty();}
        @Override public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops,MapLike<T> input){
            var raw=new CompoundTag();
            input.entries().forEach(entry->ops.getStringValue(entry.getFirst()).result().filter(key->!METADATA.contains(key))
                    .ifPresent(key->raw.put(key,ops.convertTo(NbtOps.INSTANCE,entry.getSecond()))));
            return DataResult.success(raw);
        }
        @Override public <T> RecordBuilder<T> encode(CompoundTag raw,DynamicOps<T> ops,RecordBuilder<T> prefix){
            for(String key:raw.keySet())prefix.add(key,NbtOps.INSTANCE.convertTo(ops,raw.get(key)));
            return prefix;
        }
    };
    private EnergyCubeData(){}
    static @Nullable String rejection(CompoundTag raw,EnergyCubeTier tier,boolean itemsDecoded,int itemCount){
        if(!(raw.get("schema") instanceof IntTag)||raw.getIntOr("schema",-1)!=SCHEMA)return "unknown schema";
        if(!KEYS.containsAll(raw.keySet()))return "unknown fields";
        if(!(raw.get("energy") instanceof IntTag))return "invalid energy type";
        int energy=raw.getIntOr("energy",-1);
        if(energy<0||energy>tier.capacity())return "energy outside capacity";
        if(!(raw.get("side_modes") instanceof IntTag)||!validModes(raw.getIntOr("side_modes",-1)))return "invalid side modes";
        if(!(raw.get("items") instanceof ListTag)||!itemsDecoded||itemCount!=tier.chargingSlots())return "unreadable items";
        if(raw.contains("owner")&&(!(raw.get("owner") instanceof StringTag)||owner(raw.getStringOr("owner",""))==null))return "invalid owner";
        return null;
    }
    static Optional<List<ItemStack>> items(CompoundTag raw,HolderLookup.Provider registries){
        return Optional.ofNullable(raw.get("items")).flatMap(tag->ItemStack.OPTIONAL_CODEC.listOf()
                .parse(registries.createSerializationContext(NbtOps.INSTANCE),tag).result())
                .filter(stacks->stacks.stream().noneMatch(stack->stack.getCount()>1));
    }
    static boolean validModes(int packed){
        if(packed<0||(packed>>>12)!=0)return false;
        for(int i=0;i<6;i++)if(((packed>>>(i*2))&3)>=EnergyCubeSideMode.values().length)return false;
        return true;
    }
    static @Nullable UUID owner(String value){
        try{var id=UUID.fromString(value);return id.toString().equals(value)?id:null;}
        catch(IllegalArgumentException invalid){return null;}
    }
}
