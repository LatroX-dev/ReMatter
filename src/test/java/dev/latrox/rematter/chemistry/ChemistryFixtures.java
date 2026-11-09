package dev.latrox.rematter.chemistry;

import java.util.Map;

/** Test conveniences over the chemistry core that game code does not need. */
public final class ChemistryFixtures {
    private ChemistryFixtures() {}

    /** A builder with every definition of the registry, for variations of a known registry. */
    public static ChemistryRegistry.Builder copyOf(ChemistryRegistry registry) {
        var builder = ChemistryRegistry.builder();
        registry.substances().forEach(builder::addSubstance);
        registry.profiles().forEach(builder::addProfile);
        registry.fluidProfiles().forEach(builder::addFluidProfile);
        registry.processes().forEach(builder::addProcess);
        registry.synthesisPolicy().entries().forEach((id, category) -> builder.allowSynthesis(id, category, registry.synthesisPolicy().requirement(id)));
        registry.environmentSources().forEach(builder::addEnvironmentSource);
        return builder;
    }

    /** Gives an inventory another capacity through the validated in-place restore, keeping its identity and contents. */
    public static void resize(MatterInventory inventory, long capacity) {
        inventory.restoreInPlace(new MatterInventory.Snapshot(capacity, inventory.snapshot().contents()));
    }

    /** The matter of a whole number of item batches; a partial batch is rejected instead of rounded. */
    public static Map<SubstanceKey, Long> matterFor(MaterialProfile profile, long count) {
        if (count <= 0 || count % profile.itemCount() != 0) throw new IllegalArgumentException("Partial material batch");
        return MatterAmounts.multiply(profile.matter(), count / profile.itemCount());
    }

    /** The matter of a whole number of fluid batches; a partial batch is rejected instead of rounded. */
    public static Map<SubstanceKey, Long> matterFor(FluidProfile profile, long millibuckets) {
        if (millibuckets <= 0 || millibuckets % profile.amount() != 0) throw new IllegalArgumentException("Partial fluid batch");
        return MatterAmounts.multiply(profile.matter(), millibuckets / profile.amount());
    }
}
