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
    SINTER_FURNACE("sinter_furnace");

    private final String id;
    MachineKind(String id) { this.id = id; }
    public String id() { return id; }
    public boolean isStorage() { return this == DRIVE; }
    public boolean processes() { return !isStorage() && this != TERMINAL && this != NETWORK_INTERFACE; }
}
