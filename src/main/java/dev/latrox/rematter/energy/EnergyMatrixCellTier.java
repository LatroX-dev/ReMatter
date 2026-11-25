package dev.latrox.rematter.energy;

/** Four capacity tiers of modular interior cells for the Energy Matrix multiblock. */
public enum EnergyMatrixCellTier {
    BASIC("energy_matrix_cell", 64_000_000L),
    REINFORCED("reinforced_energy_matrix_cell", 512_000_000L),
    RESONANT("resonant_energy_matrix_cell", 4_096_000_000L),
    PRIMAL("primal_energy_matrix_cell", 32_768_000_000L);

    private final String id;
    private final long capacity;

    EnergyMatrixCellTier(String id, long capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public String id() { return id; }
    public long capacity() { return capacity; }
}
