package dev.latrox.rematter.chemistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable, fully validated definitions. A reload can replace this registry atomically. */
public final class ChemistryRegistry {
    public static final int MAX_SUBSTANCES = 8_192;
    public static final int MAX_MATERIAL_PROFILES = 32_768;
    public static final int MAX_FLUID_PROFILES = 4_096;
    public static final int MAX_PROCESSES = 8_192;
    public static final int MAX_SYNTHESIS_ENTRIES = 65_536;
    private final Map<SubstanceId, SubstanceDefinition> substances;
    private final Map<String, MaterialProfile> profiles;
    private final Map<String, FluidProfile> fluidProfiles;
    private final Map<SubstanceId, ProcessRecipe> processes;
    private final Map<SubstanceId, EnvironmentSource> environmentSources;
    private final SynthesisPolicy synthesisPolicy;

    private ChemistryRegistry(Builder builder) {
        substances = Collections.unmodifiableMap(new TreeMap<>(builder.substances));
        profiles = Collections.unmodifiableMap(new TreeMap<>(builder.profiles));
        fluidProfiles = Collections.unmodifiableMap(new TreeMap<>(builder.fluidProfiles));
        var liveProcesses = new TreeMap<>(builder.processes);
        processes = Collections.unmodifiableMap(liveProcesses);
        environmentSources = Collections.unmodifiableMap(new TreeMap<>(builder.environmentSources));
        synthesisPolicy = new SynthesisPolicy(builder.allowedSynthesis, builder.synthesisRequirements);
        validate();
        // Dependency-filtered profiles and permissions describe the items actually available to this reload.
        boolean changed;
        do {
            changed=liveProcesses.values().removeIf(recipe->recipe.minField()>0&&!usefulProduct(recipe,liveProcesses));
        } while(changed);
    }

