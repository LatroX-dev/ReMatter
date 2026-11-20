package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.FluidProfile;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.content.ConfigurationCardItem;
import dev.latrox.rematter.content.MatterWrenchItem;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.primal.PrimalContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Compact field spectrometer HUD shown when holding a Discovery Lens in either hand.
 * Inspects targeted blocks, fluids, Primal ore signatures, and harvestable atmospheric gases.
 */
public final class DiscoveryLensHudOverlay {
    private static final int MAX_CARD_WIDTH = 184;
    private static final int MIN_CARD_WIDTH = 120;
    private static final int MAX_TRACE_LINES = 5;

    private record Line(String text, int color) {}

    private DiscoveryLensHudOverlay() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) return;
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (!mainHand.is(PrimalContent.DISCOVERY_LENS.get()) && !offHand.is(PrimalContent.DISCOVERY_LENS.get())) {
            return;
        }

        ChemistryRegistry chemistry = ClientChemistry.current();
        List<Line> lines = new ArrayList<>(8);
        lines.add(new Line(Component.translatable("hud.rematter.discovery_lens.title").getString(), UiPalette.PRIMAL_LIGHT));

        double reach = mc.player.blockInteractionRange();
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        HitResult fluidPick = mc.player.pick(reach, partial, true);

        boolean handledTarget = false;
        if (fluidPick instanceof BlockHitResult fluidHit && fluidHit.getType() == HitResult.Type.BLOCK) {
            BlockPos fPos = fluidHit.getBlockPos();
            FluidState fluidState = mc.level.getFluidState(fPos);
            if (!fluidState.isEmpty()) {
                Fluid sourceFluid = fluidState.getType();
                if (sourceFluid instanceof net.minecraft.world.level.material.FlowingFluid flowing) {
                    sourceFluid = flowing.getSource();
                }
                if (sourceFluid != Fluids.EMPTY) {
                    String fluidId = BuiltInRegistries.FLUID.getKey(sourceFluid).toString();
                    FluidProfile fluidProfile = chemistry.fluidProfile(fluidId).orElse(null);
                    if (fluidProfile != null) {
                        lines.add(new Line(fluidState.getFluidType().getDescription().getString(), UiPalette.TEXT));
                        var report = DiscoveryLensInspector.inspectCurrent(
                                chemistry,
                                fluidId,
                                true,
                                fluidProfile.amount(),
                                false,
                                fluidProfile.matter());
                        appendReportLines(lines, report);
                        handledTarget = true;
                    }
                }
            }
        }

        if (!handledTarget && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir()) {
                lines.add(new Line(state.getBlock().getName().getString(), UiPalette.TEXT));
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                var item = state.getBlock().asItem();
                String itemId = item != Items.AIR ? BuiltInRegistries.ITEM.getKey(item).toString() : blockId;
                if (ReservedSubstances.primalItem(itemId) || ReservedSubstances.primalItem(blockId)) {
                    lines.add(new Line(
                            Component.translatable("hud.rematter.discovery_lens.primal_signature").getString(),
                            UiPalette.PRIMAL));
                } else {
                    MaterialProfile profile = chemistry.profile(itemId).or(() -> chemistry.profile(blockId)).orElse(null);
                    if (profile != null) {
                        var report = DiscoveryLensInspector.inspectCurrent(
                                chemistry,
                                profile.itemId(),
                                false,
                                profile.itemCount(),
                                false,
                                profile.matter());
                        appendReportLines(lines, report);
                    } else {
                        lines.add(new Line(
                                Component.translatable("hud.rematter.discovery_lens.no_profile").getString(),
                                UiPalette.MUTED));
                    }
                }
                handledTarget = true;
            }
        }

        if (!handledTarget) {
            String dimensionId = mc.level.dimension().identifier().toString();
            boolean skyVisible = mc.level.canSeeSky(mc.player.blockPosition().above());
            var sources = DiscoveryLensInspector.inspectAtmosphereCurrent(chemistry, dimensionId, skyVisible);
            lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.atmosphere").getString(),
                    UiPalette.GAS));
            if (sources.isEmpty()) {
                lines.add(new Line(
                        Component.translatable("hud.rematter.discovery_lens.atmosphere_none").getString(),
                        UiPalette.MUTED));
            } else {
                int shown = 0;
                for (var entry : sources) {
                    if (shown >= 3) break;
                    if (entry.knownYields().isEmpty() && entry.hiddenYieldCount() > 0) {
                        lines.add(new Line(
                                Component.translatable("hud.rematter.discovery_lens.unanalyzed").getString(),
                                UiPalette.WARNING));
                    } else {
                        for (var trace : entry.knownYields()) {
                            if (shown >= 3) break;
                            lines.add(new Line(formatTrace(trace), phaseColor(trace.key().phase())));
                            shown++;
                        }
                    }
                    shown = Math.max(shown, 1);
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

        boolean wrenchAlsoHeld = isWrenchOverlayActive(mainHand, offHand);
        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        int x = Math.min(guiW / 2 + 12, guiW - cardWidth - 6);
        int baseOffsetY = wrenchAlsoHeld ? 68 : 8;
        int y = Math.min(guiH / 2 + baseOffsetY, guiH - cardHeight - 6);

        ScreenChrome.panel(graphics, x, y, cardWidth, cardHeight);
        int textY = y + pad;
        for (Line line : lines) {
            ScreenChrome.text(graphics, font, line.text(), x + pad, textY, contentWidth, line.color());
            textY += rowHeight;
        }
    }

    private static void appendReportLines(List<Line> lines, DiscoveryLensInspector.SampleReport report) {
        if (report.unanalyzed()) {
            lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.unanalyzed").getString(),
                    UiPalette.WARNING));
            return;
        }
        if (report.fluidMillibuckets()) {
            lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.fluid_batch", Quantities.number(report.batchCount())).getString(),
                    UiPalette.MUTED));
        } else if (report.batchCount() > 1) {
            lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.item_batch", Quantities.number(report.batchCount())).getString(),
                    UiPalette.MUTED));
        }
        int count = 0;
        for (var trace : report.knownTraces()) {
            if (count >= MAX_TRACE_LINES) {
                int remaining = report.knownTraces().size() - count;
                lines.add(new Line(
                        Component.translatable("hud.rematter.discovery_lens.more_traces", remaining).getString(),
                        UiPalette.MUTED));
                break;
            }
            lines.add(new Line(formatTrace(trace), phaseColor(trace.key().phase())));
            count++;
        }
        if (report.hiddenTraceCount() > 0) {
            lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.unknown_traces", report.hiddenTraceCount()).getString(),
                    UiPalette.WARNING));
        }
        switch (report.synthesisStatus()) {
            case SYNTHESIS_UNLOCKED -> lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_unlocked").getString(),
                    UiPalette.ACCENT));
            case SYNTHESIS_LOCKED -> lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_locked").getString(),
                    UiPalette.AMBER));
            case DECOMPOSITION_ONLY -> lines.add(new Line(
                    Component.translatable("hud.rematter.discovery_lens.decomposition_only").getString(),
                    UiPalette.MUTED));
            case NONE -> {}
        }
    }

    private static String formatTrace(DiscoveryLensInspector.KnownTrace trace) {
        Component name = SubstanceNames.labelled(trace.key());
        Component phase = SubstanceNames.phase(trace.key().phase());
        return Component.translatable(
                "hud.rematter.discovery_lens.trace_line",
                name,
                phase,
                Quantities.number(trace.amount())).getString();
    }

    private static int phaseColor(Phase phase) {
        return switch (phase) {
            case SOLID -> UiPalette.ACCENT;
            case LIQUID -> UiPalette.LIQUID;
            case GAS -> UiPalette.GAS;
        };
    }

    private static boolean isWrenchOverlayActive(ItemStack mainHand, ItemStack offHand) {
        return mainHand.getItem() instanceof MatterWrenchItem
                || mainHand.getItem() instanceof ConfigurationCardItem
                || offHand.getItem() instanceof MatterWrenchItem
                || offHand.getItem() instanceof ConfigurationCardItem;
    }
}
