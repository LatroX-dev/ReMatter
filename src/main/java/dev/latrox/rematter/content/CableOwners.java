package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import dev.latrox.rematter.ReMatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/** Persisted chunk-local ownership, without per-cable block entities or client UUID disclosure. */
final class CableOwners {
    private record PlacementOwner(UUID owner,SecurityMode mode,long tick) {}
    /** Only spans NeoForge's synchronous captured placement; never persisted or sent to clients. */
    private static final Map<ServerLevel,Map<Long,PlacementOwner>> PLACEMENTS=new WeakHashMap<>();
    static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,ReMatter.MOD_ID);
    private static final MapCodec<Map<String,UUID>> CODEC=Codec.unboundedMap(Codec.STRING,UUIDUtil.CODEC)
            .validate(map->map.size()<=65536?DataResult.success(map):DataResult.error(()->"Too many cable owners"))
            .fieldOf("owners");
    private static final MapCodec<Map<String,String>> SECURITY_CODEC=Codec.unboundedMap(Codec.STRING,Codec.STRING)
            .validate(map->map.size()<=65536?DataResult.success(map):DataResult.error(()->"Too many cable security modes"))
            .fieldOf("security");
    private static final java.util.function.Supplier<AttachmentType<Map<String,UUID>>> OWNERS=TYPES.register("cable_owners",
            ()->AttachmentType.<Map<String,UUID>>builder(Map::of).serialize(CODEC,map->!map.isEmpty()).build());
    private static final java.util.function.Supplier<AttachmentType<Map<String,String>>> SECURITY=TYPES.register("cable_security",
            ()->AttachmentType.<Map<String,String>>builder(Map::of).serialize(SECURITY_CODEC,map->!map.isEmpty()).build());
    private CableOwners() {}
    static void placedBy(ServerLevel level,BlockPos pos,UUID owner) {
        placedBy(level,pos,owner,SecurityMode.PUBLIC);
    }
    static void placedBy(ServerLevel level,BlockPos pos,UUID owner,SecurityMode mode) {
        if(!level.captureBlockSnapshots) {
            set(level,pos,owner,mode);
            return;
        }
        var pending=PLACEMENTS.computeIfAbsent(level,ignored->new HashMap<>());
        long tick=level.getGameTime();
        pending.values().removeIf(value->value.tick()!=tick);
        pending.put(pos.asLong(),new PlacementOwner(owner,mode,tick));
    }
    static void finishPlacement(ServerLevel level,BlockPos pos) {
        var pending=takePlacement(level,pos);
        if(pending!=null&&pending.tick()==level.getGameTime())set(level,pos,pending.owner(),pending.mode());
        else set(level,pos,null,SecurityMode.PUBLIC);
    }
    static void discardPlacement(ServerLevel level,BlockPos pos) {takePlacement(level,pos);}
    private static @Nullable PlacementOwner takePlacement(ServerLevel level,BlockPos pos) {
        var pending=PLACEMENTS.get(level);
        if(pending==null)return null;
        var owner=pending.remove(pos.asLong());
        if(pending.isEmpty())PLACEMENTS.remove(level);
        return owner;
    }
    static @Nullable UUID owner(ServerLevel level,BlockPos pos) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        var values=chunk==null?null:chunk.getExistingDataOrNull(OWNERS);
        return values==null?null:values.get(Long.toString(pos.asLong()));
    }
    static SecurityMode mode(ServerLevel level,BlockPos pos) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        var values=chunk==null?null:chunk.getExistingDataOrNull(SECURITY);
        if(values==null)return SecurityMode.PUBLIC;
        String raw=values.get(Long.toString(pos.asLong()));
        if(raw==null)return SecurityMode.PUBLIC;
        try { return SecurityMode.load(raw); } catch(IllegalArgumentException invalid) { return SecurityMode.PRIVATE; }
    }
    static void set(ServerLevel level,BlockPos pos,@Nullable UUID owner) {
        set(level,pos,owner,SecurityMode.PUBLIC);
    }
    static void set(ServerLevel level,BlockPos pos,@Nullable UUID owner,SecurityMode mode) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null) return;
        String key=Long.toString(pos.asLong());
        var values=new HashMap<>(chunk.getExistingData(OWNERS).orElse(Map.of()));
        UUID previous=owner==null?values.remove(key):values.put(key,owner);
        boolean ownerChanged=!java.util.Objects.equals(previous,owner);
        var modes=new HashMap<>(chunk.getExistingData(SECURITY).orElse(Map.of()));
        String targetMode=owner==null||mode==SecurityMode.PUBLIC?null:mode.getSerializedName();
        String previousMode=targetMode==null?modes.remove(key):modes.put(key,targetMode);
        boolean modeChanged=!java.util.Objects.equals(previousMode,targetMode);
        if(!ownerChanged&&!modeChanged)return;
        if(ownerChanged){
            if(values.isEmpty())chunk.removeData(OWNERS);else chunk.setData(OWNERS,Map.copyOf(values));
        }
        if(modeChanged){
            if(modes.isEmpty())chunk.removeData(SECURITY);else chunk.setData(SECURITY,Map.copyOf(modes));
        }
        chunk.markUnsaved();ChunkRevisions.touch(chunk);level.invalidateCapabilities(pos);
        MatterCableBlock.refreshLoadedAround(level,pos);
    }
    static boolean setSecurityMode(ServerLevel level,BlockPos pos,Player actor,SecurityMode mode) {
        UUID owner=owner(level,pos);
        if(owner==null||!MachineAccess.canManage(owner,actor))return false;
        set(level,pos,owner,mode);
        return true;
    }
    static boolean canRemove(ServerLevel level,BlockPos pos,Player player) {
        UUID owner=owner(level,pos);
        return MachineAccess.allows(level,owner,mode(level,pos),player);
    }
}
