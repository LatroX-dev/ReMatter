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
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.MachineScreen;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.compat.PreparationDisplay;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
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
        EmiRecipeCategory decomposition = category("decomposition", "Decomposition", MachineKind.DECOMPOSER);
        EmiRecipeCategory synthesis = category("synthesis", "Synthesis", MachineKind.SYNTHESIZER);
        EmiRecipeCategory electrolysis = category("electrolysis", "Electrolysis", MachineKind.ELECTROLYZER);
        EmiRecipeCategory reaction = category("reaction", "Chemical reactions", MachineKind.REACTOR);
        for (var category : List.of(decomposition, synthesis, electrolysis, reaction)) {
            registry.addCategory(category);
            registry.addWorkstation(category, (EmiStack) category.icon);
        }
        registry.addWorkstation(reaction, EmiStack.of(ModContent.MACHINES.get(MachineKind.INDUSTRIAL_REACTOR).get()));
        catalogue.substances().forEach(definition -> definition.phases().forEach(phase ->
                registry.addEmiStack(new MatterEmiStack(new SubstanceKey(definition.id(), phase), 1))));

        for (var profile : catalogue.profiles()) {
            Identifier itemId = Identifier.parse(profile.itemId());
            if (!BuiltInRegistries.ITEM.containsKey(itemId) || itemId.toString().equals("minecraft:air")) continue;
            EmiStack item = EmiStack.of(new ItemStack(BuiltInRegistries.ITEM.getValue(itemId), profile.itemCount()));
            List<EmiStack> matter = matter(profile.matter());
            boolean allowed = catalogue.synthesisPolicy().allowsItem(profile.itemId());
            registry.addRecipe(new ChemistryEmiRecipe(decomposition, recipeId("decomposition", itemId), List.of(item), matter,
                    profile.decompositionEnergy(), profile.ticks(), allowed ? "Synthesis allowed" : "Synthesis not allowed"));
            if (allowed) registry.addRecipe(new ChemistryEmiRecipe(synthesis, recipeId("synthesis", itemId),
                    asIngredients(matter), List.of(item), profile.synthesisEnergy(), profile.ticks(), "Synthesis allowed"));
        }
        for (var conversion : catalogue.fluidProfiles()) {
            Identifier fluidId = Identifier.parse(conversion.fluidId());
            EmiStack fluid = EmiStack.of(BuiltInRegistries.FLUID.getValue(fluidId), conversion.amount());
            List<EmiStack> matter = matter(conversion.matter());
            boolean allowed = catalogue.synthesisPolicy().allowsFluid(conversion.fluidId());
            registry.addRecipe(new ChemistryEmiRecipe(decomposition, recipeId("fluid_decomposition", fluidId),
                    List.of(fluid), matter, conversion.decompositionEnergy(), conversion.ticks(),
                    allowed ? "Synthesis allowed" : "Synthesis not allowed"));
            if (allowed) registry.addRecipe(new ChemistryEmiRecipe(synthesis, recipeId("fluid_synthesis", fluidId),
                    asIngredients(matter), List.of(fluid), conversion.synthesisEnergy(), conversion.ticks(), "Synthesis allowed"));
        }
        for (var process : catalogue.processes()) {
            var category = process.type() == ProcessType.ELECTROLYSIS ? electrolysis : reaction;
            registry.addRecipe(new ChemistryEmiRecipe(category, recipeId("process", Identifier.parse(process.id().toString())),
                    asIngredients(matter(process.inputs())), matter(process.outputs()), process.energy(), process.ticks(), "Stored substances"));
        }
    }

    private static EmiRecipeCategory category(String id, String title, MachineKind machine) {
        return new EmiRecipeCategory(Identifier.fromNamespaceAndPath("rematter", id), EmiStack.of(ModContent.MACHINES.get(machine).get())) {
            @Override public Component getName() { return Component.literal(title); }
        };
    }
    private static Identifier recipeId(String type, Identifier source) {
        return Identifier.fromNamespaceAndPath("rematter", type + "/" + source.getNamespace() + "/" + source.getPath());
    }
    private static List<EmiStack> matter(Map<SubstanceKey, Long> amounts) {
        return amounts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> (EmiStack) new MatterEmiStack(entry.getKey(), entry.getValue())).toList();
    }
    private static List<EmiIngredient> asIngredients(List<EmiStack> matter) {
        return matter.stream().map(value -> (EmiIngredient) value).toList();
    }
}
