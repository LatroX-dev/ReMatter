package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

/** Server advancement state remains authoritative; a durable mirror permits already-unlocked offline work. */
@EventBusSubscriber(modid=ReMatter.MOD_ID)
public final class MachineProgression extends SavedData {
    private static final Codec<MachineProgression> CODEC=Codec.unboundedMap(Codec.STRING,Codec.STRING.listOf())
            .xmap(MachineProgression::decode,MachineProgression::encode);
    private static final SavedDataType<MachineProgression> TYPE=new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"progression"),MachineProgression::new,CODEC);
    private final Map<UUID,Set<String>> earned=new HashMap<>();
    public MachineProgression() {}
    private static MachineProgression get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    public static boolean allows(ServerLevel level, UUID owner, String requirement) {
        if(requirement.isEmpty())return true;
        if(owner==null)return false;
        var holder=level.getServer().getAdvancements().get(Identifier.parse(requirement));
        if(holder==null)return false;
        var data=get(level);var player=level.getServer().getPlayerList().getPlayer(owner);
        if(player!=null) {
            boolean complete=player.getAdvancements().getOrStartProgress(holder).isDone();
            data.update(owner,requirement,complete);return complete;
        }
        return data.earned.getOrDefault(owner,Set.of()).contains(requirement);
    }
    @SubscribeEvent public static void onProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)
            get(player.level()).update(player.getUUID(),event.getAdvancement().id().toString(),event.getAdvancementProgress().isDone());
    }
    /** Capture pre-existing awards as soon as an owner joins, before any gated machine has run. */
    public static void capture(ServerPlayer player) {
        var completed=new HashSet<String>();
        for(var holder:player.level().getServer().getAdvancements().getAllAdvancements())
            if(player.getAdvancements().getOrStartProgress(holder).isDone())completed.add(holder.id().toString());
        var data=get(player.level());var previous=data.earned.getOrDefault(player.getUUID(),Set.of());
        if(!previous.equals(completed)) {
            if(completed.isEmpty())data.earned.remove(player.getUUID());else data.earned.put(player.getUUID(),completed);
            data.setDirty();
        }
    }
    @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player&&!(player instanceof net.neoforged.neoforge.common.util.FakePlayer))capture(player);
    }
    @SubscribeEvent public static void left(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player&&!(player instanceof net.neoforged.neoforge.common.util.FakePlayer))capture(player);
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { MachineBlockEntity.clearPlanning(); }
    private void update(UUID owner,String id,boolean complete) {
        var set=earned.computeIfAbsent(owner,ignored->new HashSet<>());
        boolean changed=complete?set.add(id):set.remove(id);
        if(set.isEmpty())earned.remove(owner);
        if(changed)setDirty();
    }
    private Map<String,List<String>> encode() {
        var result=new TreeMap<String,List<String>>();
        earned.forEach((owner,ids)->result.put(owner.toString(),ids.stream().sorted().toList()));return result;
    }
    private static MachineProgression decode(Map<String,List<String>> stored) {
        if(stored.size()>65_536)throw new IllegalArgumentException("Too many progression owners");
        var result=new MachineProgression();
        stored.forEach((owner,ids)-> {
            if(ids.size()>65_536)throw new IllegalArgumentException("Too many unlocked advancements");
            ids.forEach(Identifier::parse);result.earned.put(UUID.fromString(owner),new HashSet<>(ids));
        });return result;
    }
}
