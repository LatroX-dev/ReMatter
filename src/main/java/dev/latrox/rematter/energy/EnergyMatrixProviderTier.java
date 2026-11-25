package dev.latrox.rematter.energy;

/** Four transfer-bandwidth tiers of modular interior induction providers for the Energy Matrix multiblock. */
public enum EnergyMatrixProviderTier {
    BASIC("energy_matrix_provider", 262_144L),
    REINFORCED("reinforced_energy_matrix_provider", 1_048_576L),
    RESONANT("resonant_energy_matrix_provider", 4_194_304L),
    PRIMAL("primal_energy_matrix_provider", 16_777_216L);

    private final String id;
    private final long transferRate;

    EnergyMatrixProviderTier(String id, long transferRate) {
        this.id = id;
        this.transferRate = transferRate;
    }

    public String id() { return id; }
    public long transferRate() { return transferRate; }
}
