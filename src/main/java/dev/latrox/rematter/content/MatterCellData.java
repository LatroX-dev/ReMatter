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
    public static final int SCHEMA = 1;
    public static final int MAX_TYPES = 512;
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

    public static MatterCellData from(MatterInventory inventory) {
        var snapshot = inventory.snapshot();
        if (snapshot.contents().size() > MAX_TYPES) throw new IllegalArgumentException("Too many cell types");
        var raw = new CompoundTag(); raw.putInt("schema", SCHEMA);
        var contents = new ListTag();
        for (var stack : snapshot.contents()) {
            var row = new CompoundTag(); row.putString("substance", stack.key().id().toString());
            row.putString("phase", stack.key().phase().name()); row.putLong("amount", stack.quantity()); contents.add(row);
        }
        raw.put("contents", contents);
        var reservations = new ListTag();
        for (var reservation : snapshot.reservations()) {
            var row=new CompoundTag();row.putString("token",reservation.token().toString());
            row.put("inputs",amounts(reservation.inputs()));row.put("outputs",amounts(reservation.outputs()));reservations.add(row);
        }
        raw.put("reservations",reservations);
        return new MatterCellData(raw);
    }

    /** Capacity comes from the actual cell item, never from a mutable data field. */
    public Optional<MatterInventory> open(long capacity) {
        try {
            if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA
                    || !raw.keySet().equals(Set.of("schema", "contents", "reservations"))) return Optional.empty();
            var rows = raw.getList("contents").orElseThrow();
            if (rows.size() > MAX_TYPES) return Optional.empty();
            var contents = new ArrayList<SubstanceStack>();
            for (int i=0;i<rows.size();i++) {
                var row = rows.getCompound(i).orElseThrow();
                if (!row.keySet().equals(Set.of("substance", "phase", "amount"))) return Optional.empty();
                contents.add(new SubstanceStack(new SubstanceKey(SubstanceId.parse(row.getString("substance").orElseThrow()),
                        Phase.valueOf(row.getString("phase").orElseThrow())), exactAmount(row)));
            }
            var reservations=new ArrayList<MatterInventory.Reservation>();
            var savedReservations=raw.getList("reservations").orElseThrow();
            if(savedReservations.size()>MAX_TYPES)return Optional.empty();
            for(int i=0;i<savedReservations.size();i++) {
                var row=savedReservations.getCompound(i).orElseThrow();
                if(!row.keySet().equals(Set.of("token","inputs","outputs")))return Optional.empty();
                reservations.add(new MatterInventory.Reservation(UUID.fromString(row.getString("token").orElseThrow()),
                        readAmounts(row.getList("inputs").orElseThrow()),readAmounts(row.getList("outputs").orElseThrow())));
            }
            return Optional.of(MatterInventory.restore(new MatterInventory.Snapshot(MatterInventory.SCHEMA_VERSION,capacity,contents,reservations), MAX_TYPES));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    private static ListTag amounts(Map<SubstanceKey,Long> values) {
        var rows=new ListTag();
        values.forEach((key,amount) -> {var row=new CompoundTag();row.putString("substance",key.id().toString());
            row.putString("phase",key.phase().name());row.putLong("amount",amount);rows.add(row);});return rows;
    }
    private static Map<SubstanceKey,Long> readAmounts(ListTag rows) {
        if(rows.size()>MAX_TYPES)throw new IllegalArgumentException("Too many reserved types");
        var values=new TreeMap<SubstanceKey,Long>();
        for(int i=0;i<rows.size();i++) {
            var row=rows.getCompound(i).orElseThrow();
            if(!row.keySet().equals(Set.of("substance","phase","amount")))throw new IllegalArgumentException("Unknown reserved data");
            var key=new SubstanceKey(SubstanceId.parse(row.getString("substance").orElseThrow()),Phase.valueOf(row.getString("phase").orElseThrow()));
            if(values.putIfAbsent(key,exactAmount(row))!=null)throw new IllegalArgumentException("Duplicate reserved type");
        }return MatterAmounts.copy(values);
    }
    private static long exactAmount(CompoundTag row) {
        if (!(row.get("amount") instanceof LongTag)) throw new IllegalArgumentException("Expected exact long quantity");
        return row.getLong("amount").orElseThrow();
    }
    @Override public boolean equals(Object value) { return value instanceof MatterCellData other && raw.equals(other.raw); }
    @Override public int hashCode() { return raw.hashCode(); }
}
