package dev.latrox.rematter.content;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side chunkloading policy governing Spatial Anchors and Anchor Upgrades. */
public final class ChunkLoadingConfig {
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue MAX_CHUNKS_PER_PLAYER;
    private static final ModConfigSpec.BooleanValue ONLY_WHILE_OWNER_ONLINE;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("chunkloading");
        ENABLED = builder
                .comment("Enable ReMatter native chunkloading via Spatial Anchors and Anchor Upgrades.")
                .define("enabled", true);
        MAX_CHUNKS_PER_PLAYER = builder
                .comment("Maximum distinct force-loaded chunks allowed per player across all dimensions.")
                .defineInRange("maxChunksPerPlayer", 32, 0, 256);
        ONLY_WHILE_OWNER_ONLINE = builder
                .comment("When true, a player's ReMatter chunk anchors stay active only while that player is online.")
                .define("onlyWhileOwnerOnline", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ChunkLoadingConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "rematter-chunkloading.toml");
    }

    public static boolean enabled() {
        return !SPEC.isLoaded() || ENABLED.get();
    }

    public static int maxChunksPerPlayer() {
        return !SPEC.isLoaded() ? 32 : MAX_CHUNKS_PER_PLAYER.get();
    }

    public static boolean onlyWhileOwnerOnline() {
        return SPEC.isLoaded() && ONLY_WHILE_OWNER_ONLINE.get();
    }
}
