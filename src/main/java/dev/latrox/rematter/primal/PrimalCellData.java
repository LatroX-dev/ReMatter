package dev.latrox.rematter.primal;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Raw, immutable data. Unknown versions and invalid contents are preserved and locked. */
public final class PrimalCellData {
    public static final int SCHEMA = 2;
    /** Only not-yet-settled recent operations travel with the item; history stays in the authority ledger. */
    public static final int MAX_RECEIPTS = 8;
    public static final Codec<PrimalCellData> CODEC = CompoundTag.CODEC.xmap(PrimalCellData::new, PrimalCellData::copyTag);
    public static final StreamCodec<ByteBuf, PrimalCellData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(PrimalCellData::new, PrimalCellData::copyTag);
    public static final java.util.function.Supplier<DataComponentType<PrimalCellData>> TYPE = () -> Registration.TYPE.get();
    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final java.util.function.Supplier<DataComponentType<PrimalCellData>> TYPE = COMPONENTS.registerComponentType("primal_cell",
                b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    }
    private final CompoundTag raw;
    public PrimalCellData(CompoundTag raw) { this.raw = Objects.requireNonNull(raw).copy(); }
    public CompoundTag copyTag() { return raw.copy(); }
    static void register(IEventBus bus) { Registration.COMPONENTS.register(bus); }
    public record State(UUID cellId, long amount, long authorityRevision, Map<UUID, Long> receipts) {
        public State {
            Objects.requireNonNull(cellId); receipts = Map.copyOf(receipts);
            if (amount < 0 || authorityRevision < 0 || receipts.size() > MAX_RECEIPTS || receipts.size() > authorityRevision || receipts.values().stream().anyMatch(v -> v == 0))
                throw new IllegalArgumentException("Invalid Primal cell state");
        }
        public State withAmount(long value) { return new State(cellId, value, authorityRevision, receipts); }
        public State withReceipt(UUID operation, long delta) {
            var next = new LinkedHashMap<>(receipts);
            if (delta == 0 || next.containsKey(operation) || next.size() == MAX_RECEIPTS) throw new IllegalArgumentException("Invalid or exhausted receipt");
            next.put(operation, delta);
            return new State(cellId, Math.addExact(amount, delta), Math.incrementExact(authorityRevision), next);
        }
    }
    public static PrimalCellData from(State value) {
        var raw = new CompoundTag(); raw.putInt("schema", SCHEMA);
        raw.putString("resource", ReservedSubstances.PRIMAL_MATTER.toString());
        raw.putString("cell_id", value.cellId().toString()); raw.putLong("amount", value.amount());
        raw.putLong("authority_revision", value.authorityRevision());
        var receipts = new ListTag();
        value.receipts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var row = new CompoundTag(); row.putString("operation", entry.getKey().toString()); row.putLong("delta", entry.getValue()); receipts.add(row);
        });
        raw.put("receipts", receipts); return new PrimalCellData(raw);
    }
    public Optional<State> open(long capacity) {
        try {
            if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA
                    || !raw.keySet().equals(Set.of("schema", "resource", "cell_id", "amount", "authority_revision", "receipts"))
                    || !raw.getString("resource").orElseThrow().equals(ReservedSubstances.PRIMAL_MATTER.toString())
                    || !(raw.get("amount") instanceof LongTag) || !(raw.get("authority_revision") instanceof LongTag)) return Optional.empty();
            long amount = raw.getLong("amount").orElseThrow();
            if (capacity <= 0 || amount < 0 || amount > capacity) return Optional.empty();
            UUID id = UUID.fromString(raw.getString("cell_id").orElseThrow());
            if (!id.toString().equals(raw.getString("cell_id").orElseThrow())) return Optional.empty();
            var list = raw.getList("receipts").orElseThrow();
            if (list.size() > MAX_RECEIPTS) return Optional.empty();
            Map<UUID, Long> receipts = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                var row = list.getCompound(i).orElseThrow();
                if (!row.keySet().equals(Set.of("operation", "delta")) || !(row.get("delta") instanceof LongTag)) return Optional.empty();
                UUID operation = UUID.fromString(row.getString("operation").orElseThrow());
                if (!operation.toString().equals(row.getString("operation").orElseThrow())) return Optional.empty();
                long delta = row.getLong("delta").orElseThrow();
                if (delta == 0 || receipts.putIfAbsent(operation, delta) != null) return Optional.empty();
            }
            return Optional.of(new State(id, amount, raw.getLong("authority_revision").orElseThrow(), receipts));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    @Override public boolean equals(Object value) { return value instanceof PrimalCellData other && raw.equals(other.raw); }
    @Override public int hashCode() { return raw.hashCode(); }
}
