package dev.latrox.rematter.chemistry;

import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.network.CatalogWireCodec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies canonical synthesis unification, mod-priority fallback, migration aliases, and decomposer retention. */
class SynthesisUnifierTest {
    @Test
    void shippedCatalogueUnifiesCrossModDuplicatesUsingReForgingPriorityWhileRetainingAllDecomposerProfiles() {
        ChemistryRegistry unified = ShippedChemistry.standalone();
        ChemistryRegistry raw = ShippedChemistry.loadUnunified(ShippedChemistry.MAIN_RESOURCES);

        assertTrue(raw.synthesisPolicy().entries().size() > unified.synthesisPolicy().entries().size() + 150,
                "Expected >150 duplicate cross-mod synthesis entries to collapse");
        assertEquals(raw.profiles().size(), unified.profiles().size(),
                "Unification must never remove Decomposer or Analyzer profiles");

        // Canonical winners matching ReForging priority (minecraft -> alltheores -> mekanism -> ae2 -> ... -> rematter -> jaopca)
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:copper_dust"));
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:iron_dust"));
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:gold_dust"));
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:steel_ingot"));
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:tin_ingot"));
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:lead_ingot"));
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:silver_ingot"));
        assertTrue(unified.synthesisPolicy().allowsItem("mekanism:dust_quartz"));
        assertTrue(unified.synthesisPolicy().allowsItem("ae2:silicon"));
        assertTrue(unified.synthesisPolicy().allowsItem("ae2:ender_dust"));
        assertTrue(unified.synthesisPolicy().allowsItem("alltheores:salt_dust"));

        // Non-canonical duplicates are hidden from synthesis, remain in Decomposer, and alias to canonical target.
        for (var entry : Map.ofEntries(
                Map.entry("rematter:copper_dust", "alltheores:copper_dust"),
                Map.entry("mekanism:dust_iron", "alltheores:iron_dust"),
                Map.entry("oritech:steel_ingot", "alltheores:steel_ingot"),
                Map.entry("enderio:powdered_quartz", "mekanism:dust_quartz"),
                Map.entry("enderio:silicon", "ae2:silicon"),
                Map.entry("stellaris:silicon", "ae2:silicon"),
                Map.entry("rematter:ender_pearl_powder", "ae2:ender_dust"),
                Map.entry("bakeries:salt", "alltheores:salt_dust"),
                Map.entry("croptopia:salt", "alltheores:salt_dust"),
                Map.entry("mekanism:salt", "alltheores:salt_dust"),
                Map.entry("nautec:salt", "alltheores:salt_dust"),
                Map.entry("refurbished_furniture:sea_salt", "alltheores:salt_dust")
        ).entrySet()) {
            assertTrue(unified.profile(entry.getKey()).isPresent(), "Decomposer must retain " + entry.getKey());
            assertFalse(unified.synthesisPolicy().allowsItem(entry.getKey()), "Duplicate must not be offered: " + entry.getKey());
            assertEquals(entry.getValue(), unified.synthesisPolicy().canonicalTarget(entry.getKey()), entry.getKey());
        }
        assertTrue(unified.profile("croptopia:salt_ore").isPresent());
        assertFalse(unified.synthesisPolicy().allowsItem("croptopia:salt_ore"));

        // Distinct natural materials are never collapsed together.
        for (String distinct : List.of(
                "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log",
                "minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate", "minecraft:cobbled_deepslate",
                "minecraft:sand", "minecraft:sandstone", "minecraft:glass", "minecraft:gravel",
                "minecraft:glowstone", "jaopca:storage_blocks.glowstone",
                "minecraft:white_wool", "minecraft:red_wool", "minecraft:blue_wool",
                "minecraft:red_dye", "minecraft:blue_dye", "minecraft:magenta_dye")) {
            assertTrue(unified.synthesisPolicy().allowsItem(distinct), "Distinct natural item must stay synthesizable: " + distinct);
        }

        // Compact & industrial synthesizer policy and Automation expose only the canonical copper dust.
        for (MachineKind kind : List.of(MachineKind.SYNTHESIZER, MachineKind.INDUSTRIAL_SYNTHESIZER)) {
            assertTrue(unified.synthesisPolicy().allowsMachine("alltheores:copper_dust", kind.id()), kind.id());
            assertFalse(unified.synthesisPolicy().allowsMachine("rematter:copper_dust", kind.id()), kind.id());
            assertFalse(unified.synthesisPolicy().allowsMachine("mekanism:dust_copper", kind.id()), kind.id());
        }

        var automationIds = AutomationRecipeCatalogue.chemistry(unified, id -> true, id -> true).stream()
                .map(r -> r.id()).collect(java.util.stream.Collectors.toSet());
        assertTrue(automationIds.contains("rematter:automation/synthesize_item/alltheores/copper_dust"));
        assertFalse(automationIds.contains("rematter:automation/synthesize_item/rematter/copper_dust"));
        assertTrue(automationIds.contains("rematter:automation/decompose_item/rematter/copper_dust"));

        // Wire payload fits comfortably within network limit.
        byte[] encoded = CatalogWireCodec.encode(unified);
        assertTrue(encoded.length < CatalogWireCodec.MAX_COMPRESSED_BYTES / 4,
                "Encoded catalogue grew unexpectedly: " + encoded.length + " bytes");
    }

