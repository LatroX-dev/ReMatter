package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Changed stock rows of the open menu. A reset replaces the client's rows; otherwise only changed and removed rows
 * travel. {@code totalTypes} counts every stored type, so a list limited to the row maximum is reported, not hidden.
 */
public record MachineStockPayload(int containerId, boolean reset, boolean readable, int totalTypes,
                                  List<MachineSnapshot.Entry> changed, List<MachineSnapshot.StockKey> removed) implements CustomPacketPayload {
    public static final Type<MachineStockPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "machine_stock"));

    public MachineStockPayload {
        changed = List.copyOf(changed);
        removed = List.copyOf(removed);
        if (totalTypes < 0 || changed.size() > MachineSnapshot.MAX_ENTRIES || removed.size() > MachineSnapshot.MAX_ENTRIES)
            throw new IllegalArgumentException("Invalid stock change");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineStockPayload> CODEC = new StreamCodec<>() {
        @Override public MachineStockPayload decode(RegistryFriendlyByteBuf buffer) {
            int container = buffer.readVarInt();
            boolean reset = buffer.readBoolean(), readable = buffer.readBoolean();
            int total = buffer.readVarInt();
            int changedCount = MachineSnapshot.count(buffer, MachineSnapshot.MAX_ENTRIES);
            var changed = new ArrayList<MachineSnapshot.Entry>(changedCount);
            for (int i = 0; i < changedCount; i++) changed.add(new MachineSnapshot.Entry(buffer.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH),
                    buffer.readUtf(16), buffer.readVarLong(), buffer.readVarLong(), buffer.readBoolean()));
            int removedCount = MachineSnapshot.count(buffer, MachineSnapshot.MAX_ENTRIES);
            var removed = new ArrayList<MachineSnapshot.StockKey>(removedCount);
            for (int i = 0; i < removedCount; i++) removed.add(new MachineSnapshot.StockKey(
                    buffer.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH), buffer.readUtf(16)));
            return new MachineStockPayload(container, reset, readable, total, changed, removed);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineStockPayload value) {
            buffer.writeVarInt(value.containerId);
            buffer.writeBoolean(value.reset); buffer.writeBoolean(value.readable);
            buffer.writeVarInt(value.totalTypes);
            buffer.writeVarInt(value.changed.size());
            for (var entry : value.changed) {
                buffer.writeUtf(entry.id(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH); buffer.writeUtf(entry.phase(), 16);
                buffer.writeVarLong(entry.amount()); buffer.writeVarLong(entry.available()); buffer.writeBoolean(entry.known());
            }
            buffer.writeVarInt(value.removed.size());
            for (var key : value.removed) {
                buffer.writeUtf(key.id(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH); buffer.writeUtf(key.phase(), 16);
            }
        }
    };

    @Override public Type<MachineStockPayload> type() { return TYPE; }
}
