package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A bounded view of the open menu, sent only when values change. */
public record MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                              int selected, List<String> choices, List<Entry> entries, long used, long capacity,
                              boolean automation, boolean running, int fluidAmount, String fluidName) implements CustomPacketPayload {
    public static final Type<MachineSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "machine_snapshot"));
    public static final MachineSnapshot EMPTY = new MachineSnapshot(-1, 0, 0, 0, 0, "idle", 0, List.of(), List.of(), 0, 0, false, true, 0, "");
    public MachineSnapshot {
        choices = choices.stream().map(value -> clipped(value, 256)).toList();
        entries = List.copyOf(entries);
        if (choices.size() > 256 || entries.size() > 512) throw new IllegalArgumentException("Menu snapshot too large");
    }
    public record Entry(String name, String phase, long amount) {
        public Entry { name = clipped(name, 256); phase = clipped(phase, 16); }
    }
    private static String clipped(String value, int maximum) { return value.length() > maximum ? value.substring(0, maximum) : value; }
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSnapshot> CODEC = new StreamCodec<>() {
        @Override public MachineSnapshot decode(RegistryFriendlyByteBuf buffer) {
            int container = buffer.readVarInt();
            int kind = buffer.readVarInt();
            int energy = buffer.readVarInt();
            int progress = buffer.readVarInt();
            int duration = buffer.readVarInt();
            String status = buffer.readUtf(64);
            int selected = buffer.readVarInt();
            int choiceCount = count(buffer, 256);
            List<String> choices = new ArrayList<>(choiceCount);
            for (int i = 0; i < choiceCount; i++) choices.add(buffer.readUtf(256));
            int entryCount = count(buffer, 512);
            List<Entry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) entries.add(new Entry(buffer.readUtf(256), buffer.readUtf(16), buffer.readVarLong()));
            return new MachineSnapshot(container, kind, energy, progress, duration, status, selected, choices, entries,
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(64));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineSnapshot value) {
            buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.kind); buffer.writeVarInt(value.energy);
            buffer.writeVarInt(value.progress); buffer.writeVarInt(value.duration); buffer.writeUtf(value.status, 64);
            buffer.writeVarInt(value.selected); buffer.writeVarInt(value.choices.size());
            value.choices.forEach(choice -> buffer.writeUtf(choice, 256));
            buffer.writeVarInt(value.entries.size());
            for (Entry entry : value.entries) { buffer.writeUtf(entry.name, 256); buffer.writeUtf(entry.phase, 16); buffer.writeVarLong(entry.amount); }
            buffer.writeVarLong(value.used); buffer.writeVarLong(value.capacity); buffer.writeBoolean(value.automation);
            buffer.writeBoolean(value.running); buffer.writeVarInt(value.fluidAmount); buffer.writeUtf(value.fluidName, 64);
        }
        private int count(RegistryFriendlyByteBuf buffer, int maximum) {
            int value = buffer.readVarInt();
            if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid snapshot list size");
            return value;
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (snapshot, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == snapshot.containerId) menu.acceptSnapshot(snapshot);
        });
    }
}
