package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.MatterStore;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.preparation.PreparationInput;
import dev.latrox.rematter.content.preparation.PreparationRecipe;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.content.preparation.PreparationWork;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/** Builds the next batch from a machine's inputs or selection. Nothing here consumes or reserves resources. */
final class MachineOperations {
    static final int ANALYSIS_ENERGY = 500;
    static final int ANALYSIS_TICKS = 40;

    /** One complete batch: matter moved through storage plus a deferred physical commit. */
    record Operation(String signature, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                     long cost, int ticks, BooleanSupplier ready, Runnable finish, ItemStack visualResult) {
        Operation(String signature, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                  long cost, int ticks, BooleanSupplier ready, Runnable finish) {
            this(signature, inputs, outputs, cost, ticks, ready, finish, ItemStack.EMPTY);
        }
        boolean canFinish() { return ready.getAsBoolean(); }
    }

    private final MachineBlockEntity machine;
    private @Nullable PreparationRecipe lastPreparationRecipe;

    MachineOperations(MachineBlockEntity machine) { this.machine = machine; }

    static String itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    /** The single material-profile lookup for a sample or decomposition input. */
    static @Nullable MaterialProfile profile(ChemistryRegistry registry, ItemStack stack) {
        return stack.isEmpty() ? null : registry.profile(itemId(stack)).orElse(null);
    }

    void forgetPreparationRecipe() { lastPreparationRecipe = null; }
    boolean mixingPreparation() { return lastPreparationRecipe != null && !lastPreparationRecipe.singleInput(); }

    @Nullable Operation operation(ChemistryRegistry registry) {
        MachineKind kind = machine.kind();
        if (PreparationTier.isPreparation(kind)) return preparation();
        if (kind == MachineKind.ANALYZER || kind == MachineKind.DECOMPOSER) return sample(registry, kind);
        var choice = machine.selectedChoice();
        if (choice == null) return null;
        if (kind == MachineKind.SYNTHESIZER) return synthesis(registry, choice);
        var recipe = registry.recipe(SubstanceId.parse(choice.id())).orElse(null);
        if (recipe == null) return null;
        return new Operation(choice.id(), recipe.inputs(), recipe.outputs(), machine.cost(recipe.energy()), recipe.ticks(), () -> true, () -> {});
    }

    private @Nullable Operation preparation() {
        var holder = preparationRecipe(); if (holder == null) return null;
        var recipe = holder.value(); var result = recipe.result();
        if (recipe != lastPreparationRecipe) { machine.resetProgress(); lastPreparationRecipe = recipe; }
        return new Operation(holder.id().identifier().toString(), Map.of(), Map.of(), preparationCost(recipe), recipe.ticks(),
                () -> recipe.matches(preparationInput(), machine.getLevel()) && fitsOutput(result),
                () -> {
                    machine.stack(0).shrink(recipe.primaryCount());
                    if (recipe.secondary().isPresent()) machine.stack(3).shrink(recipe.secondaryCount());
                    if (recipe.tertiary().isPresent()) machine.stack(4).shrink(recipe.tertiaryCount());
                    putOutput(result);
                }, result);
    }

    private @Nullable Operation sample(ChemistryRegistry registry, MachineKind kind) {
        ItemStack input = machine.stack(0);
        var profile = input.getComponentsPatch().isEmpty() ? profile(registry, input) : null;
        if (profile != null) {
            boolean analyze = kind == MachineKind.ANALYZER;
            int requiredItems = analyze ? 1 : profile.itemCount();
            if (input.getCount() < requiredItems) return null;
            return new Operation("item:" + itemId(input) + ":" + requiredItems, Map.of(), analyze ? Map.of() : profile.matter(),
                    machine.cost(analyze ? ANALYSIS_ENERGY : profile.decompositionEnergy()), analyze ? ANALYSIS_TICKS : profile.ticks(),
                    () -> machine.stack(0).getCount() >= requiredItems && machine.stack(0).getItem() == input.getItem()
                            && machine.stack(0).getComponentsPatch().isEmpty(),
                    () -> { if (analyze) machine.completeAnalysis(profile); else machine.stack(0).shrink(requiredItems); },
                    analyze ? input : ItemStack.EMPTY);
        }
        if (kind != MachineKind.DECOMPOSER) return null;
        var fluids = machine.fluids();
        FluidResource fluid = fluids.getResource(0);
        var conversion = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
        if (conversion == null || !fluid.isComponentsPatchEmpty() || fluids.getAmountAsLong(0) < conversion.amount()) return null;
        return new Operation("fluid:" + conversion.fluidId(), Map.of(), conversion.matter(),
                machine.cost(conversion.decompositionEnergy()), conversion.ticks(),
                () -> fluids.getResource(0).equals(fluid) && fluids.getAmountAsLong(0) >= conversion.amount(),
                () -> fluids.set(0, fluid, (int) fluids.getAmountAsLong(0) - conversion.amount()));
    }

