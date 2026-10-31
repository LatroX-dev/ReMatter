package dev.latrox.rematter.compat.jei;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.FluidConversions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

record ChemistryDisplay(Kind kind, String id, String itemInput, String itemOutput,
                        List<MatterIngredient> inputs, List<MatterIngredient> outputs,
                        long energy, int ticks, boolean synthesisAllowed,
                        FluidConversions.Definition fluidInput, FluidConversions.Definition fluidOutput) {
    enum Kind { DECOMPOSITION, SYNTHESIS, ELECTROLYSIS, REACTION }
    ChemistryDisplay {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
    ChemistryDisplay(Kind kind, String id, String itemInput, String itemOutput,
                     List<MatterIngredient> inputs, List<MatterIngredient> outputs,
                     long energy, int ticks, boolean synthesisAllowed) {
        this(kind, id, itemInput, itemOutput, inputs, outputs, energy, ticks, synthesisAllowed, null, null);
    }

    static List<ChemistryDisplay> from(ChemistryRegistry registry) {
        List<ChemistryDisplay> result = new ArrayList<>();
        for (var profile : registry.profiles()) {
            Identifier item = Identifier.parse(profile.itemId());
            if (!BuiltInRegistries.ITEM.containsKey(item) || item.toString().equals("minecraft:air")) continue;
            boolean allowed = registry.synthesisPolicy().allows(profile.itemId());
            List<MatterIngredient> matter = ingredients(profile.matter());
            result.add(new ChemistryDisplay(Kind.DECOMPOSITION, profile.itemId(), profile.itemId(), "",
                    List.of(), matter, profile.decompositionEnergy(), profile.ticks(), allowed));
            if (allowed) result.add(new ChemistryDisplay(Kind.SYNTHESIS, profile.itemId(), "", profile.itemId(),
                    matter, List.of(), profile.synthesisEnergy(), profile.ticks(), true));
        }
        for (var fluid : FluidConversions.ALL) {
            if (!registry.isKnown(fluid.substance())) continue;
            boolean allowed = registry.synthesisPolicy().allows(fluid.fluidId());
            List<MatterIngredient> matter = List.of(new MatterIngredient(fluid.substance(), fluid.amount()));
            result.add(new ChemistryDisplay(Kind.DECOMPOSITION, "fluid/" + fluid.fluidId(), "", "", List.of(), matter,
                    fluid.decompositionEnergy(), fluid.ticks(), allowed, fluid, null));
            if (allowed) result.add(new ChemistryDisplay(Kind.SYNTHESIS, "fluid/" + fluid.fluidId(), "", "", matter, List.of(),
                    fluid.synthesisEnergy(), fluid.ticks(), true, null, fluid));
        }
        for (var recipe : registry.processes()) {
            Kind kind = recipe.type() == ProcessType.ELECTROLYSIS ? Kind.ELECTROLYSIS : Kind.REACTION;
            result.add(new ChemistryDisplay(kind, recipe.id().toString(), "", "", ingredients(recipe.inputs()),
                    ingredients(recipe.outputs()), recipe.energy(), recipe.ticks(), false));
        }
        return List.copyOf(result);
    }

    static List<MatterIngredient> ingredients(Map<SubstanceKey, Long> matter) {
        return matter.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new MatterIngredient(entry.getKey(), entry.getValue())).toList();
    }
}
