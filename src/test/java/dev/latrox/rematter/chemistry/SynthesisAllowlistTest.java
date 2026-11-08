package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Offline checks of the shipped synthesis allowlist against the product boundaries of the catalogue. */
class SynthesisAllowlistTest {
    private static final ChemistryRegistry REGISTRY = ShippedChemistry.standalone();

    @Test
    void specialLootBossAndTopTierProgressionMaterialsAreRecoveryOnly() {
        for (String item : List.of("occultism:echo_dust", "jaopca:storage_blocks.echo",
                "mysticalagriculture:supremium_essence", "mysticalagriculture:supremium_ingot", "mysticalagriculture:supremium_gemstone",
                "mysticalagradditions:insanium_essence", "mysticalagriculture:awakened_supremium_essence",
                "mysticalagriculture:awakened_supremium_block", "forbidden_arcanus:deorum_ingot", "forbidden_arcanus:stellarite_piece",
                "forbidden_arcanus:corrupted_arcane_crystal", "occultism:obsidian_dust", "enderio:powdered_ender_pearl")) {
            assertTrue(REGISTRY.profile(item).isPresent(), item);
            assertFalse(REGISTRY.synthesisPolicy().allows(item), item);
        }
        for (String item : List.of("minecraft:ender_pearl", "minecraft:obsidian", "mysticalagriculture:imperium_essence")) {
            assertTrue(REGISTRY.synthesisPolicy().allowsItem(item), item);
        }
    }
}