    private @Nullable Operation synthesis(ChemistryRegistry registry, MachineChoices.Choice choice) {
        var conversion = choice.id().startsWith("fluid:") ? registry.fluidProfile(choice.id().substring(6)).orElse(null) : null;
        if (conversion != null) {
            if (!registry.synthesisPolicy().allowsFluid(conversion.fluidId())) return null;
            var fluids = machine.fluids();
            var resource = FluidResource.of(BuiltInRegistries.FLUID.getValue(Identifier.parse(conversion.fluidId())));
            return new Operation(choice.id(), conversion.matter(), Map.of(),
                    machine.cost(conversion.synthesisEnergy()), conversion.ticks(),
                    () -> (fluids.getResource(0).isEmpty() || fluids.getResource(0).equals(resource))
                            && fluids.getAmountAsLong(0) <= MachineBlockEntity.TANK_CAPACITY - conversion.amount(),
                    () -> fluids.set(0, resource, (int) fluids.getAmountAsLong(0) + conversion.amount()));
        }
        if (!registry.synthesisPolicy().allowsItem(choice.id())) return null;
        var profile = registry.profile(choice.id()).orElse(null);
        Identifier outputId = Identifier.parse(choice.id());
        if (!BuiltInRegistries.ITEM.containsKey(outputId)) return null;
        Item output = BuiltInRegistries.ITEM.getValue(outputId);
        if (profile == null || output == null) return null;
        var result = new ItemStack(output, profile.itemCount());
        return new Operation(choice.id() + ":" + profile.itemCount(), profile.matter(), Map.of(), machine.cost(profile.synthesisEnergy()),
                profile.ticks(), () -> fitsOutput(result), () -> putOutput(result), result);
    }

    String readiness(boolean needsStorage, @Nullable MatterStore store, Operation operation) {
        if (!MachineEnergyPolicy.fitsBuffer(operation.cost())) return "energy_exceeds_buffer";
        long energy = machine.energy().getAmountAsLong();
        if (PreparationTier.isPreparation(machine.kind())) {
            ItemStack primary = machine.stack(0), secondary = machine.stack(3), tertiary = machine.stack(4);
            return PreparationWork.readiness(!primary.isEmpty(), !secondary.isEmpty(), mixingPreparation(),
                    primary.getComponentsPatch().isEmpty() && secondary.getComponentsPatch().isEmpty() && tertiary.getComponentsPatch().isEmpty(),
                    true, operation.canFinish(), energy, operation.cost());
        }
        return MachineReadiness.check(needsStorage, store, operation.inputs(), operation.outputs(), operation.canFinish(), energy, operation.cost());
    }

    String inputStatus(ChemistryRegistry registry) {
        MachineKind kind = machine.kind();
        if (PreparationTier.isPreparation(kind)) return preparationInputStatus(kind);
        if (machine.supportsBatchControl()) return "choose_process";
        ItemStack input = machine.stack(0);
        if (!input.isEmpty()) {
            if (!input.getComponentsPatch().isEmpty()) return "unsupported_components";
            var profile = profile(registry, input);
            if (profile == null) return "unsupported_input";
            if (kind == MachineKind.DECOMPOSER && input.getCount() < profile.itemCount()) return "input_batch";
        }
        var fluids = machine.fluids();
        if (kind == MachineKind.DECOMPOSER && !fluids.getResource(0).isEmpty()) {
            var fluid = fluids.getResource(0);
            if (!fluid.isComponentsPatchEmpty()) return "unsupported_components";
            var profile = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
            return profile == null ? "unsupported_input" : "input_batch";
        }
        return kind == MachineKind.ANALYZER ? "insert_sample" : "input";
    }

