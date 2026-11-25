package dev.latrox.rematter.content;

public enum MachineKind {
    ANALYZER("analyzer"),
    DECOMPOSER("decomposer"),
    SYNTHESIZER("material_synthesizer"),
    REACTOR("chemical_processor"),
    TERMINAL("matter_terminal"),
    INDUSTRIAL_REACTOR("chemical_reactor_controller"),
    DRIVE("matter_drive"),
    PREPARATOR("material_preparator"),
    HAND_PREPARATOR("hand_preparator"),
    INDUSTRIAL_PREPARATOR("preparation_reactor_controller"),
    NETWORK_INTERFACE("network_interface"),
    SINTER_FURNACE("sinter_furnace"),
    PRIMAL_CONVERTER("primal_converter"),
    CRYSTAL_GENERATOR("crystal_generator"),
    INDUSTRIAL_CRYSTAL_GENERATOR("industrial_crystal_generator"),
    ATMOSPHERIC_COLLECTOR("atmospheric_collector"),
    DEEP_EXTRACTOR("deep_extractor"),
    INDUSTRIAL_DECOMPOSER("decomposer_reactor_controller"),
    INDUSTRIAL_SYNTHESIZER("synthesizer_reactor_controller"),
    TRANSMUTATION_REACTOR("transmutation_reactor_controller"),
    PRIMAL_REACTOR("primal_reactor_controller"),
    ENERGY_MATRIX("energy_matrix_controller"),
    ENERGY_REACTOR("energy_reactor_controller");

    private final String id;
    MachineKind(String id) { this.id = id; }
    public String id() { return id; }
    public boolean isStorage() { return this == DRIVE; }
    public boolean isEnergyMatrix() { return this == ENERGY_MATRIX; }
    public boolean isEnergyReactor() { return this == ENERGY_REACTOR; }
    /** Bench machines on the alcove chassis: closed left, right, back and bottom, open to the front and the top. */
    public boolean openTop() { return switch (this) { case REACTOR, ANALYZER, DECOMPOSER, SYNTHESIZER, PREPARATOR, HAND_PREPARATOR, SINTER_FURNACE, ATMOSPHERIC_COLLECTOR, DEEP_EXTRACTOR -> true; default -> false; }; }
    public boolean decomposition() { return this == DECOMPOSER || this == INDUSTRIAL_DECOMPOSER; }
    public boolean synthesis() { return this == SYNTHESIZER || this == INDUSTRIAL_SYNTHESIZER; }
    public boolean selectable() { return synthesis() || this == REACTOR || this == INDUSTRIAL_REACTOR || this == ATMOSPHERIC_COLLECTOR || this == DEEP_EXTRACTOR || this == TRANSMUTATION_REACTOR || this == PRIMAL_REACTOR; }
    public boolean generator(){return this==CRYSTAL_GENERATOR||this==INDUSTRIAL_CRYSTAL_GENERATOR;}
    public boolean processes() { return !generator() && !isStorage() && !isEnergyMatrix() && !isEnergyReactor() && this != TERMINAL && this != NETWORK_INTERFACE; }
    public boolean supportsNetworkJobs() { return processes() && this != PRIMAL_CONVERTER && this != PRIMAL_REACTOR && this != DEEP_EXTRACTOR && this != ANALYZER && this != HAND_PREPARATOR; }
}
