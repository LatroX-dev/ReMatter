package dev.latrox.rematter.compat;

import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.generation.CrystalFuelData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Synchronized Crystal Generator fuel entries presented per generator tier in EMI and JEI. */
public record CrystalFuelDisplay(MachineKind machine, Identifier itemId, Item fuelItem, int count, long energy) {
    public static final List<MachineKind> MACHINES = List.of(
            MachineKind.CRYSTAL_GENERATOR,
            MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR);

    private static final Comparator<CrystalFuelData.Fuel> FUEL_ORDER = Comparator
            .comparingLong((CrystalFuelData.Fuel f) -> f.energy() / f.count())
            .thenComparing(CrystalFuelData.Fuel::itemId);

    public static List<CrystalFuelDisplay> from(Map<String, CrystalFuelData.Fuel> fuels) {
        var sorted = fuels.values().stream().sorted(FUEL_ORDER).toList();
        var result = new ArrayList<CrystalFuelDisplay>(sorted.size() * MACHINES.size());
        for (var machine : MACHINES) {
            for (var fuel : sorted) {
                Identifier id = Identifier.parse(fuel.itemId());
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item == Items.AIR) continue;
                result.add(new CrystalFuelDisplay(machine, id, item, fuel.count(), fuel.energy()));
            }
        }
        return List.copyOf(result);
    }

    public int rate() {
        return machine == MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR ? 512 : 128;
    }

    public int ticks() {
        return (int) ((energy + rate() - 1L) / rate());
    }

    public ItemStack input() {
        return new ItemStack(fuelItem, count);
    }

    public Identifier recipeId() {
        return Identifier.fromNamespaceAndPath("rematter",
                "/crystal_fuel/" + machine.id() + "/" + itemId.getNamespace() + "/" + itemId.getPath());
    }

    public Component yieldValue() {
        return Component.translatable("gui.rematter.crystal_fuel.yield", Quantities.energy(Quantities.number(energy)));
    }

    public Component rateTimeValue() {
        return Component.translatable("gui.rematter.crystal_fuel.rate_time",
                Quantities.number(rate()), Quantities.ticks(Quantities.number(ticks())));
    }

    public Component note() {
        return Component.translatable("gui.rematter.crystal_fuel.note");
    }
}