    @Test
    void standaloneModWithoutExternalModsFallsBackCleanlyToReMatterPowders() {
        ChemistryRegistry vanillaOnly = ShippedChemistry.load(mod -> mod.equals("minecraft") || mod.equals("rematter"),
                true, ShippedChemistry.MAIN_RESOURCES);
        for (String dust : List.of("rematter:copper_dust", "rematter:iron_dust", "rematter:gold_dust",
                "rematter:ender_pearl_powder")) {
            assertTrue(vanillaOnly.synthesisPolicy().allowsItem(dust), "Standalone fallback must keep " + dust);
        }
        for (String workshopPowder : List.of("rematter:diamond_powder", "rematter:lapis_dust",
                "rematter:emerald_dust", "rematter:amethyst_dust")) {
            assertFalse(vanillaOnly.synthesisPolicy().allowsItem(workshopPowder), "Workshop powder stays recovery-only: " + workshopPowder);
        }
        assertTrue(vanillaOnly.synthesisPolicy().canonicalAliases().isEmpty());
    }

    @Test
    void disablingUnificationRestoresAllDuplicateOutputs() {
        var allowed = Map.of(
                "alltheores:copper_dust", SynthesisCategory.RAW_MATERIAL,
                "mekanism:dust_copper", SynthesisCategory.RAW_MATERIAL,
                "rematter:copper_dust", SynthesisCategory.RAW_MATERIAL);
        var reqs = Map.of(
                "alltheores:copper_dust", SynthesisRequirement.DEFAULT,
                "mekanism:dust_copper", SynthesisRequirement.DEFAULT,
                "rematter:copper_dust", SynthesisRequirement.DEFAULT);
        var groups = Map.of(
                "alltheores:copper_dust", "dust:copper",
                "mekanism:dust_copper", "dust:copper",
                "rematter:copper_dust", "dust:copper");

        var disabled = SynthesisUnifier.apply(allowed, reqs, groups, false, SynthesisConfig.DEFAULT_MOD_PRIORITY);
        assertEquals(allowed.keySet(), disabled.allowed().keySet());
        assertTrue(disabled.aliases().isEmpty());

        var customPriority = SynthesisUnifier.apply(allowed, reqs, groups, true, List.of("mekanism", "alltheores"));
        assertEquals(Set.of("mekanism:dust_copper"), customPriority.allowed().keySet());
        assertEquals("mekanism:dust_copper", customPriority.aliases().get("alltheores:copper_dust"));
        assertEquals("mekanism:dust_copper", customPriority.aliases().get("rematter:copper_dust"));
    }
}
