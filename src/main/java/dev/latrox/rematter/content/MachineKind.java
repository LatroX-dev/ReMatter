package dev.latrox.rematter.content;

public enum MachineKind {
    ANALYZER("analyzer"),
    DECOMPOSER("decomposer"),
    SYNTHESIZER("material_synthesizer"),
    ELECTROLYZER("electrolyzer"),
    REACTOR("chemical_reactor"),
    STORAGE("matter_storage"),
    TERMINAL("matter_terminal"),
    INDUSTRIAL_REACTOR("industrial_reactor");

    private final String id;
    MachineKind(String id) { this.id = id; }
    public String id() { return id; }
    public boolean isStorage() { return this == STORAGE; }
    public boolean processes() { return this != STORAGE && this != TERMINAL; }
}
