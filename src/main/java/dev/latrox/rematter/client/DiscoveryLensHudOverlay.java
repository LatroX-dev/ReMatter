package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.FluidProfile;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.content.ConfigurationCardItem;
import dev.latrox.rematter.content.MatterWrenchItem;
import dev.latrox.rematter.content.SecurityCardItem;
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
 * Compact field spectrometer HUD shown while a Discovery Lens is active (in hand or equipped in Curios).
 * Inspects targeted blocks, fluids, Primal ore signatures, and harvestable atmospheric gases.
 */
public final class DiscoveryLensHudOverlay {
    private static final int MAX_CARD_WIDTH = 236;
    private static final int MIN_CARD_WIDTH = 136;
    private static final int MAX_TRACE_LINES = 5;
    private static final int MAX_ATMO_LINES = 4;
    private static final int PAD = 6;
    private static final int BADGE_COLUMN = 20;
    private static final int HEADER_HEIGHT = 18;
    private static final int TEXT_ROW_HEIGHT = 11;
    private static final int TRACE_ROW_HEIGHT = 18;

    private sealed interface Row permits HeaderRow, TextRow, TraceRow {
        int height();
        int width(Font font);
    }

    private record HeaderRow(String text, int color) implements Row {
        @Override public int height() { return HEADER_HEIGHT; }
        @Override public int width(Font font) { return BADGE_COLUMN + font.width(text) + 4; }
    }

    private record TextRow(String text, int color) implements Row {
        @Override public int height() { return TEXT_ROW_HEIGHT; }
        @Override public int width(Font font) { return font.width(text) + 4; }
    }

    private record TraceRow(DiscoveryLensInspector.KnownTrace trace, String text, int color) implements Row {
        @Override public int height() { return TRACE_ROW_HEIGHT; }
        @Override public int width(Font font) { return BADGE_COLUMN + font.width(text) + 4; }
    }

