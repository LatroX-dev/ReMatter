package dev.latrox.rematter.primal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** An explicit dimension gate, independent of biome tags. End ore also stays away from the central island. */
public final class PrimalDimensionFilter extends PlacementFilter {
    public static final MapCodec<PrimalDimensionFilter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(value -> value.dimension),
            Codec.intRange(0, 30000000).optionalFieldOf("minimum_radius", 0).forGetter(value -> value.minimumRadius)
    ).apply(instance, PrimalDimensionFilter::new));
    private final String dimension; private final int minimumRadius;
    public PrimalDimensionFilter(String dimension, int minimumRadius) {
        net.minecraft.resources.Identifier.parse(dimension);
        if (minimumRadius < 0) throw new IllegalArgumentException("Negative exclusion radius");
        this.dimension = dimension; this.minimumRadius = minimumRadius;
    }
    public static boolean permits(String actual, String expected, int minimumRadius, int x, int z) {
        return PrimalPlacementPolicy.permits(actual, expected, minimumRadius, x, z);
    }
    @Override protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos position) {
        return permits(context.getLevel().getLevel().dimension().identifier().toString(), dimension, minimumRadius, position.getX(), position.getZ());
    }
    @Override public PlacementModifierType<?> type() { return PrimalContent.DIMENSION_FILTER.get(); }
}
