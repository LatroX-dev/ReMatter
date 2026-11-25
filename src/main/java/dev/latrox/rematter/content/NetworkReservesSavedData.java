package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Overworld-owned per-network-owner global minimum stock reserves configured from Matter Terminals.
 * Protects automated/machine consumption from draining substances (or Primal Matter) below the configured floor.
 * Unknown SubstanceIds and schemas remain preserved across reloads.
 */
public final class NetworkReservesSavedData extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PER_OWNER = 256;
    public static final long MAX_RESERVE = 1_000_000_000_000L;

    private static final Codec<NetworkReservesSavedData> CODEC = CompoundTag.CODEC.xmap(
            NetworkReservesSavedData::fromTag,
            NetworkReservesSavedData::toTag);
    private static final SavedDataType<NetworkReservesSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "network_reserves"),
            NetworkReservesSavedData::new,
            CODEC);

    private final Map<UUID, Map<StockKey, Long>> owners;
    private final CompoundTag preservedUnknownRaw;

    public NetworkReservesSavedData() {
        this(new HashMap<>(), null);
    }

    private NetworkReservesSavedData(Map<UUID, Map<StockKey, Long>> owners, CompoundTag preservedUnknownRaw) {
        this.owners = owners;
        this.preservedUnknownRaw = preservedUnknownRaw;
    }

    public static NetworkReservesSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<StockKey, Long> of(UUID owner) {
        var map = owners.get(owner);
        return map == null ? Map.of() : Map.copyOf(map);
    }

    public long amount(UUID owner, SubstanceKey key) {
        if (owner == null || key == null) return 0L;
        var map = owners.get(owner);
        if (map == null || map.isEmpty()) return 0L;
        return map.getOrDefault(new StockKey(key.id().toString(), key.phase().name().toLowerCase(Locale.ROOT)), 0L);
    }

    public long amount(UUID owner, StockKey key) {
        if (owner == null || key == null) return 0L;
        var map = owners.get(owner);
        return map == null ? 0L : map.getOrDefault(key, 0L);
    }

    public boolean set(UUID owner, StockKey key, long amount) {
        if (preservedUnknownRaw != null || owner == null || !isValidKey(key) || amount < 0L || amount > MAX_RESERVE) {
            return false;
        }
        var own = owners.computeIfAbsent(owner, ignored -> new TreeMap<>());
        if (amount == 0L) {
            if (own.remove(key) == null) return false;
            if (own.isEmpty()) owners.remove(owner);
            setDirty();
            return true;
        }
        if (!own.containsKey(key) && own.size() >= MAX_PER_OWNER) return false;
        Long prev = own.put(key, amount);
        if (prev != null && prev == amount) return false;
        setDirty();
        return true;
    }

    public static boolean isValidKey(StockKey key) {
        if (key == null || key.id().length() > 256 || !StockQuery.PHASES.contains(key.phase())) return false;
        try {
            SubstanceId.parse(key.id());
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    public CompoundTag toTag() {
        if (preservedUnknownRaw != null) return preservedUnknownRaw.copy();
        var root = new CompoundTag();
        root.putInt("schema", SCHEMA_VERSION);
        var ownersTag = new CompoundTag();
        new TreeMap<>(owners).forEach((uuid, reserves) -> {
            if (reserves.isEmpty()) return;
            var list = new ListTag();
            new TreeMap<>(reserves).forEach((key, amount) -> {
                if (amount == null || amount <= 0L) return;
                var row = new CompoundTag();
                row.putString("substance", key.id());
                row.putString("phase", key.phase());
                row.putLong("amount", amount);
                list.add(row);
            });
            if (!list.isEmpty()) ownersTag.put(uuid.toString(), list);
        });
        root.put("owners", ownersTag);
        return root;
    }

    public static NetworkReservesSavedData fromTag(CompoundTag raw) {
        if (raw == null || raw.isEmpty()) return new NetworkReservesSavedData();
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA_VERSION) {
            ReMatter.LOGGER.warn("Preserving unreadable NetworkReservesSavedData schema");
            return new NetworkReservesSavedData(new HashMap<>(), raw.copy());
        }
        try {
            var map = new HashMap<UUID, Map<StockKey, Long>>();
            var ownersTag = raw.getCompound("owners").orElseGet(CompoundTag::new);
            for (String uuidStr : ownersTag.keySet()) {
                UUID uuid = UUID.fromString(uuidStr);
                var rows = ownersTag.getList(uuidStr).orElseGet(ListTag::new);
                var reserves = new TreeMap<StockKey, Long>();
                for (int i = 0; i < Math.min(MAX_PER_OWNER, rows.size()); i++) {
                    var row = rows.getCompound(i).orElse(null);
                    if (row == null) continue;
                    var key = new StockKey(row.getStringOr("substance", ""), row.getStringOr("phase", ""));
                    long amount = row.getLongOr("amount", 0L);
                    if (isValidKey(key) && amount > 0L && amount <= MAX_RESERVE) {
                        reserves.put(key, amount);
                    }
                }
                if (!reserves.isEmpty()) map.put(uuid, reserves);
            }
            return new NetworkReservesSavedData(map, null);
        } catch (RuntimeException invalid) {
            ReMatter.LOGGER.warn("Preserving malformed NetworkReservesSavedData tag", invalid);
            return new NetworkReservesSavedData(new HashMap<>(), raw.copy());
        }
    }
}
