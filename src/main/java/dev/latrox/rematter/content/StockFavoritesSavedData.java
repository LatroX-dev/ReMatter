package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Overworld-owned per-player pinned substances for wired Matter Terminals, handheld Wireless Matter Terminals,
 * Matter Drives, and substance pickers. Unknown SubstanceIds remain preserved across datapack reloads.
 */
public final class StockFavoritesSavedData extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PER_PLAYER = StockQuery.MAX_FAVORITES;

    private static final Codec<StockFavoritesSavedData> CODEC = CompoundTag.CODEC.xmap(
            StockFavoritesSavedData::fromTag,
            StockFavoritesSavedData::toTag);
    private static final SavedDataType<StockFavoritesSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "stock_favorites"),
            StockFavoritesSavedData::new,
            CODEC);

    private final Map<UUID, Set<StockKey>> players;
    private final CompoundTag preservedUnknownRaw;

    public StockFavoritesSavedData() {
        this(new HashMap<>(), null);
    }

    private StockFavoritesSavedData(Map<UUID, Set<StockKey>> players, CompoundTag preservedUnknownRaw) {
        this.players = players;
        this.preservedUnknownRaw = preservedUnknownRaw;
    }

    public static StockFavoritesSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Set<StockKey> of(UUID player) {
        return Set.copyOf(players.getOrDefault(player, Set.of()));
    }

    public boolean contains(UUID player, StockKey key) {
        return players.getOrDefault(player, Set.of()).contains(key);
    }

    public boolean toggle(UUID player, StockKey key) {
        if (preservedUnknownRaw != null || !isValidKey(key)) return false;
        var own = players.computeIfAbsent(player, ignored -> new TreeSet<>());
        if (!own.remove(key)) {
            if (own.size() >= MAX_PER_PLAYER) return false;
            own.add(key);
        }
        if (own.isEmpty()) players.remove(player);
        setDirty();
        return true;
    }

    /** One-time import when a player with no server-saved favorites connects with local favorites. */
    public boolean importIfEmpty(UUID player, Collection<StockKey> incoming) {
        if (preservedUnknownRaw != null || players.containsKey(player) || incoming.isEmpty()) return false;
        var valid = new TreeSet<StockKey>();
        for (var key : incoming) {
            if (valid.size() >= MAX_PER_PLAYER) break;
            if (isValidKey(key)) valid.add(key);
        }
        if (valid.isEmpty()) return false;
        players.put(player, valid);
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
        var playersTag = new CompoundTag();
        new TreeMap<>(players).forEach((uuid, keys) -> {
            if (keys.isEmpty()) return;
            var list = new ListTag();
            new TreeSet<>(keys).forEach(key -> {
                var row = new CompoundTag();
                row.putString("substance", key.id());
                row.putString("phase", key.phase());
                list.add(row);
            });
            playersTag.put(uuid.toString(), list);
        });
        root.put("players", playersTag);
        return root;
    }

    public static StockFavoritesSavedData fromTag(CompoundTag raw) {
        if (raw == null || raw.isEmpty()) return new StockFavoritesSavedData();
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA_VERSION) {
            ReMatter.LOGGER.warn("Preserving unreadable StockFavoritesSavedData schema");
            return new StockFavoritesSavedData(new HashMap<>(), raw.copy());
        }
        try {
            var map = new HashMap<UUID, Set<StockKey>>();
            var playersTag = raw.getCompound("players").orElseGet(CompoundTag::new);
            for (String uuidStr : playersTag.keySet()) {
                UUID uuid = UUID.fromString(uuidStr);
                var rows = playersTag.getList(uuidStr).orElseGet(ListTag::new);
                var keys = new TreeSet<StockKey>();
                for (int i = 0; i < Math.min(MAX_PER_PLAYER, rows.size()); i++) {
                    var row = rows.getCompound(i).orElse(null);
                    if (row == null) continue;
                    var key = new StockKey(row.getStringOr("substance", ""), row.getStringOr("phase", ""));
                    if (isValidKey(key)) keys.add(key);
                }
                if (!keys.isEmpty()) map.put(uuid, keys);
            }
            return new StockFavoritesSavedData(map, null);
        } catch (RuntimeException invalid) {
            ReMatter.LOGGER.warn("Preserving malformed StockFavoritesSavedData tag", invalid);
            return new StockFavoritesSavedData(new HashMap<>(), raw.copy());
        }
    }
}
