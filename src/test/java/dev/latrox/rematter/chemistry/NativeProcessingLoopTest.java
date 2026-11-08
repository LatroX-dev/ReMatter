package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native recipes that change item counts must not let synthesis and recovery multiply matter. The recipe facts are
 * those of Hextale 1.9.0, Ender IO 9.0.6-alpha and Occultism 1.262.0.
 */
class NativeProcessingLoopTest {
    private static final ChemistryRegistry REGISTRY = ShippedChemistry.standalone();
    /** Vibrant alloy grinding balls repeat a whole output roll with 75% chance. */
    private static final double OUTPUT_MULTIPLIER = 1.75;
    /** End steel grinding balls multiply every bonus chance by 2.4. */
    private static final double BONUS_MULTIPLIER = 2.4;

    @Test
    void hextaleTransmutationBetweenBasaltAndBlackstoneKeepsTheirConstituents() {
        assertEquals(profile("minecraft:blackstone").matter(), profile("minecraft:basalt").matter());
    }

    @Test
    void grindingBallBonusesNeverMillASynthesizableInputIntoMoreMatter() {
        assertMilling("minecraft:quartz", "enderio:powdered_quartz", 1 + bonus(0.1));
        assertMilling("minecraft:quartz_block", "minecraft:quartz", 2 + 2 * bonus(0.25));
        assertMilling("minecraft:obsidian", "enderio:powdered_obsidian", 4 * OUTPUT_MULTIPLIER);
        assertMilling("minecraft:clay", "minecraft:clay_ball", (2 + bonus(0.1)) * OUTPUT_MULTIPLIER);
        assertMilling("minecraft:sandstone", "minecraft:sand", 2 + 2 * bonus(0.4));
        assertMilling("minecraft:glowstone", "minecraft:glowstone_dust", 3 + bonus(0.8));
        assertMilling("minecraft:cobblestone", "minecraft:sand", bonus(0.1) * OUTPUT_MULTIPLIER);
        assertTrue(REGISTRY.synthesisPolicy().allowsItem("minecraft:quartz_block"), "Quartz blocks stay synthesizable");
    }

    @Test
    void occultismCrushingIgnoresTheSpiritMultiplierForSynthesizableBlocks() {
        assertEquals(profile("minecraft:end_stone").matter(), profile("occultism:crushed_end_stone").matter());
        assertEquals(profile("minecraft:calcite").matter(), profile("occultism:crushed_calcite").matter());
        assertEquals(profile("minecraft:blackstone").matter(), profile("occultism:crushed_blackstone").matter());
        assertFalse(REGISTRY.synthesisPolicy().allows("occultism:echo_dust"));
    }

    /** Upper bound of the expected output: a bonus chance raised by the strongest grinding ball, capped at certainty. */
    private static double bonus(double chance) {
        return Math.min(1, chance * BONUS_MULTIPLIER);
    }

    /** A synthesizable input must not recover more of any of its own substances after one milling pass; others may. */
    private static void assertMilling(String input, String output, double expectedCount) {
        MaterialProfile source = profile(input);
        MaterialProfile product = profile(output);
        if (!REGISTRY.synthesisPolicy().allows(input)) return;
        for (Map.Entry<SubstanceKey, Long> entry : source.matter().entrySet()) {
            double perInput = (double) entry.getValue() / source.itemCount();
            double recovered = expectedCount * product.matter().getOrDefault(entry.getKey(), 0L) / product.itemCount();
            assertTrue(recovered <= perInput, input + " mills into " + recovered + " " + entry.getKey() + " from " + perInput);
        }
    }

    private static MaterialProfile profile(String item) {
        return REGISTRY.profile(item).orElseThrow(() -> new AssertionError("Missing profile " + item));
    }
}
