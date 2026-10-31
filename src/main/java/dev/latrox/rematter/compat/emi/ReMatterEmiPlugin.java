package dev.latrox.rematter.compat.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.FluidConversions;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Native SEMI/EMI integration; no JEI classes or recipe bridge are required. */
@EmiEntrypoint
public final class ReMatterEmiPlugin implements EmiPlugin {
    private static volatile ChemistryRegistry displayed;
    private static boolean reloadWarningLogged;
    static {
        ClientChemistry.addListener(catalogue -> {
            if (catalogue == displayed || Minecraft.getInstance().level == null) return;
            // SEMI 1.1.24 for 26.1.2 has no public API to rebuild recipes after custom data sync.
            // Its reload manager coalesces requests while a reload is already running.
            try {
                if (EmiReloadManager.getStatus() != 0) EmiReloadManager.reload();
            } catch (LinkageError | RuntimeException exception) {
                if (!reloadWarningLogged) {
                    reloadWarningLogged = true;
                    LogUtils.getLogger().warn("ReMatter cannot refresh this SEMI version after chemistry sync; reconnect to refresh recipes", exception);
                }
            }
        });
    }

    @Override public void initialize(EmiInitRegistry registry) {
        registry.addIngredientSerializer(MatterEmiStack.class, new MatterEmiStack.Serializer());
    }

    @Override public void register(EmiRegistry registry) {
        ChemistryRegistry catalogue = ClientChemistry.current();
        displayed = catalogue;
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
            EmiStack item = EmiStack.of(BuiltInRegistries.ITEM.getValue(itemId));
            List<EmiStack> matter = matter(profile.matter());
            boolean allowed = catalogue.synthesisPolicy().allows(profile.itemId());
            registry.addRecipe(new ChemistryEmiRecipe(decomposition, recipeId("decomposition", itemId), List.of(item), matter,
                    profile.decompositionEnergy(), profile.ticks(), allowed ? "Synthesis allowed" : "Synthesis not allowed"));
            if (allowed) registry.addRecipe(new ChemistryEmiRecipe(synthesis, recipeId("synthesis", itemId),
                    asIngredients(matter), List.of(item), profile.synthesisEnergy(), profile.ticks(), "Synthesis allowed"));
        }
        for (var conversion : FluidConversions.ALL) {
            if (!catalogue.isKnown(conversion.substance())) continue;
            Identifier fluidId = Identifier.parse(conversion.fluidId());
            EmiStack fluid = EmiStack.of(BuiltInRegistries.FLUID.getValue(fluidId), conversion.amount());
            EmiStack matter = new MatterEmiStack(conversion.substance(), conversion.amount());
            boolean allowed = catalogue.synthesisPolicy().allows(conversion.fluidId());
            registry.addRecipe(new ChemistryEmiRecipe(decomposition, recipeId("fluid_decomposition", fluidId),
                    List.of(fluid), List.of(matter), conversion.decompositionEnergy(), conversion.ticks(),
                    allowed ? "Synthesis allowed" : "Synthesis not allowed"));
            if (allowed) registry.addRecipe(new ChemistryEmiRecipe(synthesis, recipeId("fluid_synthesis", fluidId),
                    List.of(matter), List.of(fluid), conversion.synthesisEnergy(), conversion.ticks(), "Synthesis allowed"));
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
