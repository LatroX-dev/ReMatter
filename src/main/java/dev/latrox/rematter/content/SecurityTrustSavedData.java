package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Overworld-owned per-player trusted player lists used by {@link SecurityMode#TRUSTED}.
 * Unknown future schemas are preserved unchanged.
 */
public final class SecurityTrustSavedData extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TRUSTED_PER_OWNER = 64;

    private static final Codec<SecurityTrustSavedData> CODEC = CompoundTag.CODEC.xmap(
            SecurityTrustSavedData::fromTag,
            SecurityTrustSavedData::toTag);
    private static final SavedDataType<SecurityTrustSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "security_trust"),
            SecurityTrustSavedData::new,
            CODEC);

    private final Map<UUID, Set<UUID>> trustedByOwner;
    private final Map<UUID, String> lastKnownNames;
    private final Map<UUID, SecurityMode> defaultModeByOwner;
    private final @Nullable CompoundTag preservedUnknownRaw;

    public SecurityTrustSavedData() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>(), null);
    }

    private SecurityTrustSavedData(Map<UUID, Set<UUID>> trustedByOwner, Map<UUID, String> lastKnownNames,
                                   Map<UUID, SecurityMode> defaultModeByOwner, @Nullable CompoundTag preservedUnknownRaw) {
        this.trustedByOwner = trustedByOwner;
        this.lastKnownNames = lastKnownNames;
        this.defaultModeByOwner = defaultModeByOwner;
        this.preservedUnknownRaw = preservedUnknownRaw;
    }

    public static SecurityTrustSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean isTrusted(@Nullable ServerLevel level, @Nullable UUID owner, @Nullable UUID actor) {
        if (level == null || owner == null || actor == null) return false;
        return get(level.getServer()).isTrusted(owner, actor);
    }

    public static SecurityMode defaultMode(@Nullable Level level, @Nullable UUID owner) {
        if (!(level instanceof ServerLevel server) || owner == null) return SecurityMode.PUBLIC;
        return get(server.getServer()).defaultMode(owner);
    }

    public SecurityMode defaultMode(@Nullable UUID owner) {
        if (owner == null) return SecurityMode.PUBLIC;
        return defaultModeByOwner.getOrDefault(owner, SecurityMode.PUBLIC);
    }

    public boolean setDefaultMode(UUID owner, SecurityMode mode) {
        if (preservedUnknownRaw != null || owner == null || mode == null) return false;
        SecurityMode previous = defaultModeByOwner.getOrDefault(owner, SecurityMode.PUBLIC);
        if (previous == mode) return true;
        if (mode == SecurityMode.PUBLIC) defaultModeByOwner.remove(owner);
        else defaultModeByOwner.put(owner, mode);
        setDirty();
        return true;
    }

    public boolean isTrusted(@Nullable UUID owner, @Nullable UUID actor) {
        if (owner == null || actor == null) return false;
        return trustedByOwner.getOrDefault(owner, Set.of()).contains(actor);
    }

    public Set<UUID> trustedOf(UUID owner) {
        return Set.copyOf(trustedByOwner.getOrDefault(owner, Set.of()));
    }

    public Map<UUID, String> trustedEntries(UUID owner) {
        var result = new LinkedHashMap<UUID, String>();
        for (UUID id : new TreeSet<>(trustedByOwner.getOrDefault(owner, Set.of()))) {
            result.put(id, lastKnownNames.getOrDefault(id, id.toString()));
        }
        return Map.copyOf(result);
    }

    public Optional<String> nameOf(UUID id) {
        return Optional.ofNullable(lastKnownNames.get(id));
    }

    public boolean add(UUID owner, UUID target, @Nullable String targetName) {
        if (preservedUnknownRaw != null || owner == null || target == null || owner.equals(target)) return false;
        var set = trustedByOwner.computeIfAbsent(owner, ignored -> new TreeSet<>());
        if (set.contains(target)) {
            if (targetName != null && !targetName.isBlank() && !targetName.equals(lastKnownNames.get(target))) {
                lastKnownNames.put(target, targetName.substring(0, Math.min(64, targetName.length())));
                setDirty();
            }
            return false;
        }
        if (set.size() >= MAX_TRUSTED_PER_OWNER) return false;
        set.add(target);
        if (targetName != null && !targetName.isBlank()) {
            lastKnownNames.put(target, targetName.substring(0, Math.min(64, targetName.length())));
        }
        setDirty();
        ChunkRevisions.touchGlobal();
        return true;
    }

    public boolean remove(UUID owner, UUID target) {
        if (preservedUnknownRaw != null || owner == null || target == null) return false;
        var set = trustedByOwner.get(owner);
        if (set == null || !set.remove(target)) return false;
        if (set.isEmpty()) trustedByOwner.remove(owner);
        setDirty();
        ChunkRevisions.touchGlobal();
        return true;
    }

    public Optional<UUID> findTrusted(UUID owner, String query) {
        if (owner == null || query == null || query.isBlank()) return Optional.empty();
        var set = trustedByOwner.get(owner);
        if (set == null || set.isEmpty()) return Optional.empty();
        try {
            UUID parsed = UUID.fromString(query);
            if (set.contains(parsed)) return Optional.of(parsed);
        } catch (IllegalArgumentException ignored) {}
        for (UUID id : set) {
            String known = lastKnownNames.get(id);
            if (known != null && known.equalsIgnoreCase(query)) return Optional.of(id);
        }
        return Optional.empty();
    }

    public int clear(UUID owner) {
        if (preservedUnknownRaw != null || owner == null) return 0;
        var removed = trustedByOwner.remove(owner);
        if (removed == null || removed.isEmpty()) return 0;
        setDirty();
        ChunkRevisions.touchGlobal();
        return removed.size();
    }

    public CompoundTag toTag() {
        if (preservedUnknownRaw != null) return preservedUnknownRaw.copy();
        var root = new CompoundTag();
        root.putInt("schema", SCHEMA_VERSION);
        var ownersTag = new CompoundTag();
        var referencedNames = new TreeMap<String, String>();
        new TreeMap<>(trustedByOwner).forEach((owner, targets) -> {
            if (targets.isEmpty()) return;
            var list = new ListTag();
            new TreeSet<>(targets).forEach(target -> {
                list.add(net.minecraft.nbt.StringTag.valueOf(target.toString()));
                String name = lastKnownNames.get(target);
                if (name != null && !name.isBlank()) referencedNames.put(target.toString(), name);
            });
            ownersTag.put(owner.toString(), list);
        });
        root.put("owners", ownersTag);
        var namesTag = new CompoundTag();
        referencedNames.forEach(namesTag::putString);
        root.put("names", namesTag);
        if (!defaultModeByOwner.isEmpty()) {
            var defaultsTag = new CompoundTag();
            new TreeMap<>(defaultModeByOwner).forEach((owner, mode) -> {
                if (mode != SecurityMode.PUBLIC) defaultsTag.putString(owner.toString(), mode.getSerializedName());
            });
            if (!defaultsTag.isEmpty()) root.put("defaults", defaultsTag);
        }
        return root;
    }

    public static SecurityTrustSavedData fromTag(CompoundTag raw) {
        if (raw == null || raw.isEmpty()) return new SecurityTrustSavedData();
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA_VERSION) {
            ReMatter.LOGGER.warn("Preserving unreadable SecurityTrustSavedData schema");
            return new SecurityTrustSavedData(new HashMap<>(), new HashMap<>(), new HashMap<>(), raw.copy());
        }
        try {
            var map = new HashMap<UUID, Set<UUID>>();
            var ownersTag = raw.getCompound("owners").orElseGet(CompoundTag::new);
            for (String ownerKey : ownersTag.keySet()) {
                UUID owner = UUID.fromString(ownerKey);
                var list = ownersTag.getList(ownerKey).orElseGet(ListTag::new);
                var set = new TreeSet<UUID>();
                for (int i = 0; i < Math.min(MAX_TRUSTED_PER_OWNER, list.size()); i++) {
                    var str = list.getString(i).orElse("");
                    if (str.isEmpty()) continue;
                    UUID target = UUID.fromString(str);
                    if (!target.equals(owner)) set.add(target);
                }
                if (!set.isEmpty()) map.put(owner, set);
            }
            var names = new HashMap<UUID, String>();
            var namesTag = raw.getCompound("names").orElseGet(CompoundTag::new);
            for (String idKey : namesTag.keySet()) {
                UUID id = UUID.fromString(idKey);
                String name = namesTag.getStringOr(idKey, "");
                if (!name.isBlank()) names.put(id, name.substring(0, Math.min(64, name.length())));
            }
            var defaults = new HashMap<UUID, SecurityMode>();
            var defaultsTag = raw.getCompound("defaults").orElseGet(CompoundTag::new);
            for (String ownerKey : defaultsTag.keySet()) {
                UUID owner = UUID.fromString(ownerKey);
                SecurityMode mode = SecurityMode.load(defaultsTag.getStringOr(ownerKey, ""));
                if (mode != SecurityMode.PUBLIC) defaults.put(owner, mode);
            }
            return new SecurityTrustSavedData(map, names, defaults, null);
        } catch (RuntimeException invalid) {
            ReMatter.LOGGER.warn("Preserving malformed SecurityTrustSavedData tag", invalid);
            return new SecurityTrustSavedData(new HashMap<>(), new HashMap<>(), new HashMap<>(), raw.copy());
        }
    }
}
