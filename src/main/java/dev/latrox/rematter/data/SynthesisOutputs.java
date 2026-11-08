package dev.latrox.rematter.data;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SynthesisCategory;
import dev.latrox.rematter.content.MatterCellItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

/** Additional checks against real item types, beyond the explicit chemistry allowlist. */
final class SynthesisOutputs {
    private static final Set<String> MANUFACTURED_PARTS = Set.of(
            "plate", "plates", "wire", "wires", "gear", "gears", "circuit", "circuits",
            "processor", "processors", "machine", "machines", "frame", "frames",
            "sword", "swords", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate",
            "leggings", "boots", "armor", "armour", "motor", "rotor", "turbine",
            "rod", "rods", "sheet", "sheets", "foil", "foils", "bolt", "bolts", "screw", "screws",
            "coil", "coils", "cable", "cables", "pipe", "pipes", "casing", "casings", "upgrade", "upgrades",
            "chip", "chips", "spring", "springs", "lens", "lenses", "bars", "ceramic", "ceramics",
            "bricks", "stairs", "slab", "wall", "button", "polished", "smooth", "chiseled", "cut", "pillar",
            "tiles", "pane", "panes", "fence", "door", "trapdoor");
    private static final Set<String> PROCESSED_COMPONENTS = Set.of(
            "rematter:diamond_lattice", "rematter:ender_conductor", "rematter:netherite_sheath",
            "rematter:matrix_blend", "rematter:diamond_matrix_blend", "rematter:ender_conductor_blend",
            "rematter:netherite_sheath_blend", "rematter:matrix_cluster", "rematter:cell_housing", "rematter:reactor_glass",
            "rematter:machine_port", "rematter:silith", "rematter:argil", "rematter:rhedon", "rematter:virel", "rematter:lumin",
            "rematter:rhevon_ingot", "rematter:lumivex_ingot", "rematter:automation_pattern", "rematter:recovered_matter",
            "rematter:guide");
    private static final Set<String> SPECIAL_LOOT = Set.of(
            "minecraft:nether_star", "minecraft:totem_of_undying", "minecraft:heart_of_the_sea", "minecraft:dragon_egg",
            "minecraft:enchanted_golden_apple", "minecraft:echo_shard");

    private SynthesisOutputs() {}

    static void validate(ChemistryRegistry registry, Function<Item, DataComponentMap> itemDefaults) {
        registry.profiles().forEach(profile -> {
            Identifier id = Identifier.parse(profile.itemId());
            if (!BuiltInRegistries.ITEM.containsKey(id) || profile.itemId().equals("minecraft:air")) {
                reject(profile.itemId(), "material profile needs a registered item");
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            var stack = new ItemStack(Holder.direct(item, itemDefaults.apply(item)), 1);
            if (profile.itemCount() > stack.getMaxStackSize()) {
                reject(profile.itemId(), "material batch exceeds the actual item stack limit");
            }
        });
        registry.fluidProfiles().forEach(profile -> {
            Identifier id = Identifier.parse(profile.fluidId());
            if (!BuiltInRegistries.FLUID.containsKey(id) || profile.fluidId().equals("minecraft:empty")) {
                reject(profile.fluidId(), "fluid profile needs a registered fluid");
            }
            var fluid = BuiltInRegistries.FLUID.getValue(id);
            if (fluid instanceof FlowingFluid flowing && flowing.getSource() != fluid) {
                reject(profile.fluidId(), "use the canonical source fluid, not its flowing variant");
            }
        });
        registry.synthesisPolicy().entries().forEach((name, category) -> {
            Identifier id = Identifier.parse(name);
            if (category == SynthesisCategory.GAS) {
                if (registry.definition(SubstanceId.parse(name)).filter(s -> s.supports(Phase.GAS)).isEmpty()) {
                    reject(name, "gas permission needs a known gaseous substance");
                }
                return;
            }
            if (category == SynthesisCategory.FLUID) {
                if (!BuiltInRegistries.FLUID.containsKey(id) || name.equals("minecraft:empty")) {
                    reject(name, "fluid permission needs a registered fluid");
                }
                return;
            }
            if (!BuiltInRegistries.ITEM.containsKey(id) || name.equals("minecraft:air")) {
                reject(name, "unknown item");
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item instanceof MatterCellItem) reject(name, "storage cells are manufactured containers, not raw materials");
            var components = itemDefaults.apply(item);
            if (components.has(DataComponents.TOOL) || components.has(DataComponents.WEAPON)
                    || components.has(DataComponents.MAX_DAMAGE) || components.has(DataComponents.EQUIPPABLE)
                    || components.has(DataComponents.CONTAINER) || components.has(DataComponents.BUNDLE_CONTENTS)
                    || components.has(DataComponents.ENTITY_DATA) || components.has(DataComponents.BLOCK_ENTITY_DATA)
                    || components.has(DataComponents.PIERCING_WEAPON) || components.has(DataComponents.KINETIC_WEAPON)) {
                reject(name, "equipment and containers cannot be synthesized");
            }
            if (components.has(DataComponents.DEATH_PROTECTION) || components.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
                    || !components.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                    || !components.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) {
                reject(name, "enchanted and protective items are artifacts, not raw materials");
            }
            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof EntityBlock) {
                reject(name, "machines and block entities cannot be synthesized");
            }
            if (forbiddenName(name)) {
                reject(name, "manufactured components and special loot are outside the synthesis catalogue");
            }
        });
    }

    /** Name rules for item outputs that hold independently of the loaded game, so shipped data can be checked offline. */
    static boolean forbiddenName(String name) {
        Identifier id = Identifier.parse(name);
        return Arrays.stream(id.getPath().split("[_/.-]")).anyMatch(MANUFACTURED_PARTS::contains)
                || PROCESSED_COMPONENTS.contains(name) || SPECIAL_LOOT.contains(name)
                || id.getNamespace().equals("rematter") && id.getPath().endsWith("_matrix_crystal");
    }

    private static void reject(String id, String reason) {
        throw new IllegalArgumentException("Forbidden synthesis output " + id + ": " + reason);
    }
}
