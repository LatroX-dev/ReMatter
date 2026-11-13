package dev.latrox.rematter.client;

import com.mojang.math.Quadrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latrox.rematter.content.PortAppearance;
import dev.latrox.rematter.content.MachinePortMode.Side;
import dev.latrox.rematter.content.PortSides;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jspecify.annotations.Nullable;

/**
 * The closed sides of a device as part of its block model. Each side is one baked model chosen by the role the
 * block entity publishes: a plain casing face for a side without a connection, the seat and ring of a port plate
 * otherwise. Being chunk geometry, a plate neither pops in with distance nor depends on a block entity renderer.
 */
public final class PortPlatesModel implements DynamicBlockStateModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("rematter", "port_plates");
    private static final Side[] SIDES = Side.values();
    private static final int ROLES = PortAppearance.values().length;
    /** One baked model per side and role; null where the block state lists no model for a side. */
    private final @Nullable BlockStateModelPart[] parts;
    private final int initial;
    private final Material.Baked particle;
    @BakedQuad.MaterialFlags private final int flags;

    private PortPlatesModel(@Nullable BlockStateModelPart[] parts, int initial) {
        this.parts = parts; this.initial = initial;
        Material.Baked first = null; int all = 0;
        for (var part : parts) if (part != null) {
            if (first == null) first = part.particleMaterial();
            all |= part.materialFlags();
        }
        if (first == null) throw new IllegalArgumentException("Port plates need at least one side");
        particle = first; flags = all;
    }

    /** The published roles, or the roles of a newly placed device where no block entity answers. */
    private int roles(BlockAndTintGetter level, BlockPos pos) {
        Integer published = level.getModelData(pos).get(PortSides.PLATES);
        return published == null ? initial : published;
    }

    @Override public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
            List<BlockStateModelPart> output) {
        int roles = roles(level, pos);
        for (Side side : SIDES) {
            var part = parts[side.ordinal() * ROLES + PortSides.appearance(roles, side).ordinal()];
            if (part != null) output.add(part);
        }
    }
    @Override public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return new GeometryKey(this, roles(level, pos));
    }
    private record GeometryKey(PortPlatesModel model, int roles) {}
    @Override public Material.Baked particleMaterial() { return particle; }
    @Override @BakedQuad.MaterialFlags public int materialFlags() { return flags; }

    /**
     * {@code sides} maps a side to its models by role; a role without a model of its own shows the model of
     * {@code disabled}, the plain face. A side without a {@code disabled} model adds a part to a face the body
     * draws itself, as the seats of an Energy Cube do. {@code default} holds the roles of an unconfigured device.
     */
    public record Unbaked(Quadrant y, Map<Side, PortAppearance> initial, Map<Side, Map<PortAppearance, Identifier>> sides)
            implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Quadrant.CODEC.optionalFieldOf("y", Quadrant.R0).forGetter(Unbaked::y),
                Codec.unboundedMap(Side.CODEC, PortAppearance.CODEC).fieldOf("default").forGetter(Unbaked::initial),
                Codec.unboundedMap(Side.CODEC, Codec.unboundedMap(PortAppearance.CODEC, Identifier.CODEC))
                        .fieldOf("sides").forGetter(Unbaked::sides)).apply(instance, Unbaked::new));

        @Override public MapCodec<Unbaked> codec() { return MAP_CODEC; }
        @Override public void resolveDependencies(Resolver resolver) {
            sides.values().forEach(models -> models.values().forEach(resolver::markDependency));
        }
        @Override public BlockStateModel bake(ModelBaker baker) {
            var parts = new BlockStateModelPart[SIDES.length * ROLES];
            for (var entry : sides.entrySet()) {
                var models = entry.getValue();
                for (PortAppearance role : PortAppearance.values()) {
                    var model = models.getOrDefault(role, models.get(PortAppearance.DISABLED));
                    if (model != null) parts[entry.getKey().ordinal() * ROLES + role.ordinal()] = new Variant(model).withYRot(y).bake(baker);
                }
            }
            return new PortPlatesModel(parts, PortSides.packAppearances(side -> initial.getOrDefault(side, PortAppearance.DISABLED)));
        }
    }
}
