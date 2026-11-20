package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.IndustrialModuleBlock;
import dev.latrox.rematter.content.IndustrialModuleKind;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.network.StructureHintPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

/** A bounded chamber preview persists while toggled on and hides immediately when toggled off or satisfied. */
@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientStructureHints {
    private static final long ACTIVE_PERSIST_TICKS = 24_000L;

    private record WireBox(AABB box, int color, float width) {}
    private record RenderHint(List<WireBox> boxes) {}

    private static final ContextKey<RenderHint> KEY = new ContextKey<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "construction_hint"));
    private static StructureHintPayload hint = StructureHintPayload.EMPTY;
    private static ClientLevel level;
    private static long expires;

    private ClientStructureHints() {}

    public static void accept(StructureHintPayload value) {
        if (!value.showOverlay() || !value.present()) {
            clear();
            return;
        }
        hint = value;
        level = Minecraft.getInstance().level;
        expires = level == null ? 0 : level.getGameTime() + ACTIVE_PERSIST_TICKS;
    }

    public static void clear() {
        hint = StructureHintPayload.EMPTY;
        expires = 0;
    }

    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        level = null;
    }

    public static boolean satisfied(BlockState state, String expected) {
        return switch (expected) {
            case "block.minecraft.air" -> state.isAir();
            case "block.rematter.reactor_casing" -> state.is(ModContent.REACTOR_CASING.get());
            case "block.rematter.reactor_glass" -> state.is(ModContent.REACTOR_GLASS.get())
                    || state.is(ModContent.REACTOR_CASING.get()) || state.is(ModContent.MACHINE_PORT.get());
            case "block.rematter.machine_port" -> state.is(ModContent.MACHINE_PORT.get());
            default -> {
                if (state.getBlock() instanceof IndustrialModuleBlock module) {
                    yield ("block.rematter." + module.kind().name().toLowerCase(Locale.ROOT)).equals(expected);
                }
                if (state.getBlock() instanceof MachineBlock block) {
                    yield ("block.rematter." + block.kind().id()).equals(expected);
                }
                yield false;
            }
        };
    }

    public static int colorFor(String expected, boolean primary) {
        if (primary) return UiPalette.STRUCTURE_HINT;
        return switch (expected) {
            case "block.minecraft.air" -> UiPalette.STRUCTURE_HINT_CLEAR;
            case "block.rematter.reactor_casing" -> UiPalette.STRUCTURE_HINT_SHELL;
            case "block.rematter.reactor_glass" -> UiPalette.STRUCTURE_HINT_GLASS;
            case "block.rematter.machine_port" -> UiPalette.STRUCTURE_HINT_PORT;
            case "block.rematter.mixing_basin" -> UiPalette.moduleAccent(IndustrialModuleKind.MIXING_BASIN);
            case "block.rematter.separation_unit" -> UiPalette.moduleAccent(IndustrialModuleKind.SEPARATION_UNIT);
            case "block.rematter.grinding_unit" -> UiPalette.moduleAccent(IndustrialModuleKind.GRINDING_UNIT);
            case "block.rematter.field_coil" -> UiPalette.moduleAccent(IndustrialModuleKind.FIELD_COIL);
            case "block.rematter.reaction_chamber" -> UiPalette.moduleAccent(IndustrialModuleKind.REACTION_CHAMBER);
            default -> UiPalette.STRUCTURE_HINT;
        };
    }

    @SubscribeEvent public static void extract(ExtractLevelRenderStateEvent event) {
        var boxes = new ArrayList<WireBox>();
        if (hint.present() && hint.showOverlay() && level == event.getLevel() && level.getGameTime() <= expires
                && hint.position().getCenter().distanceToSqr(event.getCamera().position()) < 128 * 128) {
            var cam = event.getCamera().position();
            boolean primaryAssigned = false;
            for (var entry : hint.entries()) {
                if (level.hasChunkAt(entry.position()) && satisfied(level.getBlockState(entry.position()), entry.expected())) {
                    continue;
                }
                boolean primary = !primaryAssigned;
                primaryAssigned = true;
                int color = colorFor(entry.expected(), primary);
                float width = primary ? 2.5F : "block.rematter.reactor_casing".equals(entry.expected())
                        || "block.rematter.reactor_glass".equals(entry.expected()) ? 1.5F : 2.0F;
                boxes.add(new WireBox(new AABB(entry.position()).inflate(primary ? 0.008 : -0.02)
                        .move(-cam.x, -cam.y, -cam.z), color, width));
                if ("up".equals(entry.orientation()) || "down".equals(entry.orientation())) {
                    double y0 = "up".equals(entry.orientation()) ? 0.02 : 0.86;
                    double y1 = "up".equals(entry.orientation()) ? 0.14 : 0.98;
                    var p = entry.position();
                    boxes.add(new WireBox(new AABB(p.getX() + 0.15, p.getY() + y0, p.getZ() + 0.15,
                            p.getX() + 0.85, p.getY() + y1, p.getZ() + 0.85).move(-cam.x, -cam.y, -cam.z), color, 2.0F));
                }
            }
            if (!boxes.isEmpty() && hint.hasBounds()) {
                var min = hint.minBounds();
                var max = hint.maxBounds();
                boxes.addFirst(new WireBox(new AABB(min.getX(), min.getY(), min.getZ(),
                        max.getX() + 1, max.getY() + 1, max.getZ() + 1).inflate(0.015)
                        .move(-cam.x, -cam.y, -cam.z), UiPalette.STRUCTURE_BOUNDS, 1.5F));
            }
        }
        event.getRenderState().setRenderData(KEY, new RenderHint(List.copyOf(boxes)));
    }

    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event) {
        var extracted = event.getLevelRenderState().getRenderData(KEY);
        if (extracted == null || extracted.boxes().isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(), (pose, vertices) -> {
            for (var wire : extracted.boxes()) {
                var box = wire.box();
                for (int axis = 0; axis < 3; axis++) {
                    for (int first = 0; first < 2; first++) {
                        for (int second = 0; second < 2; second++) {
                            float[] a = {(float) box.minX, (float) box.minY, (float) box.minZ};
                            float[] b = {(float) box.minX, (float) box.minY, (float) box.minZ};
                            float[] high = {(float) box.maxX, (float) box.maxY, (float) box.maxZ};
                            int u = (axis + 1) % 3, v = (axis + 2) % 3;
                            if (first == 1) a[u] = b[u] = high[u];
                            if (second == 1) a[v] = b[v] = high[v];
                            b[axis] = high[axis];
                            var normal = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
                            vertices.addVertex(pose, a[0], a[1], a[2]).setColor(wire.color()).setNormal(pose, normal).setLineWidth(wire.width());
                            vertices.addVertex(pose, b[0], b[1], b[2]).setColor(wire.color()).setNormal(pose, normal).setLineWidth(wire.width());
                        }
                    }
                }
            }
        });
    }
}
