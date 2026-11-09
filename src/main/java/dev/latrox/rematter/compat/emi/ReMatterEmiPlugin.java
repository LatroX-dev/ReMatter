package dev.latrox.rematter.compat.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.client.MachineScreen;
import dev.latrox.rematter.compat.ChemistryViewer;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.compat.PreparationDisplay;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Native SEMI/EMI integration; no JEI classes or recipe bridge are required. */
@EmiEntrypoint
public final class ReMatterEmiPlugin implements EmiPlugin {
    private static volatile ChemistryRegistry displayed;
    private static volatile List<PreparationRecipes.Entry> displayedPreparation = List.of();
    private static boolean reloadWarningLogged;
    static {
        ClientChemistry.addListener(catalogue -> {
            if (catalogue != displayed) refreshRecipes();
        });
        PreparationRecipes.addClientListener(catalogue -> {
            if (catalogue != displayedPreparation) refreshRecipes();
        });
    }

    private static void refreshRecipes() {
        if (Minecraft.getInstance().level == null) return;
        // SEMI 1.1.24 for 26.1.2 has no public API to rebuild after an extra native recipe sync.
        // The installed reload manager coalesces requests while a reload is already running.
        try {
            if (EmiReloadManager.getStatus() != 0) EmiReloadManager.reload();
        } catch (LinkageError | RuntimeException exception) {
            if (!reloadWarningLogged) {
                reloadWarningLogged = true;
                LogUtils.getLogger().warn("ReMatter cannot refresh this SEMI version after recipe sync; reconnect to refresh recipes", exception);
            }
        }
    }

    @Override public void initialize(EmiInitRegistry registry) {
        registry.addIngredientSerializer(MatterEmiStack.class, new MatterEmiStack.Serializer());
    }

    @Override public void register(EmiRegistry registry) {
        ModContent.MACHINES.forEach((kind, block) -> { if (kind.retiredSpecialist()) registry.removeEmiStacks(EmiStack.of(block.get())); });
        registry.addExclusionArea(MachineScreen.class, (screen, consumer) -> {
            for (var area : screen.viewerExclusionAreas())
                consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        });
        ChemistryRegistry catalogue = ClientChemistry.current();
        displayed = catalogue;
        displayedPreparation = PreparationRecipes.clientRecipes();
        var preparationCategories = new EnumMap<MachineKind, EmiRecipeCategory>(MachineKind.class);
        for (var machine : PreparationDisplay.MACHINES) {
            var preparation = new EmiRecipeCategory(Identifier.fromNamespaceAndPath("rematter", "preparation/" + machine.id()),
                    EmiStack.of(ModContent.MACHINES.get(machine).get())) {
                @Override public Component getName() { return Component.translatable("block.rematter." + machine.id()); }
            };
            preparationCategories.put(machine, preparation);
            registry.addCategory(preparation);
            registry.addWorkstation(preparation, (EmiStack) preparation.icon);
        }
        for (var recipe : PreparationDisplay.from(displayedPreparation))
            registry.addRecipe(new PreparationEmiRecipe(preparationCategories.get(recipe.machine()), recipe));
        var categories = new EnumMap<ChemistryViewer.Category, EmiRecipeCategory>(ChemistryViewer.Category.class);
        for (var value : ChemistryViewer.Category.values()) {
            var machines = value.machines();
            var category = new EmiRecipeCategory(Identifier.fromNamespaceAndPath("rematter", value.id()),
                    EmiStack.of(ModContent.MACHINES.get(machines.getFirst()).get())) {
                @Override public Component getName() { return value.title(); }
            };
            categories.put(value, category);
            registry.addCategory(category);
            for (var machine : machines) registry.addWorkstation(category, EmiStack.of(ModContent.MACHINES.get(machine).get()));
        }
        var entries = ChemistryViewer.entries(catalogue);
        ChemistryViewer.substances(entries).forEach(key -> registry.addEmiStack(new MatterEmiStack(key, 1)));
        for (var entry : entries) {
            var inputs = new ArrayList<EmiIngredient>();
            entry.consumed().ifPresent(resource -> inputs.add(stack(resource)));
            entry.inputs().forEach(matter -> inputs.add(new MatterEmiStack(matter.key(), matter.amount())));
            var outputs = new ArrayList<EmiStack>();
            entry.produced().ifPresent(resource -> outputs.add(stack(resource)));
            entry.outputs().forEach(matter -> outputs.add(new MatterEmiStack(matter.key(), matter.amount())));
            registry.addRecipe(new ChemistryEmiRecipe(categories.get(entry.category()), entry.id(), inputs, outputs,
                    entry.energy(), entry.ticks(), entry.notes()));
        }
    }

    private static EmiStack stack(ChemistryViewer.Resource resource) {
        return resource.fluid() ? EmiStack.of(BuiltInRegistries.FLUID.getValue(resource.id()), resource.amount())
                : EmiStack.of(new ItemStack(BuiltInRegistries.ITEM.getValue(resource.id()), resource.amount()));
    }
}
