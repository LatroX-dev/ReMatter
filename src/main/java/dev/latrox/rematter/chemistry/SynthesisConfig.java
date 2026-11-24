package dev.latrox.rematter.chemistry;

import java.util.List;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common-loaded synthesis unification settings. Registered as {@link ModConfig.Type#COMMON} so it is
 * already loaded when {@link dev.latrox.rematter.data.ChemistryData} prepares a catalogue reload.
 */
public final class SynthesisConfig {
    public static final List<String> DEFAULT_MOD_PRIORITY = List.of(
            "minecraft",
            "alltheores",
            "mekanism",
            "mekmm",
            "ae2",
            "extendedae",
            "ae2omnicells",
            "refinedstorage",
            "enderio",
            "ftbic",
            "oritech",
            "energizedpower",
            "occultism",
            "colossal_reactors",
            "infinity_nexus_mod",
            "appflux",
            "appgen",
            "mysticalagriculture",
            "forbidden_arcanus",
            "hextale",
            "thaumaturge",
            "stellaris",
            "nautec",
            "rematter",
            "jaopca");

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue UNIFY_OUTPUTS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_PRIORITY;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("synthesis");
        UNIFY_OUTPUTS = builder
                .comment("Collapse cross-mod duplicate synthesis outputs sharing a synthesis_group to one canonical item per group. Decomposer and Analyzer still accept every variant.")
                .define("unifyOutputs", true);
        MOD_PRIORITY = builder
                .comment("Preferred mod namespace order when selecting the single canonical synthesis output in a synthesis_group. Namespaces not listed fall back to alphabetical order.")
                .defineListAllowEmpty("modPriority", () -> DEFAULT_MOD_PRIORITY, () -> "minecraft", SynthesisConfig::validNamespace);
        builder.pop();
        SPEC = builder.build();
    }

    private SynthesisConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    public static boolean unifyOutputs() {
        return !SPEC.isLoaded() || UNIFY_OUTPUTS.get();
    }

    public static List<String> modPriority() {
        if (!SPEC.isLoaded()) return DEFAULT_MOD_PRIORITY;
        return MOD_PRIORITY.get().stream().map(String::valueOf).toList();
    }

    private static boolean validNamespace(Object value) {
        return value instanceof String namespace && namespace.matches("[a-z][a-z0-9_.-]{0,63}");
    }
}
