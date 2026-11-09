package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import dev.latrox.rematter.ReMatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,ReMatter.MOD_ID);
    private static final MapCodec<Map<String,UUID>> CODEC=Codec.unboundedMap(Codec.STRING,UUIDUtil.CODEC)
            .validate(map->map.size()<=65536?DataResult.success(map):DataResult.error(()->"Too many cable owners"))
            .fieldOf("owners");
    private static final java.util.function.Supplier<AttachmentType<Map<String,UUID>>> OWNERS=TYPES.register("cable_owners",
            ()->AttachmentType.<Map<String,UUID>>builder(Map::of).serialize(CODEC,map->!map.isEmpty()).build());
    private CableOwners() {}
    static @Nullable UUID owner(ServerLevel level,BlockPos pos) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        var values=chunk==null?null:chunk.getExistingDataOrNull(OWNERS);
        return values==null?null:values.get(Long.toString(pos.asLong()));
    }
    static void set(ServerLevel level,BlockPos pos,@Nullable UUID owner) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null) return;
        var values=new HashMap<>(chunk.getExistingData(OWNERS).orElse(Map.of()));
        String key=Long.toString(pos.asLong());
        UUID previous=owner==null?values.remove(key):values.put(key,owner);
        if(java.util.Objects.equals(previous,owner))return;
        if(values.isEmpty())chunk.removeData(OWNERS);else chunk.setData(OWNERS,Map.copyOf(values));
        chunk.markUnsaved();ChunkRevisions.touch(chunk);level.invalidateCapabilities(pos);
        MatterCableBlock.refreshLoadedAround(level,pos);
    }
    static boolean canRemove(ServerLevel level,BlockPos pos,Player player) {
        UUID owner=owner(level,pos);
        return MachineAccess.allows(owner,player.getUUID(),player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER),
                player instanceof net.neoforged.neoforge.common.util.FakePlayer);
    }
}
