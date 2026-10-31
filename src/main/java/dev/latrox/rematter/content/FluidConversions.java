package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.List;

/** Shared definitions for the actual tank-to-matter operations and recipe viewers. */
public final class FluidConversions {
    public record Definition(String fluidId, SubstanceKey substance, int amount,
                             long decompositionEnergy, long synthesisEnergy, int ticks) {}

    public static final Definition WATER = new Definition("minecraft:water", BuiltinChemistry.WATER, 1_000, 2_000, 2_000, 80);
    public static final Definition LAVA = new Definition("minecraft:lava", BuiltinChemistry.LAVA, 1_000, 2_000, 24_000, 80);
    public static final List<Definition> ALL = List.of(WATER, LAVA);

    private FluidConversions() {}
}
