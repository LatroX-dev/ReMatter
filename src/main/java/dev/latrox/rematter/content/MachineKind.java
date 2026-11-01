package dev.latrox.rematter.content;

public enum MachineKind {
    ANALYZER("analyzer"),
    DECOMPOSER("decomposer"),
    SYNTHESIZER("material_synthesizer"),
    ELECTROLYZER("electrolyzer"),
    REACTOR("chemical_reactor"),
    STORAGE("matter_storage"),
    TERMINAL("matter_terminal"),
    INDUSTRIAL_REACTOR("industrial_reactor"),
    DRIVE("matter_drive"),
    PREPARATOR("material_preparator"),
    HAND_PREPARATOR("hand_preparator"),
    INDUSTRIAL_PREPARATOR("industrial_preparator");

    private final String id;
    MachineKind(String id) { this.id = id; }
    public String id() { return id; }
    public boolean isStorage() { return this == STORAGE || this == DRIVE; }
    public boolean processes() { return !isStorage() && this != TERMINAL; }
}
