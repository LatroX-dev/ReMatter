package dev.latrox.rematter.content;

/** Craftable, resource-free mechanisms installed at actual positions inside an industrial shell. */
public enum IndustrialModuleKind {
    GRINDING_UNIT("grinding_unit"),
    SEPARATION_UNIT("separation_unit"),
    MIXING_BASIN("mixing_basin"),
    FIELD_COIL("field_coil"),
    REACTION_CHAMBER("reaction_chamber");

    private final String id;
    IndustrialModuleKind(String id) { this.id = id; }
    public String id() { return id; }
}
