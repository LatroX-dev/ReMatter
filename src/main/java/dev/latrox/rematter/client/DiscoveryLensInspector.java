package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.EnvironmentSource;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure research-gated spectrometry logic shared by the in-world Discovery Lens HUD and Shift tooltips.
 * Never reveals unanalyzed substance compositions when research progression is enabled.
 */
public final class DiscoveryLensInspector {
    public enum SynthesisStatus {
        NONE,
        DECOMPOSITION_ONLY,
        SYNTHESIS_LOCKED,
        SYNTHESIS_UNLOCKED
    }

    public record KnownTrace(SubstanceKey key, long amount) {}

    public record SampleReport(
            boolean primalSignature,
            boolean unanalyzed,
            int batchCount,
            boolean fluidMillibuckets,
            boolean durabilityScaled,
            List<KnownTrace> knownTraces,
            int hiddenTraceCount,
            SynthesisStatus synthesisStatus) {
        public SampleReport {
            knownTraces = List.copyOf(knownTraces);
        }
    }

    public record AtmosphereEntry(EnvironmentSource source, List<KnownTrace> knownYields, int hiddenYieldCount) {
        public AtmosphereEntry {
            knownYields = List.copyOf(knownYields);
        }
    }

    private DiscoveryLensInspector() {}

    public static SampleReport inspectCurrent(
            ChemistryRegistry registry,
            String exactId,
            boolean fluid,
            int batchCount,
            boolean durabilityScaled,
            Map<SubstanceKey, Long> matter) {
        return inspect(
                registry,
                exactId,
                fluid,
                batchCount,
                durabilityScaled,
                matter,
                ClientResearchState.enabled(),
                ClientResearchState.available(),
                ClientResearchState.known(exactId),
                ClientResearchState::knownSubstance);
    }

    public static SampleReport inspect(
            ChemistryRegistry registry,
            String exactId,
            boolean fluid,
            int batchCount,
            boolean durabilityScaled,
            Map<SubstanceKey, Long> matter,
            boolean researchEnabled,
            boolean researchAvailable,
            boolean targetSynthesisKnown,
            java.util.function.Predicate<SubstanceKey> substanceKnown) {
        if (!fluid && ReservedSubstances.primalItem(exactId)) {
            return new SampleReport(true, false, 1, false, false, List.of(), 0, SynthesisStatus.NONE);
        }
        boolean allowlisted = fluid
                ? registry.synthesisPolicy().allowsFluid(exactId)
                : registry.synthesisPolicy().allowsItem(exactId);

        if (researchEnabled && !researchAvailable) {
            return new SampleReport(
                    false,
                    true,
                    batchCount,
                    fluid,
                    durabilityScaled,
                    List.of(),
                    matter.size(),
                    allowlisted ? SynthesisStatus.SYNTHESIS_LOCKED : SynthesisStatus.DECOMPOSITION_ONLY);
        }

        List<KnownTrace> revealed = new ArrayList<>(matter.size());
        int hidden = 0;
        for (var entry : matter.entrySet()) {
            if (!researchEnabled || targetSynthesisKnown || substanceKnown.test(entry.getKey())) {
                revealed.add(new KnownTrace(entry.getKey(), entry.getValue()));
            } else {
                hidden++;
            }
        }

        if (researchEnabled && revealed.isEmpty()) {
            return new SampleReport(
                    false,
                    true,
                    batchCount,
                    fluid,
                    durabilityScaled,
                    List.of(),
                    hidden,
                    allowlisted ? SynthesisStatus.SYNTHESIS_LOCKED : SynthesisStatus.DECOMPOSITION_ONLY);
        }

        SynthesisStatus synthStatus;
        if (!allowlisted || durabilityScaled) {
            synthStatus = SynthesisStatus.DECOMPOSITION_ONLY;
        } else if (!researchEnabled || targetSynthesisKnown) {
            synthStatus = SynthesisStatus.SYNTHESIS_UNLOCKED;
        } else {
            synthStatus = SynthesisStatus.SYNTHESIS_LOCKED;
        }
        return new SampleReport(
                false,
                false,
                batchCount,
                fluid,
                durabilityScaled,
                revealed,
                hidden,
                synthStatus);
    }

    public static List<AtmosphereEntry> inspectAtmosphere(
            ChemistryRegistry registry,
            String dimensionId,
            boolean skyVisible,
            boolean researchEnabled,
            boolean researchAvailable,
            Set<SubstanceKey> knownSubstances) {
        return inspectAtmosphere(
                registry,
                dimensionId,
                skyVisible,
                researchEnabled,
                researchAvailable,
                knownSubstances::contains);
    }

    public static List<AtmosphereEntry> inspectAtmosphereCurrent(
            ChemistryRegistry registry, String dimensionId, boolean skyVisible) {
        return inspectAtmosphere(
                registry,
                dimensionId,
                skyVisible,
                ClientResearchState.enabled(),
                ClientResearchState.available(),
                ClientResearchState::knownSubstance);
    }

    public static List<AtmosphereEntry> inspectAtmosphere(
            ChemistryRegistry registry,
            String dimensionId,
            boolean skyVisible,
            boolean researchEnabled,
            boolean researchAvailable,
            java.util.function.Predicate<SubstanceKey> substanceKnown) {
        List<AtmosphereEntry> out = new ArrayList<>();
        for (EnvironmentSource source : registry.environmentSources()) {
            if (!source.dimensions().contains(dimensionId)) continue;
            if (source.requiresOpenAir() && !skyVisible) continue;
            List<KnownTrace> known = new ArrayList<>();
            int hidden = 0;
            for (var entry : source.outputs().entrySet()) {
                if (!researchEnabled || (researchAvailable && substanceKnown.test(entry.getKey()))) {
                    known.add(new KnownTrace(entry.getKey(), entry.getValue()));
                } else {
                    hidden++;
                }
            }
            out.add(new AtmosphereEntry(source, known, hidden));
        }
        return List.copyOf(out);
    }
}
