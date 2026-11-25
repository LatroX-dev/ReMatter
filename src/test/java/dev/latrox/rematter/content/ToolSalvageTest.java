package dev.latrox.rematter.content;

import dev.latrox.rematter.automation.OrderLedger;
import dev.latrox.rematter.automation.OrderLedgerCodec;
import dev.latrox.rematter.celltransfer.CellTransferData;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.generation.CrystalBurner;
import dev.latrox.rematter.energy.EnergyCubeData;
import dev.latrox.rematter.energy.EnergyCubeTier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolSalvageTest {
    private static final SubstanceKey IRON = SubstanceKey.of("rematter:iron", Phase.SOLID);
    private static final SubstanceKey GOLD = SubstanceKey.of("rematter:gold", Phase.SOLID);
    private static final SubstanceKey COPPER = SubstanceKey.of("rematter:copper", Phase.SOLID);
    private static final SubstanceKey PIGMENT = SubstanceKey.of("rematter:pigment", Phase.SOLID);
    private static final SubstanceKey SILICA = SubstanceKey.of("rematter:silica", Phase.SOLID);
    private static final SubstanceKey CARBON = SubstanceKey.of("rematter:carbon", Phase.SOLID);
    private static final SubstanceKey CALCIUM_PHOSPHATE = SubstanceKey.of("rematter:calcium_phosphate", Phase.SOLID);

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
    void emptyPortableDeviceTagsPassWhileFilledChargedWorkingOrUnknownTagsAreRejected() {
        // MachineBlockEntity empty tag
        CompoundTag emptyMachine = emptyMachineTag(MachineKind.DECOMPOSER);
        assertTrue(ToolSalvage.isEmptyMachineTag(emptyMachine, MachineKind.DECOMPOSER));

        // Optional valid security mode passes; invalid security value rejected
        CompoundTag privateMachine = emptyMachine.copy();
        privateMachine.putString("security", "private");
        assertTrue(ToolSalvage.isEmptyMachineTag(privateMachine, MachineKind.DECOMPOSER));
        CompoundTag badSecurityMachine = emptyMachine.copy();
        badSecurityMachine.putString("security", "bogus");
        assertFalse(ToolSalvage.isEmptyMachineTag(badSecurityMachine, MachineKind.DECOMPOSER));

        // Non-zero FE rejected
        CompoundTag chargedMachine = emptyMachine.copy();
        chargedMachine.getCompound("power").orElseThrow().putInt("energy", 1);
        assertFalse(ToolSalvage.isEmptyMachineTag(chargedMachine, MachineKind.DECOMPOSER));

        // Non-empty item slot rejected
        CompoundTag slottedMachine = emptyMachine.copy();
        CompoundTag itemSlot = new CompoundTag();
        itemSlot.putString("id", "minecraft:iron_ingot");
        slottedMachine.getList("items").orElseThrow().set(0, itemSlot);
        assertFalse(ToolSalvage.isEmptyMachineTag(slottedMachine, MachineKind.DECOMPOSER));

        // Fluid in tank rejected
        CompoundTag fluidMachine = emptyMachine.copy();
        CompoundTag fluidSlot = new CompoundTag();
        fluidSlot.putString("id", "minecraft:water");
        fluidMachine.getCompound("tank").orElseThrow().getList("stacks").orElseThrow().set(0, fluidSlot);
        assertFalse(ToolSalvage.isEmptyMachineTag(fluidMachine, MachineKind.DECOMPOSER));

        // Active work, research session, or unknown future field rejected
        CompoundTag workingMachine = emptyMachine.copy();
        workingMachine.put("work", new CompoundTag());
        assertFalse(ToolSalvage.isEmptyMachineTag(workingMachine, MachineKind.DECOMPOSER));

        CompoundTag researchingAnalyzer = emptyMachineTag(MachineKind.ANALYZER);
        researchingAnalyzer.putString("research_session", UUID.randomUUID().toString());
        assertFalse(ToolSalvage.isEmptyMachineTag(researchingAnalyzer, MachineKind.ANALYZER));

        CompoundTag futureMachine = emptyMachine.copy();
        futureMachine.putString("future_field", "preserve_me");
        assertFalse(ToolSalvage.isEmptyMachineTag(futureMachine, MachineKind.DECOMPOSER));

        // Generator with empty burner passes; burner with remaining fuel fails
        CompoundTag emptyGen = emptyMachineTag(MachineKind.CRYSTAL_GENERATOR);
        CompoundTag burner = new CompoundTag();
        burner.putInt("schema", CrystalBurner.SCHEMA);
        burner.putLong("remaining", 0L);
        emptyGen.put("crystal_burner", burner);
        assertTrue(ToolSalvage.isEmptyMachineTag(emptyGen, MachineKind.CRYSTAL_GENERATOR));

        CompoundTag fueledGen = emptyGen.copy();
        fueledGen.getCompound("crystal_burner").orElseThrow().putLong("remaining", 10L);
        assertFalse(ToolSalvage.isEmptyMachineTag(fueledGen, MachineKind.CRYSTAL_GENERATOR));

        // Network interface with empty automation_host passes; non-empty adapter data rejected
        CompoundTag emptyInterface = emptyMachineTag(MachineKind.NETWORK_INTERFACE);
        CompoundTag host = new CompoundTag();
        host.putInt("schema", 1);
        host.putString("id", UUID.randomUUID().toString());
        host.putString("backend", "ae2");
        host.putString("ledger", OrderLedgerCodec.write(new OrderLedger()));
        host.put("adapters", new CompoundTag());
        emptyInterface.put("automation_host", host);
        assertTrue(ToolSalvage.isEmptyMachineTag(emptyInterface, MachineKind.NETWORK_INTERFACE));

        CompoundTag busyInterface = emptyInterface.copy();
        CompoundTag ae2Data = new CompoundTag();
        ae2Data.putInt("tasks", 1);
        busyInterface.getCompound("automation_host").orElseThrow().getCompound("adapters").orElseThrow().put("ae2", ae2Data);
        assertFalse(ToolSalvage.isEmptyMachineTag(busyInterface, MachineKind.NETWORK_INTERFACE));

        // Energy Cube empty vs charged vs slotted
        CompoundTag emptyCube = new CompoundTag();
        emptyCube.putInt("schema", 1);
        emptyCube.putInt("energy", 0);
        emptyCube.putInt("side_modes", 1);
        ListTag cubeItems = new ListTag();
        for (int i = 0; i < EnergyCubeTier.BASIC.chargingSlots(); i++) cubeItems.add(new CompoundTag());
        emptyCube.put("items", cubeItems);
        assertTrue(EnergyCubeData.isEmptyTag(emptyCube, EnergyCubeTier.BASIC));

        CompoundTag chargedCube = emptyCube.copy();
        chargedCube.putInt("energy", 500);
        assertFalse(EnergyCubeData.isEmptyTag(chargedCube, EnergyCubeTier.BASIC));

        CompoundTag unknownCube = emptyCube.copy();
        unknownCube.putInt("extra", 1);
        assertFalse(EnergyCubeData.isEmptyTag(unknownCube, EnergyCubeTier.BASIC));

        // Cell Transfer Station empty vs slotted
        CompoundTag emptyStation = new CompoundTag();
        emptyStation.putInt("schema", 2);
        emptyStation.putBoolean("all", true);
        emptyStation.putBoolean("primal_selected", false);
        emptyStation.put("selection", new ListTag());
        ListTag stationItems = new ListTag();
        stationItems.add(new CompoundTag());
        stationItems.add(new CompoundTag());
        emptyStation.put("items", stationItems);
        assertTrue(CellTransferData.isEmptyTag(emptyStation));

        CompoundTag slottedStation = emptyStation.copy();
        CompoundTag cellSlot = new CompoundTag();
        cellSlot.putString("id", "rematter:matter_cell_4m");
        slottedStation.getList("items").orElseThrow().set(0, cellSlot);
        assertFalse(CellTransferData.isEmptyTag(slottedStation));
    }

    private static CompoundTag emptyMachineTag(MachineKind kind) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", MachineSchema.VERSION);
        tag.putBoolean("automation", false);
        tag.putBoolean("network_jobs", false);
        tag.putBoolean("running", false);
        tag.putBoolean("repeat", false);
        tag.putInt("queued_batches", 0);
        tag.putString("selection", "");
        tag.putLong("target", 0L);
        tag.putString("target_substance", "");
        tag.putString("target_phase", "solid");
        tag.put("vented_gases", new ListTag());
        tag.putLong("vented_total", 0L);
        ListTag items = new ListTag();
        for (int i = 0; i < MachineCellAccess.inventorySize(kind); i++) items.add(new CompoundTag());
        tag.put("items", items);
        CompoundTag power = new CompoundTag();
        power.putInt("energy", 0);
        tag.put("power", power);
        CompoundTag tank = new CompoundTag();
        ListTag stacks = new ListTag();
        stacks.add(new CompoundTag());
        tank.put("stacks", stacks);
        tag.put("tank", tank);
        return tag;
    }

    @Test
    void allNonPrimalReMatterItemsAreDecomposableOnlyWhilePrimalAndSpecialItemsStayExcluded() throws Exception {
        ChemistryRegistry registry = ShippedChemistry.standalone();
        Set<String> excluded = new java.util.HashSet<>(ReservedSubstances.primalItems());
        excluded.add("rematter:recovered_matter");
        excluded.add("rematter:automation_pattern");

        Set<String> rawReMatterAllowed = Set.of(
                "rematter:rhevon_dust", "rematter:lumivex_dust", "rematter:virelite_blend");
        Set<String> unifiedDusts = Set.of(
                "rematter:iron_dust", "rematter:copper_dust", "rematter:gold_dust",
                "rematter:ender_pearl_powder");

        int profiledCount = 0;
        try (var files = Files.list(Path.of("src/main/resources/assets/rematter/items"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = "rematter:" + file.getFileName().toString().replace(".json", "");
                if (excluded.contains(id)) {
                    assertTrue(registry.profile(id).isEmpty(), "Excluded item must have no profile: " + id);
                    assertFalse(registry.synthesisPolicy().allowsItem(id), "Excluded item must not be synthesizable: " + id);
                    continue;
                }
                var profile = registry.profile(id).orElseThrow(() -> new AssertionError("Missing ReMatter profile: " + id));
                int expectedBatch = id.equals("rematter:machine_frame") ? 2 : 1;
                assertEquals(expectedBatch, profile.itemCount(), id);
                profiledCount++;
                if (rawReMatterAllowed.contains(id)) {
                    assertTrue(registry.synthesisPolicy().allowsItem(id), id);
                } else if (unifiedDusts.contains(id)) {
                    assertFalse(registry.synthesisPolicy().allowsItem(id), "Unified dust should defer to priority mod: " + id);
                    assertNotEquals(id, registry.synthesisPolicy().canonicalTarget(id), id);
                } else {
                    assertFalse(registry.synthesisPolicy().allowsItem(id), "Manufactured ReMatter item must stay recovery-only: " + id);
                }
            }
        }
        assertEquals(135, profiledCount, "All 15 original + 120 non-Primal ReMatter items");
    }

    @Test
    void modIntermediatesSemiFinishedGoodsAndVanillaNaturalCoveragePreserveExactConservation() {
        ChemistryRegistry registry = ShippedChemistry.standalone();

        // Oritech small dusts & JAOPCA tiny dusts: exact 9:1, recovery-only
        for (String small : List.of("oritech:small_iron_dust", "oritech:small_copper_dust", "oritech:small_gold_dust",
                "oritech:small_nickel_dust", "oritech:small_platinum_dust", "oritech:small_uranium_dust",
                "oritech:small_plutonium_dust", "jaopca:tiny_dusts.aluminum", "jaopca:tiny_dusts.silver")) {
            var p = registry.profile(small).orElseThrow(() -> new AssertionError("Missing " + small));
            assertEquals(9, p.itemCount(), small);
            assertFalse(registry.synthesisPolicy().allowsItem(small), small);
        }

        // Mekanism enriched carbon -> carbon; alloys & semi-finished goods strictly synthesis=False
        assertEquals(Map.of(CARBON, 1_000L), registry.profile("mekanism:enriched_carbon").orElseThrow().matter());
        assertFalse(registry.synthesisPolicy().allowsItem("mekanism:enriched_carbon"));
        assertFalse(registry.synthesisPolicy().allowsItem("mekanism:alloy_infused"));
        assertEquals(2, registry.profile("mekanism:yellow_cake_uranium").orElseThrow().itemCount());
        assertEquals(3, registry.profile("mekanism:dirty_netherite_scrap").orElseThrow().itemCount());
        assertEquals(4_000L, registry.profile("mekanism:block_salt").orElseThrow().matter().values().iterator().next());

        // Plates, Gears (iron/steel = 2 ingots, others = 4), Rods (2:1), Wires (4:1), Obsidian plate = 1 dust
        assertEquals(Map.of(IRON, 1_000L), registry.profile("alltheores:iron_plate").orElseThrow().matter());
        assertEquals(Map.of(IRON, 2_000L), registry.profile("alltheores:iron_gear").orElseThrow().matter());
        assertEquals(Map.of(COPPER, 4_000L), registry.profile("alltheores:copper_gear").orElseThrow().matter());
        assertEquals(2, registry.profile("alltheores:iron_rod").orElseThrow().itemCount());
        assertEquals(4, registry.profile("ftbic:copper_wire").orElseThrow().itemCount());
        assertEquals(registry.profile("mekanism:dust_obsidian").orElseThrow().matter(),
                registry.profile("ftbic:obsidian_plate").orElseThrow().matter());
        assertEquals(Map.of(COPPER, 8_000L), registry.profile("ftbic:dense_copper_plate").orElseThrow().matter());
        for (String part : List.of("alltheores:iron_plate", "alltheores:iron_gear", "alltheores:iron_rod",
                "ftbic:copper_wire", "ftbic:obsidian_plate", "advanced_ae:quantum_alloy_plate")) {
            assertFalse(registry.synthesisPolicy().allowsItem(part), part);
        }

        // AE2 Sky Stone Block 1:1 with Sky Dust & synthesizable; Vanilla Resin 1:1 with Amber
        assertEquals(registry.profile("ae2:sky_dust").orElseThrow().matter(),
                registry.profile("ae2:sky_stone_block").orElseThrow().matter());
        assertTrue(registry.synthesisPolicy().allowsItem("ae2:sky_stone_block"));
        assertEquals(registry.profile("thaumaturge:amber").orElseThrow().matter(),
                registry.profile("minecraft:resin_clump").orElseThrow().matter());
        assertTrue(registry.synthesisPolicy().allowsItem("minecraft:resin_clump"));

        // Dye Mekanism Combiner 4x/6x safety & uniform Painting Machine pigment across colors
        assertEquals(400L, registry.profile("minecraft:red_dye").orElseThrow().matter().get(PIGMENT));
        assertEquals(200L, registry.profile("minecraft:orange_dye").orElseThrow().matter().get(PIGMENT));
        assertEquals(150L, registry.profile("minecraft:light_gray_dye").orElseThrow().matter().get(PIGMENT));
        assertEquals(100L, registry.profile("minecraft:magenta_dye").orElseThrow().matter().get(PIGMENT));
        assertEquals(100L, registry.profile("minecraft:red_wool").orElseThrow().matter().get(PIGMENT));
        assertEquals(100L, registry.profile("minecraft:magenta_wool").orElseThrow().matter().get(PIGMENT));
        assertEquals(12L, registry.profile("minecraft:white_terracotta").orElseThrow().matter().get(PIGMENT));
        assertEquals(12L, registry.profile("minecraft:magenta_terracotta").orElseThrow().matter().get(PIGMENT));
        assertEquals(12L, registry.profile("minecraft:blue_concrete_powder").orElseThrow().matter().get(PIGMENT));
        assertEquals(12L, registry.profile("minecraft:red_stained_glass").orElseThrow().matter().get(PIGMENT));

        // Bone, Gravel/Flint, Gunpowder, Blaze conservation
        assertEquals(250L, registry.profile("minecraft:bone_meal").orElseThrow().matter().get(CALCIUM_PHOSPHATE));
        assertEquals(2_000L, registry.profile("minecraft:bone").orElseThrow().matter().get(CALCIUM_PHOSPHATE));
        assertEquals(2_250L, registry.profile("minecraft:bone_block").orElseThrow().matter().get(CALCIUM_PHOSPHATE));
        assertEquals(Map.of(SILICA, 1_000L), registry.profile("minecraft:gravel").orElseThrow().matter());
        assertEquals(Map.of(SILICA, 500L), registry.profile("minecraft:flint").orElseThrow().matter());
        assertTrue(registry.synthesisPolicy().allowsItem("minecraft:gunpowder"));
        assertTrue(registry.synthesisPolicy().allowsItem("minecraft:blaze_powder"));
        assertFalse(registry.synthesisPolicy().allowsItem("minecraft:blaze_rod"));
        assertEquals(4_000L / 4, registry.profile("minecraft:blaze_rod").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum() / 2);
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
