package dev.latrox.rematter.chemistry;

public enum SynthesisCategory {
    RAW_MATERIAL(true),
    NATURAL_MATERIAL(true),
    FLUID(true),
    GAS(true),
    COMPONENT(false),
    EQUIPMENT(false),
    MACHINE(false),
    OTHER(false);

    private final boolean permitted;

    SynthesisCategory(boolean permitted) {
        this.permitted = permitted;
    }

    public boolean permitted() {
        return permitted;
    }
}
