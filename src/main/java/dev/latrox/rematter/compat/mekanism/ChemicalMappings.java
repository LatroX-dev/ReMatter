package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.Phase;

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
            new Mapping("mekanism:hydrogen", HYDROGEN, 1),
            new Mapping("mekanism:oxygen", OXYGEN, 1),
            new Mapping("mekanism:water_vapor", STEAM, 1),
            gas("chlorine", 1),
            gas("sulfur_dioxide", 1),
            gas("sulfur_trioxide", 1),
            gas("sulfuric_acid", 1),
            // Native infusing: 1 H2 + 1 Cl2 -> 1 external HCl unit.
            // One ordinary HCl formula unit therefore occupies half that amount.
            gas("hydrogen_chloride", 2),
            gas("hydrofluoric_acid", 1),
            gas("ethene", 1),
            // Native oxidizing/crystallizing: 1 dust <-> 100 lithium units.
            gas("lithium", 10),
            // Native chemical conversion: 1 ingot -> 200 osmium units.
            gas("osmium", 5));

    private ChemicalMappings() {}

    private static Mapping gas(String path, long units) {
        return new Mapping("mekanism:" + path, SubstanceKey.of("rematter:" + path, Phase.GAS), units);
    }

    record Mapping(String chemicalId, SubstanceKey matter, long unitsPerChemical) {
        Mapping {
            if (unitsPerChemical <= 0) throw new IllegalArgumentException("Chemical ratios must be positive integral quantities");
        }
    }
}
