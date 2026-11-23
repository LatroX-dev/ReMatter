package dev.latrox.rematter.compat.jei;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;

/** Client-only discovery is performed by JEI; normal ReMatter startup never touches this class. */
@JeiPlugin
public final class ReMatterJeiPlugin implements IModPlugin {
    private static final Map<ChemistryViewer.Category, IRecipeType<ChemistryViewer.Entry>> TYPES = new EnumMap<>(ChemistryViewer.Category.class);
    private static final Map<MachineKind, IRecipeType<PreparationDisplay>> PREPARATION_TYPES = new EnumMap<>(MachineKind.class);
    private static final Map<MachineKind, IRecipeType<dev.latrox.rematter.compat.CrystalFuelDisplay>> CRYSTAL_FUEL_TYPES = new EnumMap<>(MachineKind.class);
    static {
        for (var category : ChemistryViewer.Category.values()) TYPES.put(category,
                IRecipeType.create("rematter", category.id(), ChemistryViewer.Entry.class));
        for (var machine : PreparationDisplay.MACHINES) PREPARATION_TYPES.put(machine,
                IRecipeType.create("rematter", "preparation/" + machine.id(), PreparationDisplay.class));
        for (var machine : dev.latrox.rematter.compat.CrystalFuelDisplay.MACHINES) CRYSTAL_FUEL_TYPES.put(machine,
                IRecipeType.create("rematter", "crystal_fuel/" + machine.id(), dev.latrox.rematter.compat.CrystalFuelDisplay.class));
    }
    private final Map<ChemistryViewer.Entry, ChemistryViewer.Entry> knownRecipes = new HashMap<>();
    private List<ChemistryViewer.Entry> visibleRecipes = List.of();
    private List<MatterIngredient> visibleIngredients = List.of();
    private final Map<PreparationDisplay, PreparationDisplay> knownPreparation = new HashMap<>();
    private List<PreparationDisplay> visiblePreparation = List.of();
    private final Map<dev.latrox.rematter.compat.CrystalFuelDisplay, dev.latrox.rematter.compat.CrystalFuelDisplay> knownCrystalFuels = new HashMap<>();
    private List<dev.latrox.rematter.compat.CrystalFuelDisplay> visibleCrystalFuels = List.of();
    private IJeiRuntime runtime;
    private final Consumer<ChemistryRegistry> catalogueListener = this::refresh;
    private final Consumer<List<PreparationRecipes.Entry>> preparationListener = this::refreshPreparation;
    private final Consumer<Map<String, dev.latrox.rematter.content.generation.CrystalFuelData.Fuel>> crystalFuelListener = this::refreshCrystalFuels;

