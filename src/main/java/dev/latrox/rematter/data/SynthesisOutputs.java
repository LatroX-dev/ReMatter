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
            "bricks", "stairs", "slab", "wall", "button");
    private static final Set<String> PROCESSED_COMPONENTS = Set.of(
            "rematter:diamond_lattice", "rematter:ender_conductor", "rematter:netherite_sheath");

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
            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof EntityBlock) {
                reject(name, "machines and block entities cannot be synthesized");
            }
            if (Arrays.stream(id.getPath().split("[_/.-]")).anyMatch(MANUFACTURED_PARTS::contains)
                    || PROCESSED_COMPONENTS.contains(name)
                    || (id.getNamespace().equals("rematter") && id.getPath().endsWith("_matrix_crystal"))
                    || name.equals("minecraft:nether_star") || name.equals("rematter:matrix_cluster")
                    || name.equals("rematter:cell_housing")) {
                reject(name, "manufactured components and special loot are outside the synthesis catalogue");
            }
        });
    }

    private static void reject(String id, String reason) {
        throw new IllegalArgumentException("Forbidden synthesis output " + id + ": " + reason);
    }
}