    private DiscoveryLensHudOverlay() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) return;
        ItemStack activeLens = DiscoveryLensState.activeHudLens(mc.player);
        if (activeLens.isEmpty()) return;

        boolean discoveryLens = activeLens.is(PrimalContent.DISCOVERY_LENS.get());
        ChemistryRegistry chemistry = ClientChemistry.current();
        List<Row> rows = new ArrayList<>(10);
        rows.add(new HeaderRow(
                Component.translatable(discoveryLens
                        ? "hud.rematter.discovery_lens.title"
                        : "hud.rematter.spectrometer_lens.title").getString(),
                discoveryLens ? UiPalette.PRIMAL_LIGHT : UiPalette.ACCENT_BRIGHT));

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
                        rows.add(new TextRow(fluidState.getFluidType().getDescription().getString(), UiPalette.TEXT));
                        var report = DiscoveryLensInspector.inspectCurrent(
                                chemistry,
                                fluidId,
                                true,
                                fluidProfile.amount(),
                                false,
                                fluidProfile.matter());
                        appendReportRows(rows, report);
                        handledTarget = true;
                    }
                }
            }
        }

        if (!handledTarget && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir()) {
                rows.add(new TextRow(state.getBlock().getName().getString(), UiPalette.TEXT));
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                var item = state.getBlock().asItem();
                String itemId = item != Items.AIR ? BuiltInRegistries.ITEM.getKey(item).toString() : blockId;
                if (ReservedSubstances.primalItem(itemId) || ReservedSubstances.primalItem(blockId)) {
                    rows.add(new TextRow(
                            Component.translatable("hud.rematter.discovery_lens.primal_signature").getString(),
                            UiPalette.PRIMAL_LIGHT));
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
                        appendReportRows(rows, report);
                    } else {
                        rows.add(new TextRow(
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
            rows.add(new TextRow(
                    Component.translatable("hud.rematter.discovery_lens.atmosphere").getString(),
                    UiPalette.GAS));
            if (sources.isEmpty()) {
                rows.add(new TextRow(
                        Component.translatable("hud.rematter.discovery_lens.atmosphere_none").getString(),
                        UiPalette.MUTED));
            } else {
                var bestBySubstance = new java.util.LinkedHashMap<dev.latrox.rematter.chemistry.SubstanceKey, Long>();
                for (var entry : sources) {
                    for (var trace : entry.knownYields()) {
                        bestBySubstance.merge(trace.key(), trace.amount(), Math::max);
                    }
                }
                List<DiscoveryLensInspector.KnownTrace> uniqueYields = bestBySubstance.entrySet().stream()
                        .map(e -> new DiscoveryLensInspector.KnownTrace(e.getKey(), e.getValue()))
                        .sorted(java.util.Comparator.comparingLong(DiscoveryLensInspector.KnownTrace::amount)
                                .reversed()
                                .thenComparing(DiscoveryLensInspector.KnownTrace::key))
                        .toList();
                int shown = 0;
                for (var trace : uniqueYields) {
                    if (shown >= MAX_ATMO_LINES) {
                        rows.add(new TextRow(
                                Component.translatable("hud.rematter.discovery_lens.more_traces",
                                        uniqueYields.size() - shown).getString(),
                                UiPalette.MUTED));
                        break;
                    }
                    rows.add(traceRow(trace));
                    shown++;
                }
            }
        }

        Font font = mc.font;
        int contentWidth = MIN_CARD_WIDTH;
        int contentHeight = 0;
        for (Row row : rows) {
            contentWidth = Math.max(contentWidth, Math.min(MAX_CARD_WIDTH, row.width(font)));
            contentHeight += row.height();
        }
        int cardWidth = contentWidth + PAD * 2;
        int cardHeight = contentHeight + PAD * 2 - 1;

        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        boolean wrenchAlsoHeld = isWrenchOverlayActive(mainHand, offHand);
        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        int x = Math.min(guiW / 2 + 12, guiW - cardWidth - 6);
        int maxY = Math.max(6, guiH - cardHeight - 74); // Keep clear of the actionbar overlay and hotbar.
        int desiredY = wrenchAlsoHeld ? guiH / 2 - cardHeight - 8 : guiH / 2 - cardHeight / 2;
        int y = Math.clamp(desiredY, 6, maxY);

        ScreenChrome.panel(graphics, x, y, cardWidth, cardHeight);
        ItemStack lensIcon = new ItemStack(activeLens.getItem());
        int rowY = y + PAD;
        for (Row row : rows) {
            switch (row) {
                case HeaderRow header -> {
                    graphics.fakeItem(lensIcon, x + PAD, rowY);
                    ScreenChrome.text(graphics, font, header.text(), x + PAD + BADGE_COLUMN, rowY + 4,
                            contentWidth - BADGE_COLUMN, header.color());
                }
                case TextRow text -> ScreenChrome.text(graphics, font, text.text(), x + PAD, rowY + 1,
                        contentWidth, text.color());
                case TraceRow trace -> {
                    SubstanceIcon.draw(graphics, x + PAD, rowY, trace.trace().key());
                    ScreenChrome.text(graphics, font, trace.text(), x + PAD + BADGE_COLUMN, rowY + 4,
                            contentWidth - BADGE_COLUMN, trace.color());
                }
            }
            rowY += row.height();
        }
    }

    private static void appendReportRows(List<Row> rows, DiscoveryLensInspector.SampleReport report) {
        if (report.fluidMillibuckets()) {
            rows.add(new TextRow(
                    Component.translatable("hud.rematter.discovery_lens.fluid_batch",
                            Quantities.number(report.batchCount())).getString(),
                    UiPalette.MUTED));
        } else if (report.batchCount() > 1) {
            rows.add(new TextRow(
                    Component.translatable("hud.rematter.discovery_lens.item_batch",
                            Quantities.number(report.batchCount())).getString(),
                    UiPalette.MUTED));
        }
        int count = 0;
        for (var trace : report.knownTraces()) {
            if (count >= MAX_TRACE_LINES) {
                int remaining = report.knownTraces().size() - count;
                rows.add(new TextRow(
                        Component.translatable("hud.rematter.discovery_lens.more_traces", remaining).getString(),
                        UiPalette.MUTED));
                break;
            }
            rows.add(traceRow(trace));
            count++;
        }
        switch (report.synthesisStatus()) {
            case SYNTHESIS_UNLOCKED -> rows.add(new TextRow(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_unlocked").getString(),
                    UiPalette.ACCENT));
            case SYNTHESIS_LOCKED -> rows.add(new TextRow(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_locked").getString(),
                    UiPalette.AMBER));
            case DECOMPOSITION_ONLY -> rows.add(new TextRow(
                    Component.translatable("hud.rematter.discovery_lens.decomposition_only").getString(),
                    UiPalette.MUTED));
            case NONE -> {}
        }
    }

    private static TraceRow traceRow(DiscoveryLensInspector.KnownTrace trace) {
        return new TraceRow(
                trace,
                DiscoveryLensTooltip.formatLabel(trace).getString(),
                DiscoveryLensTooltip.phaseColor(trace.key().phase()));
    }

    private static boolean isWrenchOverlayActive(ItemStack mainHand, ItemStack offHand) {
        return mainHand.getItem() instanceof MatterWrenchItem
                || mainHand.getItem() instanceof ConfigurationCardItem
                || mainHand.getItem() instanceof SecurityCardItem
                || offHand.getItem() instanceof MatterWrenchItem
                || offHand.getItem() instanceof ConfigurationCardItem
                || offHand.getItem() instanceof SecurityCardItem;
    }
}
