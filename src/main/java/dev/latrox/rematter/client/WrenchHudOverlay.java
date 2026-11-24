package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ConfigurationCardData;
import dev.latrox.rematter.content.ConfigurationCardItem;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachinePortBlockEntity;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MatterCableBlock;
import dev.latrox.rematter.content.MatterWrenchItem;
import dev.latrox.rematter.content.PortSides;
import dev.latrox.rematter.content.SecurityCardItem;
import dev.latrox.rematter.content.SecurityMode;
import dev.latrox.rematter.content.WrenchInteractions;
import dev.latrox.rematter.content.WrenchMode;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.network.WrenchDiagnosticPayload;
import dev.latrox.rematter.network.WrenchDiagnosticQueryPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Compact ReMatter HUD diagnostic overlay shown when holding a Matter Wrench or Configuration Card.
 * Combines zero-latency client port face inspection with throttled server-authoritative diagnostics.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class WrenchHudOverlay {
    private static final int QUERY_INTERVAL_TICKS = 10;
    private static final long MAX_DIAGNOSTIC_AGE_MS = 1_600L;
    private static final int MAX_CARD_WIDTH = 168;
    private static final int MIN_CARD_WIDTH = 112;

    private static @Nullable BlockPos lastQueriedPos;
    private static @Nullable Direction lastQueriedFace;
    private static int ticksSinceQuery;

    private record Line(String text, int color) {}

    private WrenchHudOverlay() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWrenchDiagnostics.clear();
        lastQueriedPos = null;
        lastQueriedFace = null;
        ticksSinceQuery = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
            lastQueriedPos = null;
            lastQueriedFace = null;
            return;
        }
        ItemStack tool = activeTool(mc.player.getMainHandItem(), mc.player.getOffhandItem());
        if (tool.isEmpty() || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            lastQueriedPos = null;
            lastQueriedFace = null;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!WrenchInteractions.wrenchable(state)) {
            lastQueriedPos = null;
            lastQueriedFace = null;
            return;
        }
        Direction face = hit.getDirection();
        boolean targetChanged = !pos.equals(lastQueriedPos) || face != lastQueriedFace;
        ticksSinceQuery++;
        if (targetChanged || ticksSinceQuery >= QUERY_INTERVAL_TICKS) {
            lastQueriedPos = pos.immutable();
            lastQueriedFace = face;
            ticksSinceQuery = 0;
            ClientPacketDistributor.sendToServer(new WrenchDiagnosticQueryPayload(lastQueriedPos, face));
        }
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) return;
        ItemStack tool = activeTool(mc.player.getMainHandItem(), mc.player.getOffhandItem());
        if (tool.isEmpty()) return;

        List<Line> lines = new ArrayList<>(6);
        lines.add(toolHeader(tool));

        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (WrenchInteractions.wrenchable(state)) {
                lines.add(new Line(state.getBlock().getName().getString(), UiPalette.TEXT));
                Line sideLine = localSideLine(mc.level.getBlockEntity(pos), state, hit.getDirection());
                if (sideLine != null) lines.add(sideLine);

                WrenchDiagnosticPayload diag = ClientWrenchDiagnostics.current(MAX_DIAGNOSTIC_AGE_MS);
                if (diag != null && diag.pos().equals(pos)) {
                    appendServerDiagnostics(lines, state, diag);
                }
            }
        }

        Font font = mc.font;
        int contentWidth = MIN_CARD_WIDTH;
        for (Line line : lines) {
            contentWidth = Math.max(contentWidth, Math.min(MAX_CARD_WIDTH, font.width(line.text()) + 4));
        }
        int pad = 5;
        int rowHeight = 10;
        int cardWidth = contentWidth + pad * 2;
        int cardHeight = lines.size() * rowHeight + pad * 2 - 1;

        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        int x = Math.min(guiW / 2 + 12, guiW - cardWidth - 6);
        int y = Math.min(guiH / 2 + 8, guiH - cardHeight - 6);

        ScreenChrome.panel(graphics, x, y, cardWidth, cardHeight);
        int textY = y + pad;
        for (Line line : lines) {
            ScreenChrome.text(graphics, font, line.text(), x + pad, textY, contentWidth, line.color());
            textY += rowHeight;
        }
    }

    private static ItemStack activeTool(ItemStack mainHand, ItemStack offHand) {
        if (isTool(mainHand)) return mainHand;
        if (isTool(offHand)) return offHand;
        return ItemStack.EMPTY;
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof MatterWrenchItem
                || stack.getItem() instanceof ConfigurationCardItem
                || stack.getItem() instanceof SecurityCardItem;
    }

    private static Line toolHeader(ItemStack tool) {
        if (tool.getItem() instanceof MatterWrenchItem) {
            WrenchMode mode = MatterWrenchItem.mode(tool);
            int color = switch (mode) {
                case PORTS -> UiPalette.ACCENT;
                case ROTATE -> UiPalette.AMBER;
                case DISMANTLE -> UiPalette.EMBER;
            };
            String label = Component.translatable("screen.rematter.wrench_hud.mode", mode.label()).getString();
            return new Line(label, color);
        }
        if (tool.getItem() instanceof SecurityCardItem) {
            SecurityMode mode = SecurityCardItem.mode(tool);
            int color = switch (mode) {
                case PUBLIC -> UiPalette.ACCENT;
                case TRUSTED -> UiPalette.PORT_ENERGY_INPUT;
                case PRIVATE -> UiPalette.EMBER;
            };
            String label = Component.translatable("screen.rematter.wrench_hud.security_card", mode.label()).getString();
            return new Line(label, color);
        }
        var data = tool.get(ConfigurationCardData.TYPE.get());
        String stateKey = data == null ? "screen.rematter.wrench_hud.card_blank" : "screen.rematter.wrench_hud.card_ready";
        return new Line(Component.translatable(stateKey).getString(), data == null ? UiPalette.MUTED : UiPalette.ACCENT);
    }

    private static @Nullable Line localSideLine(
            @Nullable BlockEntity be, BlockState state, Direction hitFace) {
        if (be instanceof MachineBlockEntity machine && state.hasProperty(MachineBlock.FACING)) {
            if (!MachinePortMode.configurable(machine.kind())) return null;
            Direction front = state.getValue(MachineBlock.FACING);
            var side = MachinePortMode.Side.local(front, hitFace);
            if (PortSides.closed(machine.kind(), side) != null) {
                return new Line(
                        Component.translatable("screen.rematter.wrench_hud.face_closed", side.label()).getString(),
                        UiPalette.MUTED);
            }
            return new Line(
                    Component.translatable("port.rematter.side_mode", side.label(), machine.portMode(side).label()).getString(),
                    UiPalette.LIQUID);
        }
        if (be instanceof EnergyCubeBlockEntity cube && state.hasProperty(EnergyCubeBlock.FACING)) {
            Direction front = state.getValue(EnergyCubeBlock.FACING);
            var side = MachinePortMode.Side.local(front, hitFace);
            if (!PortSides.energyCube(side)) {
                return new Line(
                        Component.translatable("screen.rematter.wrench_hud.face_closed", side.label()).getString(),
                        UiPalette.MUTED);
            }
            return new Line(
                    Component.translatable("port.rematter.side_mode", side.label(), cube.sideMode(side).label()).getString(),
                    UiPalette.LIQUID);
        }
        if (be instanceof MachinePortBlockEntity port) {
            return new Line(
                    Component.translatable("screen.rematter.wrench_hud.port_mode", port.mode().label()).getString(),
                    UiPalette.LIQUID);
        }
        return null;
    }

    private static void appendServerDiagnostics(
            List<Line> lines, BlockState state, WrenchDiagnosticPayload diag) {
        if (diag.locked()) {
            lines.add(new Line(Component.translatable("screen.rematter.wrench_hud.locked").getString(), UiPalette.EMBER));
            return;
        }
        if (state.getBlock() instanceof MatterCableBlock) {
            lines.add(new Line(
                    Component.translatable(
                            "screen.rematter.wrench_hud.cable_rates",
                            diag.matterTypes(),
                            diag.transferRate()).getString(),
                    UiPalette.MUTED));
            if (diag.networkNodes() > 0) {
                lines.add(new Line(
                        Component.translatable(
                                "screen.rematter.wrench_hud.network",
                                diag.networkNodes(),
                                diag.networkCells()).getString(),
                        UiPalette.ACCENT));
            }
            return;
        }
        if (diag.linkState() > 0) {
            String key = switch (diag.linkState()) {
                case 2 -> "screen.rematter.wrench_hud.link_paired";
                case 3 -> "screen.rematter.wrench_hud.link_inactive";
                default -> "screen.rematter.wrench_hud.link_unpaired";
            };
            int color = diag.linkState() == 2 ? UiPalette.ACCENT : UiPalette.WARNING;
            String text = diag.linkState() == 1
                    ? Component.translatable(key).getString()
                    : Component.translatable(key, diag.linkDistance()).getString();
            lines.add(new Line(text, color));
            return;
        }
        if (diag.threshold() >= 0) {
            String key = diag.redstoneActive()
                    ? "screen.rematter.wrench_hud.emitter_active"
                    : "screen.rematter.wrench_hud.emitter_idle";
            lines.add(new Line(
                    Component.translatable(key, diag.matterUsed(), diag.threshold()).getString(),
                    diag.redstoneActive() ? UiPalette.ACCENT : UiPalette.MUTED));
            return;
        }
        if (!diag.status().isEmpty()) {
            String statusKey = "status.rematter." + diag.status().toLowerCase(Locale.ROOT);
            Component statusLabel = Component.translatableWithFallback(statusKey, diag.status());
            int statusColor = diag.status().equals("ready") || diag.status().equals("active") || diag.status().equals("transferred")
                    ? UiPalette.ACCENT
                    : UiPalette.WARNING;
            lines.add(new Line(
                    Component.translatable("screen.rematter.wrench_hud.status", statusLabel).getString(),
                    statusColor));
        }
        if (diag.energyCapacity() > 0) {
            lines.add(new Line(
                    Component.translatable("screen.rematter.wrench_hud.energy", diag.energy(), diag.energyCapacity()).getString(),
                    UiPalette.MUTED));
        }
        if (diag.matterCapacity() > 0) {
            lines.add(new Line(
                    Component.translatable(
                            "screen.rematter.wrench_hud.storage",
                            diag.matterUsed(),
                            diag.matterCapacity(),
                            diag.matterTypes()).getString(),
                    UiPalette.MUTED));
        }
        if (diag.networkNodes() > 0) {
            lines.add(new Line(
                    Component.translatable(
                            "screen.rematter.wrench_hud.network",
                            diag.networkNodes(),
                            diag.networkCells()).getString(),
                    UiPalette.MUTED));
        }
    }
}