    private boolean usefulProduct(ProcessRecipe recipe,Map<SubstanceId,ProcessRecipe> candidates) {
        var product=recipe.product();
        for(var entry:synthesisPolicy.entries().entrySet()){
            var profile=profiles.get(entry.getKey());
            if(profile!=null&&profile.matter().keySet().stream().anyMatch(key->key.id().equals(product)))return true;
            var fluid=fluidProfiles.get(entry.getKey());
            if(fluid!=null&&fluid.matter().keySet().stream().anyMatch(key->key.id().equals(product)))return true;
            if(entry.getValue()==SynthesisCategory.GAS&&entry.getKey().equals(product.toString()))return true;
        }
        return candidates.values().stream().anyMatch(other->!other.id().equals(recipe.id())
                &&other.inputs().keySet().stream().anyMatch(key->key.id().equals(product)));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<SubstanceDefinition> definition(SubstanceId id) {
        return Optional.ofNullable(substances.get(id));
    }

    public Optional<MaterialProfile> profile(String itemId) {
        return Optional.ofNullable(profiles.get(itemId));
    }

    public Optional<FluidProfile> fluidProfile(String fluidId) {
        return Optional.ofNullable(fluidProfiles.get(fluidId));
    }

    public Collection<FluidProfile> fluidProfiles() {
        return fluidProfiles.values();
    }

    public Optional<ProcessRecipe> recipe(SubstanceId id) {
        return Optional.ofNullable(processes.get(id));
    }

    public Collection<SubstanceDefinition> substances() {
        return substances.values();
    }

    public Collection<MaterialProfile> profiles() {
        return profiles.values();
    }

    public Collection<ProcessRecipe> processes() {
        return processes.values();
    }

    public SynthesisPolicy synthesisPolicy() {
        return synthesisPolicy;
    }

    public Collection<EnvironmentSource> environmentSources() { return environmentSources.values(); }
    public Optional<EnvironmentSource> environmentSource(SubstanceId id) { return Optional.ofNullable(environmentSources.get(id)); }

    public boolean isKnown(SubstanceKey key) {
        SubstanceDefinition definition = substances.get(key.id());
        return definition != null && definition.supports(key.phase());
    }

    public Map<SubstanceId, Long> elementAmounts(Map<SubstanceKey, Long> amounts) {
        TreeMap<SubstanceId, Long> result = new TreeMap<>();
        MatterAmounts.copy(amounts).forEach((key, amount) -> {
            SubstanceDefinition substance = requireSubstance(key);
            substance.composition().multiply(amount).forEach((element, quantity) ->
                    result.merge(element, quantity, Math::addExact));
        });
        return Collections.unmodifiableMap(result);
    }

    private void validate() {
        if (substances.size() > MAX_SUBSTANCES || profiles.size() > MAX_MATERIAL_PROFILES
                || fluidProfiles.size() > MAX_FLUID_PROFILES || processes.size() > MAX_PROCESSES
                || synthesisPolicy.entries().size() > MAX_SYNTHESIS_ENTRIES) {
            throw new IllegalArgumentException("Chemistry catalogue exceeds its supported entry limits");
        }
        for (SubstanceDefinition substance : substances.values()) {
            if (ReservedSubstances.reserved(substance.id())) {
                if (!substance.equals(ReservedSubstances.primalDefinition())) throw new IllegalArgumentException("Reserved Primal resource cannot be redefined");
            } else if (substance.composition().elements().containsKey(ReservedSubstances.PRIMAL_MATTER)) {
                throw new IllegalArgumentException("Ordinary substances cannot contain Primal Matter: " + substance.id());
            }
            for (SubstanceId element : substance.composition().elements().keySet()) {
                SubstanceDefinition definition = substances.get(element);
                if (definition == null || definition.kind() != SubstanceKind.ELEMENT) {
                    throw new IllegalArgumentException("Unknown element " + element + " in " + substance.id());
                }
            }
        }
        for (MaterialProfile profile : profiles.values()) {
            if (ReservedSubstances.primalItem(profile.itemId())) throw new IllegalArgumentException("Primal items have no ordinary material profile: " + profile.itemId());
            elementAmounts(profile.matter());
        }
        for (FluidProfile profile : fluidProfiles.values()) {
            elementAmounts(profile.matter());
        }
        for (Map.Entry<String, SynthesisCategory> allowed : synthesisPolicy.entries().entrySet()) {
            if (ReservedSubstances.primalItem(allowed.getKey()) || allowed.getKey().equals(ReservedSubstances.PRIMAL_MATTER.toString()))
                throw new IllegalArgumentException("Primal resources cannot be synthesized: " + allowed.getKey());
            if ((allowed.getValue() == SynthesisCategory.RAW_MATERIAL
                    || allowed.getValue() == SynthesisCategory.NATURAL_MATERIAL)
                    && !profiles.containsKey(allowed.getKey())) {
                throw new IllegalArgumentException("Synthesis requires an explicit material profile: " + allowed.getKey());
            }
            if (allowed.getValue() == SynthesisCategory.FLUID && !fluidProfiles.containsKey(allowed.getKey())) {
                throw new IllegalArgumentException("Fluid synthesis requires an explicit fluid profile: " + allowed.getKey());
            }
            if (allowed.getValue() == SynthesisCategory.GAS
                    && !isKnown(new SubstanceKey(SubstanceId.parse(allowed.getKey()), Phase.GAS))) {
                throw new IllegalArgumentException("Gas synthesis requires a known gaseous substance: " + allowed.getKey());
            }
        }
        for (ProcessRecipe recipe : processes.values()) {
            long inputUnits=recipe.inputs().values().stream().reduce(0L,Math::addExact);
            long outputUnits=recipe.outputs().values().stream().reduce(0L,Math::addExact);
            if(java.math.BigInteger.valueOf(outputUnits).compareTo(java.math.BigInteger.valueOf(inputUnits).multiply(java.math.BigInteger.valueOf(8)))>0)
                throw new IllegalArgumentException("Process exceeds the eightfold unit bound: "+recipe.id());
            Map<SubstanceId, Long> inputElements = elementAmounts(recipe.inputs());
            Map<SubstanceId, Long> outputElements = elementAmounts(recipe.outputs());
            if (!inputElements.equals(outputElements)) {
                throw new IllegalArgumentException("Unbalanced process " + recipe.id()
                        + ": " + inputElements + " -> " + outputElements);
            }
        }
        if (environmentSources.size() > MAX_PROCESSES) throw new IllegalArgumentException("Too many environment sources");
        for (EnvironmentSource source : environmentSources.values()) elementAmounts(source.outputs());
    }

    private SubstanceDefinition requireSubstance(SubstanceKey key) {
        if (ReservedSubstances.reserved(key)) throw new IllegalArgumentException("Primal Matter is excluded from ordinary chemistry");
        SubstanceDefinition substance = substances.get(key.id());
        if (substance == null || !substance.supports(key.phase())) {
            throw new IllegalArgumentException("Unknown substance or unsupported phase: " + key);
        }
        return substance;
    }

    public static final class Builder {
        private final Map<SubstanceId, SubstanceDefinition> substances = new TreeMap<>();
        private final Map<String, MaterialProfile> profiles = new TreeMap<>();
        private final Map<String, FluidProfile> fluidProfiles = new TreeMap<>();
        private final Map<SubstanceId, ProcessRecipe> processes = new TreeMap<>();
        private final Map<String, SynthesisCategory> allowedSynthesis = new TreeMap<>();
        private final Map<String, SynthesisRequirement> synthesisRequirements = new TreeMap<>();
        private final Map<SubstanceId, EnvironmentSource> environmentSources = new TreeMap<>();

        public Builder addSubstance(SubstanceDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            if (substances.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate substance: " + definition.id());
            }
            return this;
        }

        public Builder addProfile(MaterialProfile profile) {
            Objects.requireNonNull(profile, "profile");
            if (profiles.putIfAbsent(profile.itemId(), profile) != null) {
                throw new IllegalArgumentException("Duplicate material profile: " + profile.itemId());
            }
            return this;
        }

        public Builder addFluidProfile(FluidProfile profile) {
            Objects.requireNonNull(profile, "profile");
            if (fluidProfiles.putIfAbsent(profile.fluidId(), profile) != null) {
                throw new IllegalArgumentException("Duplicate fluid profile: " + profile.fluidId());
            }
            return this;
        }

        public Builder addProcess(ProcessRecipe recipe) {
            Objects.requireNonNull(recipe, "recipe");
            if (processes.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalArgumentException("Duplicate process: " + recipe.id());
            }
            return this;
        }

        public Builder allowSynthesis(String exactId, SynthesisCategory category) {
            return allowSynthesis(exactId, category, SynthesisRequirement.DEFAULT);
        }

        public Builder allowSynthesis(String exactId, SynthesisCategory category, SynthesisRequirement requirement) {
            SubstanceId.parse(exactId);
            Objects.requireNonNull(category, "category");
            if (!category.permitted()) {
                throw new IllegalArgumentException("Forbidden synthesis category: " + category);
            }
            if (allowedSynthesis.putIfAbsent(exactId, category) != null) {
                throw new IllegalArgumentException("Duplicate synthesis entry: " + exactId);
            }
            synthesisRequirements.put(exactId, Objects.requireNonNull(requirement));
            return this;
        }

        public Builder addEnvironmentSource(EnvironmentSource source) {
            if (environmentSources.putIfAbsent(source.id(), source) != null)
                throw new IllegalArgumentException("Duplicate environment source: " + source.id());
            return this;
        }

        public ChemistryRegistry build() {
            return new ChemistryRegistry(this);
        }
    }
}
