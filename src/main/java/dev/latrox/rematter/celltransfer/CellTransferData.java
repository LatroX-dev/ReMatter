package dev.latrox.rematter.celltransfer;

import com.mojang.serialization.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.*;
import dev.latrox.rematter.primal.PrimalCellItem;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Unknown station formats are kept as raw data; Cell components remain their sole resource owners. */
final class CellTransferData {
    static final int SCHEMA=1;
    static final Codec<Tag> TAG=Codec.PASSTHROUGH.xmap(d->d.convert(NbtOps.INSTANCE).getValue(),tag->new Dynamic<>(NbtOps.INSTANCE,tag));
    private static final Set<String> METADATA=Set.of("id","x","y","z","components");
    private static final Set<String> KEYS=Set.of("schema","items","owner","all","primal_selected","selection");
    static final MapCodec<CompoundTag> RAW=new MapCodec<>(){
        public <T> Stream<T> keys(DynamicOps<T> ops){return Stream.empty();}
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops,MapLike<T> input){
            var raw=new CompoundTag();input.entries().forEach(e->ops.getStringValue(e.getFirst()).result().filter(k->!METADATA.contains(k)).ifPresent(k->raw.put(k,ops.convertTo(NbtOps.INSTANCE,e.getSecond()))));return DataResult.success(raw);
        }
        public <T> RecordBuilder<T> encode(CompoundTag raw,DynamicOps<T> ops,RecordBuilder<T> prefix){for(String key:raw.keySet())prefix.add(key,NbtOps.INSTANCE.convertTo(ops,raw.get(key)));return prefix;}
    };
    record Loaded(List<ItemStack> items,CellTransferSelection selection,@Nullable UUID owner){}
    static boolean isCell(ItemStack stack){return stack.getItem() instanceof MatterCellItem||stack.getItem() instanceof PrimalCellItem;}
    static Optional<Loaded> read(CompoundTag raw,HolderLookup.Provider registries){
        try {
            if(!KEYS.containsAll(raw.keySet())||!(raw.get("schema") instanceof IntTag)||raw.getIntOr("schema",-1)!=SCHEMA
                    ||!(raw.get("all") instanceof ByteTag)||!(raw.get("primal_selected") instanceof ByteTag))return Optional.empty();
            var items=ItemStack.OPTIONAL_CODEC.listOf().parse(registries.createSerializationContext(NbtOps.INSTANCE),Objects.requireNonNull(raw.get("items"))).result();
            if(items.isEmpty()||items.get().size()!=6||items.get().stream().anyMatch(s->!s.isEmpty()&&(s.getCount()!=1||!isCell(s))))return Optional.empty();
            UUID owner=null;
            if(raw.contains("owner")){owner=owner(raw.getString("owner").orElseThrow());if(owner==null)return Optional.empty();}
            var rows=raw.getList("selection").orElseThrow();if(rows.size()>CellPartitionData.MAX_KEYS)return Optional.empty();
            var keys=new TreeSet<SubstanceKey>();
            for(int i=0;i<rows.size();i++){
                var row=rows.getCompound(i).orElseThrow();if(!row.keySet().equals(Set.of("substance","phase")))return Optional.empty();
                String id=row.getString("substance").orElseThrow();if(id.length()>CellPartitionData.MAX_ID_LENGTH)return Optional.empty();
                if(!keys.add(new SubstanceKey(SubstanceId.parse(id),Phase.valueOf(row.getString("phase").orElseThrow()))))return Optional.empty();
            }
            return Optional.of(new Loaded(items.get(),new CellTransferSelection(raw.getBooleanOr("all",false),raw.getBooleanOr("primal_selected",false),keys),owner));
        }catch(RuntimeException invalid){return Optional.empty();}
    }
    static ListTag keys(CellTransferSelection selection){var rows=new ListTag();for(var key:new TreeSet<>(selection.keys())){var row=new CompoundTag();row.putString("substance",key.id().toString());row.putString("phase",key.phase().name());rows.add(row);}return rows;}
    static @Nullable UUID owner(String value){try{var id=UUID.fromString(value);return id.toString().equals(value)?id:null;}catch(IllegalArgumentException invalid){return null;}}
}
