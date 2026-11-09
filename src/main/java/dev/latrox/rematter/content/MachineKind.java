package dev.latrox.rematter.content;

public enum MachineKind {
    ANALYZER("analyzer"),
    DECOMPOSER("decomposer"),
    SYNTHESIZER("material_synthesizer"),
    ELECTROLYZER("electrolyzer"),
    REACTOR("chemical_reactor"),
    TERMINAL("matter_terminal"),
    INDUSTRIAL_REACTOR("industrial_reactor"),
    DRIVE("matter_drive"),
    PREPARATOR("material_preparator"),
    HAND_PREPARATOR("hand_preparator"),
    INDUSTRIAL_PREPARATOR("industrial_preparator"),
    NETWORK_INTERFACE("network_interface"),
    SINTER_FURNACE("sinter_furnace"),
    SEPARATOR("separator"),
    THERMAL_PROCESSOR("thermal_processor"),
    CRYSTALLIZER("crystallizer"),
    AIR_SEPARATOR("air_separator");

    private final String id;
    MachineKind(String id) { this.id = id; }
    public String id() { return id; }
    public boolean isStorage() { return this == DRIVE; }
    public boolean synthesis() { return this == SYNTHESIZER || this == CRYSTALLIZER; }
    public boolean selectable() { return synthesis() || this == ELECTROLYZER || this == REACTOR
            || this == INDUSTRIAL_REACTOR || this == SEPARATOR || this == THERMAL_PROCESSOR || this == AIR_SEPARATOR; }
    public boolean processes() { return !isStorage() && this != TERMINAL && this != NETWORK_INTERFACE; }
    public boolean supportsNetworkJobs() { return processes() && this != ANALYZER && this != HAND_PREPARATOR && this != AIR_SEPARATOR; }
}
