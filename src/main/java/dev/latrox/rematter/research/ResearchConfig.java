package dev.latrox.rematter.research;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ResearchConfig {
    public enum Sharing { PERSONAL_AND_TEAM, SERVER }
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.EnumValue<Sharing> SHARING;
    private static final ModConfigSpec.IntValue DISCOVERY_MINIMUM;
    static {
        var builder = new ModConfigSpec.Builder(); builder.push("research");
        ENABLED = builder.comment("Require researched material families in addition to the synthesis allowlist.").define("enabled", true);
        SHARING = builder.comment("Knowledge view only. Matter, ownership and network access remain separate.").defineEnum("sharing", Sharing.PERSONAL_AND_TEAM);
        DISCOVERY_MINIMUM = builder.comment("Earned material families required for Discovery. Administrative/start grants do not count.").defineInRange("discoveryMinimumEarned", 32, 0, 8192);
        builder.pop(); SPEC = builder.build();
    }
    private ResearchConfig() {}
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER, SPEC); }
    public static boolean enabled() { return ENABLED.get(); }
    public static Sharing sharing() { return SHARING.get(); }
    public static int discoveryMinimum() { return DISCOVERY_MINIMUM.get(); }
}
