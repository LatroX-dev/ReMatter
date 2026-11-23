package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MatterCableBlock;
import dev.latrox.rematter.network.MatterCablePulsePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Zero-particle 3D cable-hugging transfer indicator rendered in one batched additive pass
 * (RenderTypes.lightning) during committed Matter and Energy transfers.
 * Respects ParticleStatus and uses only UiPalette colours.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientMatterCablePulses {
    public enum TransferStyle { ENERGY_SLEEVE, TRACE_RING, NONE }

    /** One-line switch for active cable transfer visualization (Matter transfers only by default). */
    public static final TransferStyle TRANSFER_STYLE = TransferStyle.ENERGY_SLEEVE;
    /** Keep continuous FE supply quiet on the PCB traces; set true to also show energy transfer waves. */
    public static final boolean SHOW_ENERGY_PULSES = false;

    private static final int MAX_ACTIVE_PULSES = 24;
    private static final double MAX_VIEW_DISTANCE_SQ = 44.0 * 44.0;
    private static final float HUB_BOUNDARY = 0.19f;
    private static final float SLICE_STEP = 0.08f;
    private static final ContextKey<RenderPulses> KEY = new ContextKey<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "cable_transfer_pulses"));
    private static final List<Pulse> ACTIVE = new ArrayList<>();
    private static ClientLevel activeLevel;

    private record SleeveSlice(float ax, float ay, float az, float bx, float by, float bz,
            Direction dir, float radius, float r, float g, float b, float alphaA, float alphaB) {}

    private record RenderPulses(List<SleeveSlice> slices) {}

    private ClientMatterCablePulses() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(MatterCablePulsePayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            useLevel(minecraft.level);
            if (activeLevel == null || TRANSFER_STYLE == TransferStyle.NONE
                    || (payload.energy() && !SHOW_ENERGY_PULSES)
                    || minecraft.options.particles().get() == ParticleStatus.MINIMAL) return;
            if (payload.path().size() < 2 || ACTIVE.size() >= MAX_ACTIVE_PULSES) return;
            if (ACTIVE.stream().anyMatch(pulse -> pulse.energy == payload.energy() && pulse.path.equals(payload.path()))) return;
            ACTIVE.add(new Pulse(payload.path(), payload.energy()));
        });
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        activeLevel = null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        useLevel(minecraft.level);
        if (activeLevel == null || minecraft.player == null || minecraft.isPaused()) return;
        if (TRANSFER_STYLE == TransferStyle.NONE || minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            ACTIVE.clear();
            return;
        }
        if (ACTIVE.isEmpty()) return;
        float tail = tailSpan(TRANSFER_STYLE);
        ACTIVE.removeIf(pulse -> pulse.advance(tail));
    }

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (TRANSFER_STYLE == TransferStyle.NONE || activeLevel != event.getLevel() || ACTIVE.isEmpty()
                || minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            event.getRenderState().setRenderData(KEY, new RenderPulses(List.of()));
            return;
        }
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().position();
        boolean reduced = minecraft.options.particles().get() == ParticleStatus.DECREASED;
        float alphaScale = reduced ? 0.65f : 1.0f;
        var slices = new ArrayList<SleeveSlice>();
        for (Pulse pulse : ACTIVE) {
            pulse.extractSlices(activeLevel, cam, partialTick, TRANSFER_STYLE, alphaScale, slices);
        }
        event.getRenderState().setRenderData(KEY, new RenderPulses(List.copyOf(slices)));
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        RenderPulses extracted = event.getLevelRenderState().getRenderData(KEY);
        if (extracted == null || extracted.slices().isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, vertices) -> {
            for (SleeveSlice s : extracted.slices()) {
                // Orthonormal right-handed basis (F, U, V) with F = travel direction, U x V = F.
                float fx = s.dir().getStepX(), fy = s.dir().getStepY(), fz = s.dir().getStepZ();
                float ux, uy, uz;
                if (s.dir().getAxis() == Direction.Axis.Y) {
                    ux = 0f; uy = 0f; uz = 1f;
                } else {
                    ux = 0f; uy = 1f; uz = 0f;
                }
                // V = F x U
                float vx = fy * uz - fz * uy;
                float vy = fz * ux - fx * uz;
                float vz = fx * uy - fy * ux;
                float rad = s.radius();
                float[] sx = {+1, -1, -1, +1};
                float[] sv = {+1, +1, -1, -1};
                for (int side = 0; side < 4; side++) {
                    int next = (side + 1) & 3;
                    float o0x = rad * (sx[side] * ux + sv[side] * vx);
                    float o0y = rad * (sx[side] * uy + sv[side] * vy);
                    float o0z = rad * (sx[side] * uz + sv[side] * vz);
                    float o1x = rad * (sx[next] * ux + sv[next] * vx);
                    float o1y = rad * (sx[next] * uy + sv[next] * vy);
                    float o1z = rad * (sx[next] * uz + sv[next] * vz);
                    // Outward CCW winding: c0(A) -> c1(A) -> c1(B) -> c0(B).
                    vertices.addVertex(pose, s.ax() + o0x, s.ay() + o0y, s.az() + o0z)
                            .setColor(s.r(), s.g(), s.b(), s.alphaA());
                    vertices.addVertex(pose, s.ax() + o1x, s.ay() + o1y, s.az() + o1z)
                            .setColor(s.r(), s.g(), s.b(), s.alphaA());
                    vertices.addVertex(pose, s.bx() + o1x, s.by() + o1y, s.bz() + o1z)
                            .setColor(s.r(), s.g(), s.b(), s.alphaB());
                    vertices.addVertex(pose, s.bx() + o0x, s.by() + o0y, s.bz() + o0z)
                            .setColor(s.r(), s.g(), s.b(), s.alphaB());
                }
            }
        });
    }

    private static void useLevel(ClientLevel level) {
        if (activeLevel != level) {
            ACTIVE.clear();
            activeLevel = level;
        }
    }

    private static float frontSpan(TransferStyle style) {
        return style == TransferStyle.TRACE_RING ? 0.09f : 0.14f;
    }

    private static float tailSpan(TransferStyle style) {
        return style == TransferStyle.TRACE_RING ? 0.09f : 0.56f;
    }

    private static float envelope(float delta, boolean energy, TransferStyle style) {
        if (style == TransferStyle.TRACE_RING) {
            float d = Math.abs(delta);
            float span = 0.09f;
            if (d >= span) return 0f;
            float u = 1f - d / span;
            float peak = energy ? 0.62f : 0.54f;
            return peak * u * (2f - u);
        }
        // ENERGY_SLEEVE: soft leading edge + silky trailing wake wrapping the conduit.
        float peak = energy ? 0.48f : 0.40f;
        if (delta > 0f) {
            float span = 0.14f;
            if (delta >= span) return 0f;
            float u = 1f - delta / span;
            return peak * u * u * (3f - 2f * u);
        }
        float tail = 0.56f;
        if (delta <= -tail) return 0f;
        float u = 1f + delta / tail;
        return peak * u * u;
    }

    private static int tierColor(MatterCableBlock.Tier tier) {
        return switch (tier) {
            case BASIC -> UiPalette.CABLE_BASIC;
            case REINFORCED -> UiPalette.CABLE_REINFORCED;
            case RESONANT -> UiPalette.CABLE_RESONANT;
        };
    }

    private static final class Pulse {
        private final List<BlockPos> path;
        private final boolean energy;
        private final float speed;
        private float prevDistance;
        private float distance;

        private Pulse(List<BlockPos> path, boolean energy) {
            this.path = path;
            this.energy = energy;
            this.speed = energy ? 0.30f : 0.22f;
        }

        private boolean advance(float tailSpan) {
            prevDistance = distance;
            distance += speed;
            return distance - tailSpan > path.size() - 1;
        }

        private void extractSlices(ClientLevel level, Vec3 cam, float partialTick, TransferStyle style,
                float alphaScale, List<SleeveSlice> out) {
            float head = Mth.lerp(partialTick, prevDistance, distance);
            float maxDist = path.size() - 1;
            float sMin = Math.max(0f, head - tailSpan(style));
            float sMax = Math.min(maxDist, head + frontSpan(style));
            if (sMax - sMin < 0.005f) return;

            int firstSeg = Math.max(0, (int) Math.floor(sMin));
            int lastSeg = Math.min(path.size() - 2, (int) Math.floor(sMax - 1e-4f));
            for (int seg = firstSeg; seg <= lastSeg; seg++) {
                BlockPos from = path.get(seg);
                BlockPos to = path.get(seg + 1);
                if (!level.hasChunkAt(from) || !level.hasChunkAt(to)) continue;
                Vec3 fromCenter = Vec3.atCenterOf(from);
                if (cam.distanceToSqr(fromCenter) > MAX_VIEW_DISTANCE_SQ) continue;

                BlockState fromState = level.getBlockState(from);
                BlockState toState = level.getBlockState(to);
                MatterCableBlock cable = fromState.getBlock() instanceof MatterCableBlock c ? c
                        : toState.getBlock() instanceof MatterCableBlock c2 ? c2 : null;
                if (cable == null) continue;

                boolean fromHub = fromState.getBlock() instanceof MatterCableBlock && MatterCableBlock.connections(fromState) >= 3;
                boolean toHub = toState.getBlock() instanceof MatterCableBlock && MatterCableBlock.connections(toState) >= 3;
                Direction dir = Direction.getNearest(to.subtract(from), Direction.NORTH);
                int color = energy ? UiPalette.AMBER : tierColor(cable.tier());
                float r = ARGB.red(color) / 255f;
                float g = ARGB.green(color) / 255f;
                float b = ARGB.blue(color) / 255f;

                float t0 = Math.max(0f, sMin - seg);
                float t1 = Math.min(1f, sMax - seg);
                if (t1 - t0 < 0.004f) continue;

                float[] stops = buildStops(t0, t1, head - seg, fromHub, toHub);
                Vec3 toCenter = Vec3.atCenterOf(to);
                for (int i = 0; i < stops.length - 1; i++) {
                    float a = stops[i];
                    float end = stops[i + 1];
                    if (end - a < 0.003f) continue;
                    float alphaA = envelope(seg + a - head, energy, style) * alphaScale;
                    float alphaB = envelope(seg + end - head, energy, style) * alphaScale;
                    if (alphaA < 0.004f && alphaB < 0.004f) continue;

                    float mid = (a + end) * 0.5f;
                    boolean inHub = (fromHub && mid < HUB_BOUNDARY) || (toHub && mid > 1f - HUB_BOUNDARY);
                    float radius = style == TransferStyle.TRACE_RING
                            ? (inHub ? 0.198f : 0.139f)
                            : (inHub ? 0.1935f : 0.131f);
                    float ax = (float) (Mth.lerp(a, fromCenter.x, toCenter.x) - cam.x);
                    float ay = (float) (Mth.lerp(a, fromCenter.y, toCenter.y) - cam.y);
                    float az = (float) (Mth.lerp(a, fromCenter.z, toCenter.z) - cam.z);
                    float bx = (float) (Mth.lerp(end, fromCenter.x, toCenter.x) - cam.x);
                    float by = (float) (Mth.lerp(end, fromCenter.y, toCenter.y) - cam.y);
                    float bz = (float) (Mth.lerp(end, fromCenter.z, toCenter.z) - cam.z);
                    out.add(new SleeveSlice(ax, ay, az, bx, by, bz, dir, radius, r, g, b, alphaA, alphaB));
                }
            }
        }

        private static float[] buildStops(float t0, float t1, float localHead, boolean fromHub, boolean toHub) {
            float[] buf = new float[24];
            int count = 0;
            buf[count++] = t0;
            if (fromHub && HUB_BOUNDARY > t0 && HUB_BOUNDARY < t1) buf[count++] = HUB_BOUNDARY;
            if (toHub && 1f - HUB_BOUNDARY > t0 && 1f - HUB_BOUNDARY < t1) buf[count++] = 1f - HUB_BOUNDARY;
            if (localHead > t0 && localHead < t1) buf[count++] = localHead;
            for (float s = t0 + SLICE_STEP; s < t1 - 0.01f; s += SLICE_STEP) {
                if (count < buf.length - 1) buf[count++] = s;
            }
            buf[count++] = t1;
            java.util.Arrays.sort(buf, 0, count);
            return java.util.Arrays.copyOf(buf, count);
        }
    }
}
