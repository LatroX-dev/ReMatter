package dev.latrox.rematter.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.StockQuery;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Favorite substances of each local player, kept in one client file. A stock view sends the current player's favorites
 * with its query, so the server keeps them within its row limit. Favorites of substances that are currently undefined
 * stay stored. An unreadable file is left untouched and favorites are then kept for the session only.
 */
final class StockFavorites {
    static final int SCHEMA_VERSION = 1;
    static final int MAX_PER_PLAYER = StockQuery.MAX_FAVORITES;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static StockFavorites instance;

    private final Path file;
    private final Map<UUID, Set<StockKey>> favorites;
    private final boolean writable;
    private int revision;

    private StockFavorites(Path file, Map<UUID, Set<StockKey>> favorites, boolean writable) {
        this.file = file;
        this.favorites = favorites;
        this.writable = writable;
    }

    static StockFavorites get() {
        if (instance == null) instance = load(FMLPaths.CONFIGDIR.get().resolve("rematter").resolve("stock_favorites.json"));
        return instance;
    }

    static StockFavorites load(Path file) {
        if (!Files.exists(file)) return new StockFavorites(file, new HashMap<>(), true);
        try {
            return new StockFavorites(file, decode(JsonParser.parseString(Files.readString(file))), true);
        } catch (IOException | RuntimeException unreadable) {
            LOGGER.warn("ReMatter favorites in {} cannot be read and are left unchanged; favorites are kept for this session only", file, unreadable);
            return new StockFavorites(file, new HashMap<>(), false);
        }
    }

    boolean contains(UUID player, StockKey key) { return favorites.getOrDefault(player, Set.of()).contains(key); }

    Set<StockKey> of(UUID player) { return Set.copyOf(favorites.getOrDefault(player, Set.of())); }

    /** Changes since the screen last filtered its rows. */
    int revision() { return revision; }

    boolean toggle(UUID player, StockKey key) {
        validate(key);
        var own = favorites.computeIfAbsent(player, ignored -> new TreeSet<>());
        if (!own.remove(key)) {
            if (own.size() >= MAX_PER_PLAYER) return false;
            own.add(key);
        }
        if (own.isEmpty()) favorites.remove(player);
        revision++;
        save();
        return true;
    }

    private void save() {
        if (!writable) return;
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, encode(favorites).toString());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failed) {
            LOGGER.warn("ReMatter favorites cannot be saved to {}", file, failed);
        }
    }

    static JsonObject encode(Map<UUID, Set<StockKey>> favorites) {
        var players = new JsonObject();
        new TreeMap<>(favorites).forEach((player, keys) -> {
            var list = new JsonArray();
            new TreeSet<>(keys).forEach(key -> {
                var entry = new JsonObject();
                entry.addProperty("substance", key.id());
                entry.addProperty("phase", key.phase());
                list.add(entry);
            });
            players.add(player.toString(), list);
        });
        var root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.add("players", players);
        return root;
    }

    /** Only the current schema is accepted; anything else is rejected as a whole. */
    static Map<UUID, Set<StockKey>> decode(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        if (!root.keySet().equals(Set.of("schema_version", "players"))) throw new IllegalArgumentException("Unexpected favorites fields");
        var version = root.getAsJsonPrimitive("schema_version");
        if (!version.isNumber() || version.getAsBigDecimal().compareTo(BigDecimal.valueOf(SCHEMA_VERSION)) != 0) {
            throw new IllegalArgumentException("Unsupported favorites schema");
        }
        var result = new HashMap<UUID, Set<StockKey>>();
        for (var player : root.getAsJsonObject("players").entrySet()) {
            UUID id = UUID.fromString(player.getKey());
            if (!id.toString().equals(player.getKey())) throw new IllegalArgumentException("Non-canonical player ID");
            var keys = new TreeSet<StockKey>();
            for (var value : player.getValue().getAsJsonArray()) {
                JsonObject entry = value.getAsJsonObject();
                if (!entry.keySet().equals(Set.of("substance", "phase"))) throw new IllegalArgumentException("Unexpected favorite fields");
                var key = new StockKey(entry.getAsJsonPrimitive("substance").getAsString(), entry.getAsJsonPrimitive("phase").getAsString());
                validate(key);
                if (!keys.add(key)) throw new IllegalArgumentException("Duplicate favorite");
            }
            if (keys.isEmpty() || keys.size() > MAX_PER_PLAYER) throw new IllegalArgumentException("Invalid favorite count");
            result.put(id, keys);
        }
        return result;
    }

    private static void validate(StockKey key) {
        SubstanceId.parse(key.id());
        if (!StockQuery.PHASES.contains(key.phase())) throw new IllegalArgumentException("Unknown favorite phase");
    }
}
