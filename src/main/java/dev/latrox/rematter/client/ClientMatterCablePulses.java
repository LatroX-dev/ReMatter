package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MatterPipeBlock;
import dev.latrox.rematter.network.MatterCablePulsePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Full-bright glints follow the received route using the client particle engine. */
@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientMatterCablePulses {
    private static final int MAX_ACTIVE_PULSES = 12;
    private static final Direction[] FACES = Direction.values();
    private static final SpriteId GLINT = new SpriteId(TextureAtlas.LOCATION_PARTICLES,
            Identifier.withDefaultNamespace("generic_0"));
    private static final List<Pulse> ACTIVE = new ArrayList<>();
    private static ClientLevel activeLevel;

    private ClientMatterCablePulses() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(MatterCablePulsePayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            useLevel(minecraft.level);
            if (activeLevel == null || minecraft.options.particles().get() == ParticleStatus.MINIMAL) return;
            if (ACTIVE.size() == MAX_ACTIVE_PULSES) return;
            if (ACTIVE.stream().anyMatch(pulse -> pulse.energy == payload.energy() && pulse.path.equals(payload.path()))) return;
            ACTIVE.add(new Pulse(payload.path(), payload.energy()));
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        useLevel(minecraft.level);
        if (activeLevel == null || minecraft.player == null || minecraft.isPaused()) return;
        if (minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            ACTIVE.clear();
            return;
        }
        if (ACTIVE.isEmpty()) return;
        Vec3 viewer = minecraft.gameRenderer.getMainCamera().position();
        ACTIVE.removeIf(pulse -> pulse.advance(minecraft, activeLevel, viewer));
    }

    private static void useLevel(ClientLevel level) {
        if (activeLevel != level) {
            ACTIVE.clear();
            activeLevel = level;
        }
    }

    private static void glint(Minecraft minecraft, ClientLevel level, Vec3 start, Vec3 target,
            MatterPipeBlock pipe, int ticks, boolean energy) {
        int color = energy ? 0xfff4d6 : switch (pipe.tier()) {
            case BASIC -> 0x83fff0;
            case REINFORCED -> 0xffcb70;
            case RESONANT -> 0xdeb4ff;
        };
        minecraft.particleEngine.add(new Glint(level, start, target, ticks, color, energy ? .037F : .054F,
                minecraft.getAtlasManager().get(GLINT)));
    }

    private static Vec3 surface(Vec3 center, Direction face, BlockState state) {
        double offset = MatterPipeBlock.connections(state) >= 3 ? .193 : .131;
        return center.add(face.getUnitVec3().scale(offset));
    }

    private static Direction visibleFace(Vec3 viewer, Vec3 center, Direction.Axis cableAxis) {
        Vec3 sight = viewer.subtract(center);
        Direction best = Direction.UP;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Direction face : FACES) {
            if (face.getAxis() == cableAxis) continue;
            double score = sight.dot(face.getUnitVec3());
            if (score > bestScore) {
                best = face;
                bestScore = score;
            }
        }
        return best;
    }

    private static final class Pulse {
        private final List<BlockPos> path;
        private final int stepTicks;
        private final boolean energy;
        private int step;
        private int delay;

        private Pulse(List<BlockPos> path, boolean energy) {
            this.path = path;
            this.energy = energy;
            this.stepTicks = energy ? 2 : Math.max(2, Math.min(4, 36 / Math.max(1, path.size() - 1)));
        }

        private boolean advance(Minecraft minecraft, ClientLevel level, Vec3 viewer) {
            if (delay > 0) {
                delay--;
                return false;
            }
            BlockPos from = path.get(step);
            BlockPos to = path.get(Math.min(step + 1, path.size() - 1));
            if (level.hasChunkAt(from) && level.hasChunkAt(to)) {
                BlockState source = level.getBlockState(from);
                BlockState target = level.getBlockState(to);
                Vec3 center = Vec3.atCenterOf(from);
                if (source.getBlock() instanceof MatterPipeBlock pipe && target.getBlock() instanceof MatterPipeBlock
                        && viewer.distanceToSqr(center) < 40 * 40) {
                    Direction direction = Direction.getNearest(to.subtract(from), Direction.NORTH);
                    Direction face = visibleFace(viewer, center, direction.getAxis());
                    glint(minecraft, level, surface(center, face, source),
                            surface(Vec3.atCenterOf(to), face, target), pipe, stepTicks, energy);
                }
            }
            step++;
            delay = stepTicks - 1;
            return step >= path.size() - 1;
        }
    }

    private static final class Glint extends SingleQuadParticle {
        private Glint(ClientLevel level, Vec3 start, Vec3 target, int ticks, int color, float size,
                TextureAtlasSprite sprite) {
            super(level, start.x, start.y, start.z, sprite);
            setLifetime(ticks);
            hasPhysics = false;
            friction = 1;
            quadSize = size;
            xd = (target.x - start.x) / ticks;
            yd = (target.y - start.y) / ticks;
            zd = (target.z - start.z) / ticks;
            rCol = ARGB.red(color) / 255F;
            gCol = ARGB.green(color) / 255F;
            bCol = ARGB.blue(color) / 255F;
        }

        @Override public Layer getLayer() { return Layer.OPAQUE; }
        @Override public int getLightCoords(float partialTick) { return 0xF000F0; }
    }
}
