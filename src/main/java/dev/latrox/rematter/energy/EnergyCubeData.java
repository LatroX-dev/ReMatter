package dev.latrox.rematter.energy;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Structural validation precedes reading any stored energy or items. Rejected data is carried unchanged. */
public final class EnergyCubeData {
    static final int SCHEMA = 1;
    // LevelChunk adds keepPacked when persisting a block entity; it is not device payload.
    private static final Set<String> METADATA = Set.of("id", "x", "y", "z", "components", "keepPacked");
    private static final Set<String> KEYS = Set.of("schema", "energy", "items", "discharge_items", "side_modes", "owner", "security");

    static final MapCodec<CompoundTag> RAW = new MapCodec<>() {
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.empty(); }
        @Override public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            var raw = new CompoundTag();
            input.entries().forEach(entry -> ops.getStringValue(entry.getFirst()).result()
                    .filter(key -> !METADATA.contains(key))
                    .ifPresent(key -> raw.put(key, ops.convertTo(NbtOps.INSTANCE, entry.getSecond()))));
            return DataResult.success(raw);
        }
        @Override public <T> RecordBuilder<T> encode(CompoundTag raw, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : raw.keySet()) prefix.add(key, NbtOps.INSTANCE.convertTo(ops, raw.get(key)));
            return prefix;
        }
    };

    private EnergyCubeData() {}

    static @Nullable String rejection(CompoundTag raw, EnergyCubeTier tier, boolean itemsDecoded, int itemCount) {
        boolean hasDischarge = raw.contains("discharge_items");
        int dischargeCount = hasDischarge && raw.get("discharge_items") instanceof ListTag list ? list.size() : tier.dischargingSlots();
        boolean dischargeValid = !hasDischarge || raw.get("discharge_items") instanceof ListTag;
        return rejection(raw, tier, itemsDecoded, itemCount, dischargeValid, dischargeCount);
    }

    static @Nullable String rejection(CompoundTag raw, EnergyCubeTier tier,
                                      boolean itemsDecoded, int itemCount,
                                      boolean dischargeDecoded, int dischargeCount) {
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA) return "unknown schema";
        if (!KEYS.containsAll(raw.keySet())) return "unknown fields";
        if (!(raw.get("energy") instanceof IntTag)) return "invalid energy type";
        int energy = raw.getIntOr("energy", -1);
        if (energy < 0 || energy > tier.capacity()) return "energy outside capacity";
        if (!(raw.get("side_modes") instanceof IntTag) || !validModes(raw.getIntOr("side_modes", -1))) return "invalid side modes";
        if (!(raw.get("items") instanceof ListTag) || !itemsDecoded || itemCount != tier.chargingSlots()) return "unreadable items";
        if (raw.contains("discharge_items") && (!(raw.get("discharge_items") instanceof ListTag)
                || !dischargeDecoded || dischargeCount != tier.dischargingSlots())) {
            return "unreadable discharge items";
        }
        if (raw.contains("owner") && (!(raw.get("owner") instanceof StringTag) || owner(raw.getStringOr("owner", "")) == null)) {
            return "invalid owner";
        }
        if (raw.contains("security") && (!(raw.get("security") instanceof StringTag) || !dev.latrox.rematter.content.SecurityMode.validName(raw.getStringOr("security", "")))) {
            return "invalid security mode";
        }
        return null;
    }

    /** Strict structural empty check without registry decoding: every slot must be an empty compound and energy zero. */
    public static boolean isEmptyTag(CompoundTag raw, EnergyCubeTier tier) {
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA) return false;
        if (!KEYS.containsAll(raw.keySet())) return false;
        if (!(raw.get("energy") instanceof IntTag) || raw.getIntOr("energy", -1) != 0) return false;
        if (!(raw.get("side_modes") instanceof IntTag) || !validModes(raw.getIntOr("side_modes", -1))) return false;
        if (raw.contains("owner") && (!(raw.get("owner") instanceof StringTag) || owner(raw.getStringOr("owner", "")) == null)) return false;
        if (raw.contains("security") && (!(raw.get("security") instanceof StringTag) || !dev.latrox.rematter.content.SecurityMode.validName(raw.getStringOr("security", "")))) return false;
        if (!allEmptyCompounds(raw.get("items"), tier.chargingSlots())) return false;
        if (raw.contains("discharge_items") && !allEmptyCompounds(raw.get("discharge_items"), tier.dischargingSlots())) return false;
        return true;
    }

    private static boolean allEmptyCompounds(net.minecraft.nbt.@Nullable Tag tag, int expectedSize) {
        if (!(tag instanceof ListTag list) || list.size() != expectedSize) return false;
        for (var element : list) {
            if (!(element instanceof CompoundTag compound) || !compound.isEmpty()) return false;
        }
        return true;
    }

    static Optional<List<ItemStack>> items(CompoundTag raw, HolderLookup.Provider registries) {
        return stackList(raw, "items", registries);
    }

    static Optional<List<ItemStack>> dischargeItems(CompoundTag raw, HolderLookup.Provider registries) {
        if (!raw.contains("discharge_items")) return Optional.of(List.of());
        return stackList(raw, "discharge_items", registries);
    }

    private static Optional<List<ItemStack>> stackList(CompoundTag raw, String key, HolderLookup.Provider registries) {
        return Optional.ofNullable(raw.get(key)).flatMap(tag -> ItemStack.OPTIONAL_CODEC.listOf()
                        .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result())
                .filter(stacks -> stacks.stream().noneMatch(stack -> stack.getCount() > 1));
    }

    static boolean validModes(int packed) {
        if (packed < 0 || (packed >>> 12) != 0) return false;
        for (int i = 0; i < 6; i++) if (((packed >>> (i * 2)) & 3) >= EnergyCubeSideMode.values().length) return false;
        return true;
    }

    static @Nullable UUID owner(String value) {
        try {
            var id = UUID.fromString(value);
            return id.toString().equals(value) ? id : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
