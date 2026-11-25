package dev.latrox.rematter.client;

import com.mojang.math.Quadrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latrox.rematter.content.ReactorGlassBlock;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

/**
 * Connected chunk-mesh glass for formed reactors: merges adjacent formed same-tier glass panes into one continuous
 * window while retaining perimeter frames, tier accents and concave inner corners around inset controllers/ports.
 */
public final class ConnectedGlassModel implements DynamicBlockStateModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("rematter", "connected_glass");
    private static final Direction[] FACES = Direction.values();
    /**
     * Per face, the four tangent directions corresponding to sprite edges (top, right, bottom, left)
     * after rotating a north-facing quad with {@link #bakeRotated}.
     */
    private static final Direction[][] TANGENTS = new Direction[6][];
    static {
        // Derived directly from tools/family_geometry.py TURNED / ROTATION for north-facing UV [0,0..16,16]
        // (on NORTH: top=UP, right=WEST, bottom=DOWN, left=EAST).
        TANGENTS[Direction.NORTH.ordinal()] = new Direction[]{Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST};
        TANGENTS[Direction.EAST.ordinal()] = new Direction[]{Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH};
        TANGENTS[Direction.SOUTH.ordinal()] = new Direction[]{Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST};
        TANGENTS[Direction.WEST.ordinal()] = new Direction[]{Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH};
        TANGENTS[Direction.UP.ordinal()] = new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        TANGENTS[Direction.DOWN.ordinal()] = new Direction[]{Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
    }

    /** [face.ordinal()][mask 0..14] baked 1-face parts (mask 15 = all 4 edges connected -> transparent, no frame). */
    private final BlockStateModelPart[][] masks;
    /** [face.ordinal()][corner 0..3] baked concave inner-corner patches (0=TR, 1=BR, 2=BL, 3=TL). */
    private final BlockStateModelPart[][] corners;
    private final Material.Baked particle;
    @BakedQuad.MaterialFlags private final int flags;

    private ConnectedGlassModel(BlockStateModelPart[][] masks, BlockStateModelPart[][] corners) {
        this.masks = masks;
        this.corners = corners;
        this.particle = masks[Direction.NORTH.ordinal()][0].particleMaterial();
        int all = 0;
        for (int f = 0; f < 6; f++) {
            for (var p : masks[f]) all |= p.materialFlags();
            for (var p : corners[f]) all |= p.materialFlags();
        }
        this.flags = all;
    }

    private static boolean sameFormedGlass(BlockAndTintGetter level, BlockPos pos, Block self) {
        BlockState neighbor = level.getBlockState(pos);
        return neighbor.is(self) && neighbor.hasProperty(ReactorGlassBlock.FORMED) && neighbor.getValue(ReactorGlassBlock.FORMED);
    }

    /**
     * Packs 9 bits per face (6 * 9 = 54 bits <= 64): bits 0..3 = orthogonal connectivity (T,R,B,L),
     * bits 4..7 = concave inner corners, bit 8 (0x100) = hidden face (adjacent same glass).
     */
    private long computeKey(BlockAndTintGetter level, BlockPos pos, Block self) {
        long packed = 0L;
        for (Direction face : FACES) {
            int f = face.ordinal();
            if (level.getBlockState(pos.relative(face)).is(self)) {
                packed |= (0x100L << (f * 9));
                continue;
            }
            Direction[] t = TANGENTS[f];
            boolean ct = sameFormedGlass(level, pos.relative(t[0]), self);
            boolean cr = sameFormedGlass(level, pos.relative(t[1]), self);
            boolean cb = sameFormedGlass(level, pos.relative(t[2]), self);
            boolean cl = sameFormedGlass(level, pos.relative(t[3]), self);
            int mask = (ct ? 1 : 0) | (cr ? 2 : 0) | (cb ? 4 : 0) | (cl ? 8 : 0);
            int inner = 0;
            if (ct && cr && !sameFormedGlass(level, pos.relative(t[0]).relative(t[1]), self)) inner |= 1;
            if (cb && cr && !sameFormedGlass(level, pos.relative(t[2]).relative(t[1]), self)) inner |= 2;
            if (cb && cl && !sameFormedGlass(level, pos.relative(t[2]).relative(t[3]), self)) inner |= 4;
            if (ct && cl && !sameFormedGlass(level, pos.relative(t[0]).relative(t[3]), self)) inner |= 8;
            packed |= (((long) (mask | (inner << 4))) << (f * 9));
        }
        return packed;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> output) {
        long key = computeKey(level, pos, state.getBlock());
        for (Direction face : FACES) {
            int f = face.ordinal();
            int faceBits = (int) ((key >>> (f * 9)) & 0x1FFL);
            if ((faceBits & 0x100) != 0) continue;
            int mask = faceBits & 0x0F;
            if (mask < 15) output.add(masks[f][mask]);
            int inner = (faceBits >>> 4) & 0x0F;
            for (int c = 0; c < 4; c++) {
                if ((inner & (1 << c)) != 0) output.add(corners[f][c]);
            }
        }
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return new GeometryKey(this, computeKey(level, pos, state.getBlock()));
    }

    private record GeometryKey(ConnectedGlassModel model, long key) {}

    @Override public Material.Baked particleMaterial() { return particle; }
    @Override @BakedQuad.MaterialFlags public int materialFlags() { return flags; }

    public record ModelRef(Identifier model) {
        public static final Codec<ModelRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(ModelRef::model)
        ).apply(instance, ModelRef::new));
    }

    public record Unbaked(List<ModelRef> masks, List<ModelRef> corners) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ModelRef.CODEC.listOf(15, 15).fieldOf("masks").forGetter(Unbaked::masks),
                ModelRef.CODEC.listOf(4, 4).fieldOf("corners").forGetter(Unbaked::corners)
        ).apply(instance, Unbaked::new));

        @Override public MapCodec<Unbaked> codec() { return MAP_CODEC; }

        @Override
        public void resolveDependencies(Resolver resolver) {
            for (var ref : masks) resolver.markDependency(ref.model());
            for (var ref : corners) resolver.markDependency(ref.model());
        }

        private static BlockStateModelPart bakeRotated(ModelBaker baker, Identifier model, Direction face) {
            var variant = new Variant(model);
            variant = switch (face) {
                case NORTH -> variant;
                case EAST -> variant.withYRot(Quadrant.R90);
                case SOUTH -> variant.withYRot(Quadrant.R180);
                case WEST -> variant.withYRot(Quadrant.R270);
                case UP -> variant.withXRot(Quadrant.R270);
                case DOWN -> variant.withXRot(Quadrant.R90);
            };
            return variant.bake(baker);
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            var bakedMasks = new BlockStateModelPart[6][15];
            var bakedCorners = new BlockStateModelPart[6][4];
            for (Direction face : FACES) {
                int f = face.ordinal();
                for (int m = 0; m < 15; m++) bakedMasks[f][m] = bakeRotated(baker, masks.get(m).model(), face);
                for (int c = 0; c < 4; c++) bakedCorners[f][c] = bakeRotated(baker, corners.get(c).model(), face);
            }
            return new ConnectedGlassModel(bakedMasks, bakedCorners);
        }
    }
}
