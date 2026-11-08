package dev.latrox.rematter.chemistry;

import java.util.Map;
import java.util.Set;

/** Core substances and balanced processes. Item, fluid and gas permissions come from the generated catalogue data. */
public final class BuiltinChemistry {
    public static final SubstanceKey COPPER = key("copper", Phase.SOLID);
    public static final SubstanceKey IRON = key("iron", Phase.SOLID);
    public static final SubstanceKey GOLD = key("gold", Phase.SOLID);
    public static final SubstanceKey CARBON = key("carbon", Phase.SOLID);
    public static final SubstanceKey SILICON = key("silicon", Phase.SOLID);
    public static final SubstanceKey ALUMINUM = key("aluminum", Phase.SOLID);
    public static final SubstanceKey CALCIUM = key("calcium", Phase.SOLID);
    public static final SubstanceKey HYDROGEN = key("hydrogen", Phase.GAS);
    public static final SubstanceKey OXYGEN = key("oxygen", Phase.GAS);
    public static final SubstanceKey WATER = key("water", Phase.LIQUID);
    public static final SubstanceKey STEAM = key("water", Phase.GAS);
    public static final SubstanceKey ICE = key("water", Phase.SOLID);
    public static final SubstanceKey SILICA = key("silica", Phase.SOLID);
    public static final SubstanceKey ALUMINA = key("alumina", Phase.SOLID);
    public static final SubstanceKey LIME = key("calcium_oxide", Phase.SOLID);
    public static final SubstanceKey ROCK = key("mineral_mix", Phase.SOLID);
    public static final SubstanceKey LAVA = key("mineral_mix", Phase.LIQUID);
    public static final SubstanceKey SOIL = key("soil", Phase.SOLID);
    public static final SubstanceKey CARBON_DIOXIDE = key("carbon_dioxide", Phase.GAS);

    private static final Map<SubstanceKey, Long> STONE_MATTER =
            Map.of(SILICA, 1_000L, ALUMINA, 1_000L, LIME, 1_000L);

    private BuiltinChemistry() {}