    private static boolean enabled() { return !ModList.get().isLoaded("emi"); }
    @Override public Identifier getPluginUid() { return Identifier.fromNamespaceAndPath("rematter", "chemistry"); }
    static IRecipeType<ChemistryViewer.Entry> type(ChemistryViewer.Category category) { return TYPES.get(category); }
    static IRecipeType<PreparationDisplay> preparationType(MachineKind kind) { return PREPARATION_TYPES.get(kind); }
    static IRecipeType<dev.latrox.rematter.compat.CrystalFuelDisplay> crystalFuelType(MachineKind kind) { return CRYSTAL_FUEL_TYPES.get(kind); }
    static Block machine(MachineKind kind) { return ModContent.MACHINES.get(kind).get(); }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (!enabled()) return;
        registration.addGuiContainerHandler(MachineScreen.class, new IGuiContainerHandler<MachineScreen>() {
            @Override public List<Rect2i> getGuiExtraAreas(MachineScreen screen) {
                return screen.viewerExclusionAreas();
            }
        });
        registration.addGuiContainerHandler(
                (Class) net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,
                new IGuiContainerHandler<net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>>() {
                    @Override
                    public java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> getClickableIngredientUnderMouse(
                            mezz.jei.api.gui.builder.IClickableIngredientFactory builder,
                            net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
                            double mouseX,
                            double mouseY) {
                        var slot = screen.getSlotUnderMouse();
                        if (slot != null && slot.hasItem()
                                && dev.latrox.rematter.content.PressurizedCanisterData.read(slot.getItem())
                                        instanceof dev.latrox.rematter.content.PressurizedCanisterData.State.Matter matter) {
                            return builder.createBuilder(MatterIngredient.TYPE, new MatterIngredient(matter.key(), 1))
                                    .buildWithArea((int) mouseX - 8, (int) mouseY - 8, 16, 16);
                        }
                        return java.util.Optional.empty();
                    }
                });
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        if (!enabled()) return;
        visibleRecipes = ChemistryViewer.entries(ClientChemistry.current());
        visibleIngredients = ingredients(visibleRecipes);
        registration.register(MatterIngredient.TYPE, visibleIngredients, new MatterIngredient.Helper(), new MatterIngredient.Renderer(), MatterIngredient.CODEC);
    }
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!enabled()) return;
        for (var category : ChemistryViewer.Category.values()) registration.addRecipeCategories(new ChemistryCategory(category, registration.getJeiHelpers().getGuiHelper()));
        for (var machine : PreparationDisplay.MACHINES) registration.addRecipeCategories(new PreparationCategory(machine, registration.getJeiHelpers().getGuiHelper()));
        for (var machine : dev.latrox.rematter.compat.CrystalFuelDisplay.MACHINES) registration.addRecipeCategories(new CrystalFuelCategory(machine, registration.getJeiHelpers().getGuiHelper()));
    }
    @Override
    public void registerVanillaCategoryExtensions(mezz.jei.api.registration.IVanillaCategoryExtensionRegistration registration) {
        if (!enabled()) return;
        registration.getCraftingCategory().addExtension(
                dev.latrox.rematter.energy.EnergyUpgradeRecipe.class,
                new mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension<>() {
                    @Override
                    public List<net.minecraft.world.item.crafting.display.SlotDisplay> getIngredients(
                            net.minecraft.world.item.crafting.RecipeHolder<dev.latrox.rematter.energy.EnergyUpgradeRecipe> holder) {
                        return holder.value().pattern.ingredients().stream()
                                .map(ing -> ing.map(net.minecraft.world.item.crafting.Ingredient::display)
                                        .orElse(net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE))
                                .toList();
                    }
                    @Override public int getWidth(net.minecraft.world.item.crafting.RecipeHolder<dev.latrox.rematter.energy.EnergyUpgradeRecipe> holder) { return holder.value().getWidth(); }
                    @Override public int getHeight(net.minecraft.world.item.crafting.RecipeHolder<dev.latrox.rematter.energy.EnergyUpgradeRecipe> holder) { return holder.value().getHeight(); }
                    @Override
                    public void setRecipe(
                            net.minecraft.world.item.crafting.RecipeHolder<dev.latrox.rematter.energy.EnergyUpgradeRecipe> holder,
                            mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder,
                            mezz.jei.api.gui.ingredient.ICraftingGridHelper helper,
                            mezz.jei.api.recipe.IFocusGroup focuses) {
                        helper.createAndSetIngredientsFromDisplays(builder, getIngredients(holder), getWidth(holder), getHeight(holder));
                        helper.createAndSetOutputs(builder, new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(holder.value().resultTemplate()));
                    }
                });
    }
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!enabled()) return;
        visibleRecipes.forEach(recipe -> knownRecipes.put(recipe, recipe));
        for (var category : ChemistryViewer.Category.values()) registration.addRecipes(type(category), forCategory(visibleRecipes, category));
        visiblePreparation = PreparationDisplay.from(PreparationRecipes.clientRecipes());
        visiblePreparation.forEach(recipe -> knownPreparation.put(recipe, recipe));
        for (var machine : PreparationDisplay.MACHINES) registration.addRecipes(preparationType(machine), forMachine(visiblePreparation, machine));
        visibleCrystalFuels = dev.latrox.rematter.compat.CrystalFuelDisplay.from(dev.latrox.rematter.network.ClientCrystalFuels.current());
        visibleCrystalFuels.forEach(recipe -> knownCrystalFuels.put(recipe, recipe));
        for (var machine : dev.latrox.rematter.compat.CrystalFuelDisplay.MACHINES) registration.addRecipes(crystalFuelType(machine), forFuelMachine(visibleCrystalFuels, machine));
    }
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!enabled()) return;
        for (var category : ChemistryViewer.Category.values())
            for (var machine : category.machines()) registration.addCraftingStation(type(category), machine(machine));
        for (var machine : PreparationDisplay.MACHINES) registration.addCraftingStation(preparationType(machine), ModContent.MACHINES.get(machine).get());
        for (var machine : dev.latrox.rematter.compat.CrystalFuelDisplay.MACHINES) registration.addCraftingStation(crystalFuelType(machine), ModContent.MACHINES.get(machine).get());
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!enabled()) return;
        this.runtime = runtime;
        ClientChemistry.removeListener(catalogueListener);
        ClientChemistry.addListener(catalogueListener);
        PreparationRecipes.removeClientListener(preparationListener);
        PreparationRecipes.addClientListener(preparationListener);
        dev.latrox.rematter.network.ClientCrystalFuels.removeListener(crystalFuelListener);
        dev.latrox.rematter.network.ClientCrystalFuels.addListener(crystalFuelListener);
        refresh(ClientChemistry.current());
        refreshPreparation(PreparationRecipes.clientRecipes());
        refreshCrystalFuels(dev.latrox.rematter.network.ClientCrystalFuels.current());
    }
    @Override public void onRuntimeUnavailable() {
        ClientChemistry.removeListener(catalogueListener);
        PreparationRecipes.removeClientListener(preparationListener);
        dev.latrox.rematter.network.ClientCrystalFuels.removeListener(crystalFuelListener);
        runtime = null; knownRecipes.clear(); visibleRecipes = List.of(); visibleIngredients = List.of();
        knownPreparation.clear(); visiblePreparation = List.of();
        knownCrystalFuels.clear(); visibleCrystalFuels = List.of();
    }

    private void refreshCrystalFuels(Map<String, dev.latrox.rematter.content.generation.CrystalFuelData.Fuel> fuels) {
        if (runtime == null) return;
        var desired = dev.latrox.rematter.compat.CrystalFuelDisplay.from(fuels);
        var manager = runtime.getRecipeManager();
        for (var machine : dev.latrox.rematter.compat.CrystalFuelDisplay.MACHINES) {
            var old = forFuelMachine(visibleCrystalFuels, machine);
            var next = forFuelMachine(desired, machine);
            manager.hideRecipes(crystalFuelType(machine), old.stream().filter(value -> !next.contains(value)).toList());
            List<dev.latrox.rematter.compat.CrystalFuelDisplay> fresh = new ArrayList<>();
            List<dev.latrox.rematter.compat.CrystalFuelDisplay> reveal = new ArrayList<>();
            for (var recipe : next) {
                var existing = knownCrystalFuels.putIfAbsent(recipe, recipe);
                if (existing == null) fresh.add(recipe);
                else if (!old.contains(existing)) reveal.add(existing);
            }
            if (!fresh.isEmpty()) manager.addRecipes(crystalFuelType(machine), fresh);
            if (!reveal.isEmpty()) manager.unhideRecipes(crystalFuelType(machine), reveal);
        }
        visibleCrystalFuels = desired;
    }

    private static List<dev.latrox.rematter.compat.CrystalFuelDisplay> forFuelMachine(List<dev.latrox.rematter.compat.CrystalFuelDisplay> recipes, MachineKind machine) {
        return recipes.stream().filter(recipe -> recipe.machine() == machine).toList();
    }

    private void refreshPreparation(List<PreparationRecipes.Entry> entries) {
        if (runtime == null) return;
        var desired = PreparationDisplay.from(entries);
        var manager = runtime.getRecipeManager();
        for (var machine : PreparationDisplay.MACHINES) {
            var old = forMachine(visiblePreparation, machine);
            var next = forMachine(desired, machine);
            manager.hideRecipes(preparationType(machine), old.stream().filter(value -> !next.contains(value)).toList());
            List<PreparationDisplay> fresh = new ArrayList<>();
            List<PreparationDisplay> reveal = new ArrayList<>();
            for (var recipe : next) {
                var existing = knownPreparation.putIfAbsent(recipe, recipe);
                if (existing == null) fresh.add(recipe);
                else if (!old.contains(existing)) reveal.add(existing);
            }
            if (!fresh.isEmpty()) manager.addRecipes(preparationType(machine), fresh);
            if (!reveal.isEmpty()) manager.unhideRecipes(preparationType(machine), reveal);
        }
        visiblePreparation = desired;
    }

    private static List<PreparationDisplay> forMachine(List<PreparationDisplay> recipes, MachineKind machine) {
        return recipes.stream().filter(recipe -> recipe.machine() == machine).toList();
    }

    private void refresh(ChemistryRegistry registry) {
        if (runtime == null) return;
        List<ChemistryViewer.Entry> desired = ChemistryViewer.entries(registry);
        List<MatterIngredient> ingredients = ingredients(desired);
        var ingredientManager = runtime.getIngredientManager();
        var current = Set.copyOf(ingredients);
        var previous = Set.copyOf(visibleIngredients);
        var removed = visibleIngredients.stream().filter(value -> !current.contains(value)).toList();
        var added = ingredients.stream().filter(value -> !previous.contains(value)).toList();
        if (!removed.isEmpty()) ingredientManager.removeIngredientsAtRuntime(MatterIngredient.TYPE, removed);
        if (!added.isEmpty()) ingredientManager.addIngredientsAtRuntime(MatterIngredient.TYPE, added);
        if (!ingredients.isEmpty()) ingredientManager.refreshIngredientsAtRuntime(MatterIngredient.TYPE, ingredients);
        visibleIngredients = ingredients;

        var manager = runtime.getRecipeManager();
        for (var category : ChemistryViewer.Category.values()) {
            var old = Set.copyOf(forCategory(visibleRecipes, category));
            var next = forCategory(desired, category);
            var kept = Set.copyOf(next);
            manager.hideRecipes(type(category), old.stream().filter(value -> !kept.contains(value)).toList());
            List<ChemistryViewer.Entry> fresh = new ArrayList<>();
            List<ChemistryViewer.Entry> reveal = new ArrayList<>();
            for (var recipe : next) {
                var existing = knownRecipes.putIfAbsent(recipe, recipe);
                if (existing == null) fresh.add(recipe);
                else if (!old.contains(existing)) reveal.add(existing);
            }
            if (!fresh.isEmpty()) manager.addRecipes(type(category), fresh);
            if (!reveal.isEmpty()) manager.unhideRecipes(type(category), reveal);
        }
        visibleRecipes = desired;
    }

    private static List<ChemistryViewer.Entry> forCategory(List<ChemistryViewer.Entry> recipes, ChemistryViewer.Category category) {
        return recipes.stream().filter(recipe -> recipe.category() == category).toList();
    }
    private static List<MatterIngredient> ingredients(List<ChemistryViewer.Entry> recipes) {
        return ChemistryViewer.substances(recipes).stream().map(key -> new MatterIngredient(key, 1)).toList();
    }
}
