package dev.latrox.rematter.content;

public enum MachineKind {
    ANALYZER("analyzer"),
    DECOMPOSER("decomposer"),
    SYNTHESIZER("material_synthesizer"),
    REACTOR("chemical_reactor"),
    TERMINAL("matter_terminal"),
    INDUSTRIAL_REACTOR("industrial_reactor"),
    DRIVE("matter_drive"),
    PREPARATOR("material_preparator"),
    HAND_PREPARATOR("hand_preparator"),
    INDUSTRIAL_PREPARATOR("industrial_preparator"),
    NETWORK_INTERFACE("network_interface"),
    SINTER_FURNACE("sinter_furnace"),
    PRIMAL_CONVERTER("primal_converter"),
    CRYSTAL_GENERATOR("crystal_generator"),
    INDUSTRIAL_CRYSTAL_GENERATOR("industrial_crystal_generator");

    private final String id;
    MachineKind(String id) { this.id = id; }
    public String id() { return id; }
    public boolean isStorage() { return this == DRIVE; }
    public boolean synthesis() { return this == SYNTHESIZER; }
    public boolean selectable() { return synthesis() || this == REACTOR || this == INDUSTRIAL_REACTOR; }
    public boolean generator(){return this==CRYSTAL_GENERATOR||this==INDUSTRIAL_CRYSTAL_GENERATOR;}
    public boolean processes() { return !generator() && !isStorage() && this != TERMINAL && this != NETWORK_INTERFACE; }
    public boolean supportsNetworkJobs() { return processes() && this != PRIMAL_CONVERTER && this != ANALYZER && this != HAND_PREPARATOR; }
}
