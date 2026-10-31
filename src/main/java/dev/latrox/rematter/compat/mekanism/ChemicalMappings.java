package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.chemistry.SubstanceKey;

import java.util.List;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.HYDROGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.OXYGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.STEAM;

/** Exact mappings for Mekanism: Version Locked 26.1.2, release 2.11. */
final class ChemicalMappings {
    /*
     * The shipped separator/water recipe consumes 2 mB water and produces
     * 2 hydrogen + 1 oxygen. ReMatter uses the same molecular quantities:
     * 1,000 water -> 1,000 hydrogen + 500 oxygen. No rounding or scaling applies.
     * Rotary water_vapor also exchanges 1 chemical unit for 1 mB vanilla water.
     * The distinct mekanism:steam chemical is deliberately not an alias here.
     */
    static final List<Mapping> ALL = List.of(
            new Mapping("mekanism:hydrogen", HYDROGEN),
            new Mapping("mekanism:oxygen", OXYGEN),
            new Mapping("mekanism:water_vapor", STEAM));

    private ChemicalMappings() {}

    record Mapping(String chemicalId, SubstanceKey matter) {}
}
