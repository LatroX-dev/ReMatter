package dev.latrox.rematter.celltransfer;

import com.mojang.serialization.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Unknown station formats are kept as raw data; Cell components remain their sole resource owners. */
public final class CellTransferData {
    static final int SCHEMA = 2;
    private static final int EMPTY_SLOTS = 2;
    static final Codec<Tag> TAG = Codec.PASSTHROUGH.xmap(d -> d.convert(NbtOps.INSTANCE).getValue(), tag -> new Dynamic<>(NbtOps.INSTANCE, tag));
    // LevelChunk adds keepPacked when persisting a block entity; it is not device payload.
    private static final Set<String> METADATA = Set.of("id", "x", "y", "z", "components", "keepPacked");
    private static final Set<String> KEYS = Set.of("schema", "items", "owner", "security", "all", "primal_selected", "selection");
    static final MapCodec<CompoundTag> RAW = new MapCodec<>() {
        public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.empty(); }
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            var raw = new CompoundTag();
            input.entries().forEach(e -> ops.getStringValue(e.getFirst()).result()
                    .filter(k -> !METADATA.contains(k))
                    .ifPresent(k -> raw.put(k, ops.convertTo(NbtOps.INSTANCE, e.getSecond()))));
            return DataResult.success(raw);
        }
        public <T> RecordBuilder<T> encode(CompoundTag raw, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : raw.keySet()) prefix.add(key, NbtOps.INSTANCE.convertTo(ops, raw.get(key)));
            return prefix;
        }
    };
    record Loaded(List<ItemStack> items, CellTransferSelection selection, @Nullable UUID owner, SecurityMode securityMode) {}
    static boolean isCell(ItemStack stack) { return MachineCellAccess.isMountableBayCell(stack); }
    static Optional<Loaded> read(CompoundTag raw, HolderLookup.Provider registries) {
        try {
            if (!KEYS.containsAll(raw.keySet()) || !(raw.get("schema") instanceof IntTag)
                    || !(raw.get("all") instanceof ByteTag) || !(raw.get("primal_selected") instanceof ByteTag)) return Optional.empty();
            int schema = raw.getIntOr("schema", -1);
            if (schema != SCHEMA && schema != 1) return Optional.empty();
            var parsed = ItemStack.OPTIONAL_CODEC.listOf().parse(registries.createSerializationContext(NbtOps.INSTANCE), Objects.requireNonNull(raw.get("items"))).result();
            if (parsed.isEmpty() || parsed.get().stream().anyMatch(s -> !s.isEmpty() && (s.getCount() != 1 || !isCell(s)))) return Optional.empty();
            List<ItemStack> twoSlots;
            if (schema == SCHEMA) {
                if (parsed.get().size() != CellTransferStationBlockEntity.SLOTS) return Optional.empty();
                twoSlots = parsed.get();
            } else {
                if (parsed.get().size() != 6) return Optional.empty();
                if (!parsed.get().get(1).isEmpty() || !parsed.get().get(2).isEmpty()
                        || !parsed.get().get(4).isEmpty() || !parsed.get().get(5).isEmpty()) return Optional.empty();
                twoSlots = List.of(parsed.get().get(0), parsed.get().get(3));
            }
            UUID owner = null;
            if (raw.contains("owner")) { owner = owner(raw.getString("owner").orElseThrow()); if (owner == null) return Optional.empty(); }
            SecurityMode securityMode = SecurityMode.PUBLIC;
            if (raw.contains("security")) {
                if (!(raw.get("security") instanceof StringTag)) return Optional.empty();
                String sec = raw.getStringOr("security", "");
                if (!SecurityMode.validName(sec)) return Optional.empty();
                securityMode = SecurityMode.load(sec);
            }
            var rows = raw.getList("selection").orElseThrow();
            if (rows.size() > CellPartitionData.MAX_KEYS) return Optional.empty();
            var keys = new TreeSet<SubstanceKey>();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.getCompound(i).orElseThrow();
                if (!row.keySet().equals(Set.of("substance", "phase"))) return Optional.empty();
                String id = row.getString("substance").orElseThrow();
                if (id.length() > CellPartitionData.MAX_ID_LENGTH) return Optional.empty();
                if (!keys.add(new SubstanceKey(SubstanceId.parse(id), Phase.valueOf(row.getString("phase").orElseThrow())))) return Optional.empty();
            }
            return Optional.of(new Loaded(twoSlots, new CellTransferSelection(raw.getBooleanOr("all", false), raw.getBooleanOr("primal_selected", false), keys), owner, securityMode));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    static ListTag keys(CellTransferSelection selection) {
        var rows = new ListTag();
        for (var key : new TreeSet<>(selection.keys())) {
            var row = new CompoundTag();
            row.putString("substance", key.id().toString());
            row.putString("phase", key.phase().name());
            rows.add(row);
        }
        return rows;
    }
    static @Nullable UUID owner(String value) {
        try { var id = UUID.fromString(value); return id.toString().equals(value) ? id : null; }
        catch (IllegalArgumentException invalid) { return null; }
    }

    /** Strict structural empty check: schema 2, valid keys, and both cell slots encoded as empty compounds. */
    public static boolean isEmptyTag(CompoundTag raw) {
        if (!KEYS.containsAll(raw.keySet()) || !(raw.get("schema") instanceof IntTag)
                || raw.getIntOr("schema", -1) != SCHEMA
                || !(raw.get("all") instanceof ByteTag) || !(raw.get("primal_selected") instanceof ByteTag)) return false;
        if (raw.contains("owner") && (!(raw.get("owner") instanceof StringTag) || owner(raw.getStringOr("owner", "")) == null)) return false;
        if (raw.contains("security") && (!(raw.get("security") instanceof StringTag) || !SecurityMode.validName(raw.getStringOr("security", "")))) return false;
        if (!(raw.get("items") instanceof ListTag list) || list.size() != EMPTY_SLOTS) return false;
        for (var element : list) {
            if (!(element instanceof CompoundTag compound) || !compound.isEmpty()) return false;
        }
        return raw.get("selection") instanceof ListTag;
    }
}