    private String preparationInputStatus(MachineKind kind) {
        ItemStack primary = machine.stack(0), secondary = machine.stack(3), tertiary = machine.stack(4);
        if (primary.isEmpty()) return "input";
        if (!primary.getComponentsPatch().isEmpty() || !secondary.getComponentsPatch().isEmpty() || !tertiary.getComponentsPatch().isEmpty())
            return "unsupported_components";
        if (!(machine.getLevel() instanceof ServerLevel server)) return "unsupported_input";
        var catalogue = PreparationRecipes.serverCatalogue(server);
        if (!catalogue.valid()) return "preparation_catalogue_invalid";
        var candidates = catalogue.entries().stream().filter(e -> e.recipe().acceptsPrimary(primary)).toList();
        if (candidates.isEmpty()) return "unsupported_input";
        var eligible = candidates.stream().filter(e -> PreparationTier.permits(kind, e.minimumTier(), e.process())).toList();
        if (eligible.isEmpty()) {
            String process = kind == MachineKind.SINTER_FURNACE ? PreparationRecipe.SINTERING : PreparationRecipe.PREPARATION;
            return candidates.stream().anyMatch(e -> e.process().equals(process)) ? "preparation_tier" : "preparation_process";
        }
        if (eligible.stream().allMatch(e -> e.recipe().secondary().isEmpty()) && !secondary.isEmpty()) return "secondary_slot_occupied";
        if (eligible.stream().allMatch(e -> e.recipe().tertiary().isEmpty()) && !tertiary.isEmpty()) return "tertiary_slot_occupied";
        if (secondary.isEmpty() && eligible.stream().allMatch(e -> e.recipe().secondary().isPresent())) return "secondary_input";
        if (tertiary.isEmpty() && eligible.stream().allMatch(e -> e.recipe().tertiary().isPresent())) return "tertiary_input";
        boolean rightTypes = eligible.stream().anyMatch(e -> (e.recipe().secondary().isEmpty() ? secondary.isEmpty() : e.recipe().acceptsSecondary(secondary))
                && (e.recipe().tertiary().isEmpty() ? tertiary.isEmpty() : e.recipe().acceptsTertiary(tertiary)));
        return rightTypes ? "input_batch" : "unsupported_input";
    }

    /** Recipe-side slot acceptance for inputs; slot roles are checked by the machine. */
    boolean acceptsInput(int index, ItemStack stack, ChemistryRegistry registry) {
        MachineKind kind = machine.kind();
        if (PreparationTier.isPreparation(kind)) {
            if (!(machine.getLevel() instanceof ServerLevel server) || (index != 0 && index != 3 && index != 4)) return false;
            return PreparationRecipes.serverCatalogue(server).entries().stream().filter(entry -> PreparationTier.permits(kind, entry.minimumTier(), entry.process()))
                    .anyMatch(entry -> index == 0 ? entry.recipe().acceptsPrimary(stack)
                            : index == 3 ? entry.recipe().acceptsSecondary(stack) : entry.recipe().acceptsTertiary(stack));
        }
        return (kind == MachineKind.DECOMPOSER || kind == MachineKind.ANALYZER) && stack.getComponentsPatch().isEmpty()
                && profile(registry, stack) != null;
    }

    @Nullable RecipeHolder<PreparationRecipe> preparationRecipe() {
        if (!(machine.getLevel() instanceof ServerLevel server) || !PreparationRecipes.serverCatalogue(server).valid()) return null;
        MachineKind kind = machine.kind();
        // Preserve the server's recipe priority, skipping recipes unavailable to this machine tier.
        return server.getServer().getRecipeManager().recipeMap()
                .getRecipesFor(PreparationRecipes.TYPE.get(), preparationInput(), server)
                .filter(holder -> PreparationTier.permits(kind, holder.value().minimumTier(), holder.value().process())).findFirst().orElse(null);
    }

    long preparationCost(PreparationRecipe recipe) {
        return machine.cost(MachineEnergyPolicy.scaled(recipe.energy(), PreparationTier.energyMultiplier(machine.kind())));
    }

    boolean hasValidAnalysis(ChemistryRegistry registry, AnalysisState analysis) {
        if (machine.kind() != MachineKind.ANALYZER) return false;
        ItemStack sample = machine.stack(0);
        if (sample.isEmpty()) return false;
        return analysis.matches(itemId(sample), sample.getComponentsPatch().isEmpty(), profile(registry, sample));
    }

    boolean analyzableSample(ChemistryRegistry registry) {
        ItemStack sample = machine.stack(0);
        return !sample.isEmpty() && sample.getComponentsPatch().isEmpty() && profile(registry, sample) != null;
    }

    private PreparationInput preparationInput() { return new PreparationInput(machine.stack(0), machine.stack(3), machine.stack(4)); }

    private boolean fitsOutput(ItemStack result) {
        ItemStack output = machine.stack(1);
        int maximum = Math.min(machine.getMaxStackSize(), result.getMaxStackSize());
        return result.getCount() <= maximum && (output.isEmpty()
                || (ItemStack.isSameItemSameComponents(output, result) && result.getCount() <= maximum - output.getCount()));
    }

    private void putOutput(ItemStack result) {
        if (machine.stack(1).isEmpty()) machine.putStack(1, result);
        else machine.stack(1).grow(result.getCount());
    }
}