    public static ChemistryRegistry create() {
        ChemistryRegistry.Builder builder = ChemistryRegistry.builder();
        element(builder, COPPER, "Cu", 1);
        element(builder, IRON, "Fe", 1);
        element(builder, GOLD, "Au", 1);
        element(builder, CARBON, "C", 1);
        element(builder, SILICON, "Si", 1);
        element(builder, ALUMINUM, "Al", 1);
        element(builder, CALCIUM, "Ca", 1);
        element(builder, HYDROGEN, "H₂", 2);
        element(builder, OXYGEN, "O₂", 2);

        substance(builder, WATER.id(), "H₂O", SubstanceKind.COMPOUND,
                Map.of(HYDROGEN.id(), 2L, OXYGEN.id(), 1L), Set.of(Phase.SOLID, Phase.LIQUID, Phase.GAS));
        substance(builder, SILICA.id(), "SiO₂", SubstanceKind.COMPOUND,
                Map.of(SILICON.id(), 1L, OXYGEN.id(), 2L), Set.of(Phase.SOLID));
        substance(builder, ALUMINA.id(), "Al₂O₃", SubstanceKind.COMPOUND,
                Map.of(ALUMINUM.id(), 2L, OXYGEN.id(), 3L), Set.of(Phase.SOLID));
        substance(builder, LIME.id(), "CaO", SubstanceKind.COMPOUND,
                Map.of(CALCIUM.id(), 1L, OXYGEN.id(), 1L), Set.of(Phase.SOLID));
        substance(builder, ROCK.id(), "", SubstanceKind.MIXTURE,
                Map.of(SILICON.id(), 1L, ALUMINUM.id(), 2L, CALCIUM.id(), 1L, OXYGEN.id(), 6L),
                Set.of(Phase.SOLID, Phase.LIQUID));
        substance(builder, SOIL.id(), "", SubstanceKind.MIXTURE,
                Map.of(SILICON.id(), 1L, OXYGEN.id(), 3L, HYDROGEN.id(), 2L, CARBON.id(), 1L),
                Set.of(Phase.SOLID));
        substance(builder, CARBON_DIOXIDE.id(), "CO₂", SubstanceKind.COMPOUND,
                Map.of(CARBON.id(), 1L, OXYGEN.id(), 2L), Set.of(Phase.GAS));

        process(builder, "water_electrolysis", ProcessType.ELECTROLYSIS,
                Map.of(WATER, 1_000L), Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), 32_000, 200);
        process(builder, "water_synthesis", ProcessType.REACTION,
                Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), Map.of(WATER, 1_000L), 16_000, 200);
        process(builder, "water_evaporation", ProcessType.PHASE_CHANGE,
                Map.of(WATER, 1_000L), Map.of(STEAM, 1_000L), 16_000, 200);
        process(builder, "steam_condensation", ProcessType.PHASE_CHANGE,
                Map.of(STEAM, 1_000L), Map.of(WATER, 1_000L), 8_000, 200);
        process(builder, "water_freezing", ProcessType.PHASE_CHANGE,
                Map.of(WATER, 1_000L), Map.of(ICE, 1_000L), 8_000, 200);
        process(builder, "ice_melting", ProcessType.PHASE_CHANGE,
                Map.of(ICE, 1_000L), Map.of(WATER, 1_000L), 8_000, 200);
        process(builder, "mineral_melting", ProcessType.PHASE_CHANGE,
                Map.of(ROCK, 1_000L), Map.of(LAVA, 1_000L), 128_000, 400);
        process(builder, "lava_cooling", ProcessType.PHASE_CHANGE,
                Map.of(LAVA, 1_000L), Map.of(ROCK, 1_000L), 16_000, 400);
        process(builder, "mineral_separation", ProcessType.SEPARATION,
                Map.of(ROCK, 1_000L), STONE_MATTER, 48_000, 300);
        process(builder, "mineral_blending", ProcessType.REACTION,
                STONE_MATTER, Map.of(ROCK, 1_000L), 32_000, 300);
        process(builder, "soil_separation", ProcessType.SEPARATION,
                Map.of(SOIL, 1_000L), Map.of(SILICA, 1_000L, CARBON, 1_000L, WATER, 1_000L), 24_000, 200);
        process(builder, "soil_blending", ProcessType.REACTION,
                Map.of(SILICA, 1_000L, CARBON, 1_000L, WATER, 1_000L), Map.of(SOIL, 1_000L), 16_000, 200);
        process(builder, "silica_electrolysis", ProcessType.ELECTROLYSIS,
                Map.of(SILICA, 1_000L), Map.of(SILICON, 1_000L, OXYGEN, 1_000L), 64_000, 400);
        process(builder, "silica_synthesis", ProcessType.REACTION,
                Map.of(SILICON, 1_000L, OXYGEN, 1_000L), Map.of(SILICA, 1_000L), 32_000, 300);
        process(builder, "carbon_oxidation", ProcessType.REACTION,
                Map.of(CARBON, 1_000L, OXYGEN, 1_000L), Map.of(CARBON_DIOXIDE, 1_000L), 16_000, 200);
        return builder.build();
    }

    private static void element(ChemistryRegistry.Builder builder, SubstanceKey key,
                                String symbol, long atomsPerUnit) {
        substance(builder, key.id(), symbol, SubstanceKind.ELEMENT,
                Map.of(key.id(), atomsPerUnit), Set.of(key.phase()));
    }

    private static void substance(ChemistryRegistry.Builder builder, SubstanceId id, String symbol,
                                  SubstanceKind kind, Map<SubstanceId, Long> composition, Set<Phase> phases) {
        builder.addSubstance(new SubstanceDefinition(id,
                "substance." + id.namespace() + "." + id.path().replace('/', '.'), symbol,
                kind, new Composition(composition), phases));
    }

    private static void process(ChemistryRegistry.Builder builder, String path, ProcessType type,
                                Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                                long energy, int ticks) {
        builder.addProcess(new ProcessRecipe(SubstanceId.parse("rematter:" + path),
                type, inputs, outputs, energy, ticks));
    }

    private static SubstanceKey key(String path, Phase phase) {
        return SubstanceKey.of("rematter:" + path, phase);
    }
}
