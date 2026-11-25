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
    private static final ModConfigSpec.LongValue PRIMAL_REACTOR_ENERGY;
    private static final ModConfigSpec.IntValue MASS_COMPRESSION_DISCOUNT;
    private static final ModConfigSpec.LongValue MASS_COMPRESSION_WASTE;

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
        builder.push("primal_reactor");
        PRIMAL_REACTOR_ENERGY = builder
                .comment("Base Forge Energy (FE) required by a Primal Reactor to condense 1,000 u of Primal Matter into connected Primal Cells.")
                .defineInRange("energyPer1000Primal", 2_000_000L, 100_000L, 100_000_000L);
        MASS_COMPRESSION_DISCOUNT = builder
                .comment("Percentage FE discount applied by a Mass Compression Upgrade when condensing Primal Matter with bulk waste matter (0-90%).")
                .defineInRange("massCompressionDiscountPercent", 60, 0, 90);
        MASS_COMPRESSION_WASTE = builder
                .comment("Bulk waste matter units (Silica, Soil, Alumina, Calcium Oxide, or Mineral Mix) consumed per 1,000 u Primal Matter when a Mass Compression Upgrade is active.")
                .defineInRange("massCompressionWasteUnits", 4_000L, 100L, 64_000L);
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

    public static long primalReactorEnergyPer1000() {
        return !SPEC.isLoaded() ? 2_000_000L : PRIMAL_REACTOR_ENERGY.get();
    }

    public static int massCompressionDiscountPercent() {
        return !SPEC.isLoaded() ? 60 : MASS_COMPRESSION_DISCOUNT.get();
    }

    public static long massCompressionWasteUnits() {
        return !SPEC.isLoaded() ? 4_000L : MASS_COMPRESSION_WASTE.get();
    }

    private static boolean validNamespace(Object value) {
        return value instanceof String namespace && namespace.matches("[a-z][a-z0-9_.-]{0,63}");
    }
}
