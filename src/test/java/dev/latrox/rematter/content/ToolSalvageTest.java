package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolSalvageTest {
    private static final SubstanceKey IRON = SubstanceKey.of("rematter:iron", Phase.SOLID);
    private static final SubstanceKey GOLD = SubstanceKey.of("rematter:gold", Phase.SOLID);
    private static final SubstanceKey COPPER = SubstanceKey.of("rematter:copper", Phase.SOLID);

    @Test
    void scaleMatterRoundsStrictlyDownAndRejectsZeroOrInvalidDurability() {
        Map<SubstanceKey, Long> pickaxe = Map.of(IRON, 3_000L);
        assertEquals(Map.of(IRON, 1_500L), ToolSalvage.scaleMatter(pickaxe, 125, 250).orElseThrow());
        assertEquals(Map.of(IRON, 2_988L), ToolSalvage.scaleMatter(pickaxe, 1, 250).orElseThrow());
        assertEquals(Map.of(IRON, 12L), ToolSalvage.scaleMatter(pickaxe, 249, 250).orElseThrow());

        // A 1,000-unit shovel with maxDamage > 1,000 at 1 durability remaining would round down to 0 -> rejected.
        assertTrue(ToolSalvage.scaleMatter(Map.of(IRON, 1_000L), 1_560, 1_561).isEmpty());
        assertTrue(ToolSalvage.scaleMatter(pickaxe, 250, 250).isEmpty());
        assertTrue(ToolSalvage.scaleMatter(pickaxe, -1, 250).isEmpty());
        assertTrue(ToolSalvage.scaleMatter(pickaxe, 0, 0).isEmpty());
    }

    @Test
    void shippedCatalogueIncludesReviewedSalvageAndStatePreservingCrystalsWhileExcludingVillagerDiamondGear() throws Exception {
        ChemistryRegistry registry = ShippedChemistry.standalone();

        Map<String, Long> pieceUnits = Map.of(
                "shovel", 1_000L, "sword", 2_000L, "hoe", 2_000L, "pickaxe", 3_000L,
                "axe", 3_000L, "boots", 4_000L, "helmet", 5_000L, "leggings", 7_000L, "chestplate", 8_000L);
        Map<String, SubstanceKey> metals = Map.of("copper", COPPER, "iron", IRON, "golden", GOLD);

        for (var metal : metals.entrySet()) {
            for (var piece : pieceUnits.entrySet()) {
                String id = "minecraft:" + metal.getKey() + "_" + piece.getKey();
                var profile = registry.profile(id).orElseThrow(() -> new AssertionError("Missing salvage profile " + id));
                assertEquals(1, profile.itemCount(), id);
                assertEquals(Map.of(metal.getValue(), piece.getValue()), profile.matter(), id);
                assertFalse(registry.synthesisPolicy().allowsItem(id), "Equipment must never be synthesizable: " + id);
            }
        }

        for (String tier : List.of("diamond", "netherite")) {
            for (String piece : pieceUnits.keySet()) {
                assertTrue(registry.profile("minecraft:" + tier + "_" + piece).isEmpty(),
                        "Progression gear must stay excluded from generic salvage: " + tier + "_" + piece);
            }
        }

        for (String charged : List.of("ae2:charged_certus_quartz_crystal", "appflux:redstone_crystal",
                "appflux:charged_redstone", "appflux:charged_redstone_block", "extendedae:entro_ingot")) {
            assertTrue(registry.profile(charged).isPresent(), charged);
            assertFalse(registry.synthesisPolicy().allowsItem(charged), charged);
        }
        assertNotEquals(
                registry.profile("ae2:certus_quartz_crystal").orElseThrow().matter().keySet(),
                registry.profile("ae2:charged_certus_quartz_crystal").orElseThrow().matter().keySet());
        assertEquals(Set.of(
                SubstanceKey.of("rematter:entro", Phase.SOLID),
                GOLD,
                SubstanceKey.of("rematter:lapis", Phase.SOLID)),
                registry.profile("extendedae:entro_ingot").orElseThrow().matter().keySet());
    }
}
