package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.EnergyReactorRecipes;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.network.MachineActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Zero-overlap Main view for the endgame Energy Reactor multiblock. */
final class EnergyReactorPanel {
    private final MachineMenu menu;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final Runnable toggleRunning;
    private final int left, top, width;
    private DarkButton crystalBtn, matterBtn, primalBtn, coolantBtn, fuelControlBtn, runBtn;
    private boolean visible;

    EnergyReactorPanel(MachineMenu menu, Font font, Consumer<AbstractWidget> addWidget,
                       Runnable toggleRunning, int left, int top, int width) {
        this.menu = menu;
        this.font = font;
        this.addWidget = addWidget;
        this.toggleRunning = toggleRunning;
        this.left = left;
        this.top = top;
        this.width = width;
    }

    void initWidgets() {
        int btnW = 64;
        int rowY = top + 44;
        crystalBtn = DarkButton.labelIcon(
                UiIcon.FUEL, tr("mode.crystal"), tr("mode.crystal_hint"),
                left, rowY, btnW, 16,
                b -> selectMode(EnergyReactorRecipes.FuelMode.CRYSTAL));
        crystalBtn.setAccent(EMBER);

        matterBtn = DarkButton.labelIcon(
                UiIcon.MATTER, tr("mode.matter"), tr("mode.matter_hint"),
                left + btnW + 4, rowY, btnW, 16,
                b -> selectMode(EnergyReactorRecipes.FuelMode.MATTER));
        matterBtn.setAccent(ACCENT);

        primalBtn = DarkButton.labelIcon(
                UiIcon.PRIMAL, tr("mode.primal"), tr("mode.primal_hint"),
                left + (btnW + 4) * 2, rowY, btnW, 16,
                b -> selectMode(EnergyReactorRecipes.FuelMode.PRIMAL));
        primalBtn.setAccent(PRIMAL);

        coolantBtn = DarkButton.labelIcon(
                UiIcon.LIQUID, tr("coolant_mode.auto"), tr("coolant_hint"),
                left, top + 63, 102, 16,
                b -> cycleCoolantMode());
        coolantBtn.setAccent(LIQUID);

        fuelControlBtn = DarkButton.labelIcon(
                UiIcon.MATTER, tr("matter_filter.auto"), tr("matter_filter_hint"),
                left + 106, top + 63, 94, 16,
                b -> handleFuelControl());
        fuelControlBtn.setAccent(ACCENT);

        runBtn = DarkButton.labelIcon(
                UiIcon.PAUSE, Component.translatable("screen.rematter.pause"),
                Component.translatable("screen.rematter.pause_hint"),
                left, top + 82, 76, 18,
                b -> toggleRunning.run());
        runBtn.setAccent(EMBER);

        addWidget.accept(crystalBtn);
        addWidget.accept(matterBtn);
        addWidget.accept(primalBtn);
        addWidget.accept(coolantBtn);
        addWidget.accept(fuelControlBtn);
        addWidget.accept(runBtn);
        tick();
    }

    private void selectMode(EnergyReactorRecipes.FuelMode mode) {
        ClientPacketDistributor.sendToServer(new MachineActionPayload(
                menu.containerId, MachineActionPayload.Action.SET_REACTOR_FUEL_MODE, mode.id(), 0));
    }

    private void cycleCoolantMode() {
        var cur = EnergyReactorRecipes.CoolantMode.load(extraString("reactorCoolantMode", "auto"));
        var all = EnergyReactorRecipes.CoolantMode.values();
        var next = all[(cur.ordinal() + 1) % all.length];
        ClientPacketDistributor.sendToServer(new MachineActionPayload(
                menu.containerId, MachineActionPayload.Action.SET_REACTOR_COOLANT_MODE, next.id(), 0));
    }

    private void handleFuelControl() {
        String mode = extraString("reactorFuelMode", "crystal");
        if ("matter".equals(mode)) {
            var cur = EnergyReactorRecipes.MatterFuelFilter.load(extraString("reactorMatterFilter", "auto"));
            var all = EnergyReactorRecipes.MatterFuelFilter.values();
            var next = all[(cur.ordinal() + 1) % all.length];
            ClientPacketDistributor.sendToServer(new MachineActionPayload(
                    menu.containerId, MachineActionPayload.Action.SET_REACTOR_MATTER_FILTER, next.id(), 0));
        } else if ("primal".equals(mode)) {
            ClientPacketDistributor.sendToServer(new MachineActionPayload(
                    menu.containerId, MachineActionPayload.Action.TOGGLE_REACTOR_NET_PRIMAL, "", 0));
        }
    }

    void setVisible(boolean value) {
        visible = value;
        for (var btn : List.of(crystalBtn, matterBtn, primalBtn, coolantBtn, fuelControlBtn, runBtn)) {
            if (btn != null) {
                btn.visible = value;
                btn.active = value;
            }
        }
        if (value) tick();
    }

    void tick() {
        if (crystalBtn == null) return;
        String mode = extraString("reactorFuelMode", "crystal");
        boolean running = extraBool("reactorRunning", menu.snapshot().running());
        boolean cooled = extraBool("reactorCooled", false);
        var coolantMode = EnergyReactorRecipes.CoolantMode.load(extraString("reactorCoolantMode", "auto"));
        var matterFilter = EnergyReactorRecipes.MatterFuelFilter.load(extraString("reactorMatterFilter", "auto"));
        boolean allowNetPrimal = extraBool("reactorAllowNetPrimal", true);
        long cores = Math.max(1L, extraLong("reactorFuelCores", 1L));
        long coils = extraLong("reactorInductionCoils", 0L);
        int coilBonusPct = (int) extraLong("reactorCoilBonusPct", 0L);

        crystalBtn.visible = crystalBtn.active = visible;
        matterBtn.visible = matterBtn.active = visible;
        primalBtn.visible = primalBtn.active = visible;
        coolantBtn.visible = coolantBtn.active = visible;
        runBtn.visible = runBtn.active = visible;

        crystalBtn.setSelected("crystal".equals(mode));
        matterBtn.setSelected("matter".equals(mode));
        primalBtn.setSelected("primal".equals(mode));

        crystalBtn.setTooltip(Tooltip.create(tr("mode.crystal_hint").copy()
                .append("\n").append(tr("rate_line", Quantities.compact(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE * cores)))));
        matterBtn.setTooltip(Tooltip.create(tr("mode.matter_hint").copy()
                .append("\n").append(tr("matter_recipes_detail"))));
        primalBtn.setTooltip(Tooltip.create(tr("mode.primal_hint").copy()
                .append("\n").append(tr("primal_rate_detail",
                        Quantities.number(EnergyReactorRecipes.PRIMAL_UR_PER_CORE_PER_TICK * cores),
                        Quantities.compact(EnergyReactorRecipes.PRIMAL_FE_PER_CORE * cores)))));

        int coolantBonusPct = (int) extraLong("reactorCoolantBonusPct", 0L);
        coolantBtn.setMessage(cooled && coolantBonusPct > 0
                ? tr("coolant_btn_active", coolantBonusPct, tr("coolant_mode." + coolantMode.id()))
                : tr("coolant_btn", tr("coolant_mode." + coolantMode.id())));
        coolantBtn.setSelected(cooled || coolantMode != EnergyReactorRecipes.CoolantMode.AUTO);
        coolantBtn.setTooltip(Tooltip.create(tr("coolant_title").copy()
                .append("\n").append(tr("coolant_mode_detail", tr("coolant_mode." + coolantMode.id())))
                .append("\n").append(tr("coolant_hint"))
                .append("\n").append(tr("modules_detail", extraLong("reactorFuelCores", 0L), coils, coilBonusPct))));

        boolean showControl = visible && ("matter".equals(mode) || "primal".equals(mode));
        fuelControlBtn.visible = fuelControlBtn.active = showControl;
        if ("matter".equals(mode)) {
            fuelControlBtn.setIcon(UiIcon.MATTER);
            fuelControlBtn.setAccent(ACCENT);
            fuelControlBtn.setMessage(tr("matter_filter." + matterFilter.id()));
            fuelControlBtn.setSelected(matterFilter != EnergyReactorRecipes.MatterFuelFilter.AUTO);
            fuelControlBtn.setTooltip(Tooltip.create(tr("matter_filter_title", tr("matter_filter." + matterFilter.id())).copy()
                    .append("\n").append(tr("matter_filter_hint"))
                    .append("\n").append(tr("matter_recipes_detail"))));
        } else if ("primal".equals(mode)) {
            fuelControlBtn.setIcon(UiIcon.PRIMAL);
            fuelControlBtn.setAccent(PRIMAL);
            fuelControlBtn.setMessage(tr(allowNetPrimal ? "primal_source.network" : "primal_source.local"));
            fuelControlBtn.setSelected(!allowNetPrimal);
            fuelControlBtn.setTooltip(Tooltip.create(tr("primal_source_title",
                    tr(allowNetPrimal ? "primal_source.network" : "primal_source.local")).copy()
                    .append("\n").append(tr("primal_source_hint"))));
        }

        runBtn.setMessage(Component.translatable("screen.rematter." + (running ? "pause" : "start")));
        runBtn.setIcon(running ? UiIcon.PAUSE : UiIcon.START);
        runBtn.setSelected(!running);
        runBtn.setTooltip(Tooltip.create(Component.translatable("screen.rematter.pause_hint")));
    }

    void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!visible) return;
        long energy = extraLong("reactorEnergy", 0L);
        long capacity = extraLong("reactorCapacity", 0L);
        long maxOut = extraLong("reactorMaxOutRate", 0L);
        long genRate = extraLong("reactorGenRate", 0L);
        long outRate = extraLong("reactorOutRate", 0L);
        int flux = (int) extraLong("reactorFlux", 0L);
        boolean cooled = extraBool("reactorCooled", false);
        String activeFuel = extraString("reactorActiveFuel", "");
        String activeCoolant = extraString("reactorActiveCoolant", "");
        long cores = extraLong("reactorFuelCores", 0L);
        long coils = extraLong("reactorInductionCoils", 0L);
        int coilBonusPct = (int) extraLong("reactorCoilBonusPct", 0L);
        int pctTenths = capacity > 0L ? (int) Math.clamp(Math.min(energy, capacity) * 1_000L / capacity, 0L, 1_000L) : 0;

        // Row 1 (top..top+17, w=200): 64-bit Reactor Energy Buffer Bar
        ScreenChrome.inset(g, left, top, width, 18);
        int innerW = width - 4;
        int fillW = capacity > 0L ? (int) Math.clamp(Math.min(energy, capacity) * innerW / capacity, 0L, innerW) : 0;
        if (fillW > 0) {
            int barCol = mix(EMBER, AMBER, pctTenths / 1000F * 0.6F);
            g.fill(left + 2, top + 2, left + 2 + fillW, top + 16, barCol);
            g.fill(left + 2, top + 2, left + 2 + fillW, top + 4, tint(barCol, 0.25F));
        }
        String barText = Quantities.compact(energy) + " / " + Quantities.compact(capacity) + " FE \u00b7 "
                + (pctTenths / 10) + "." + (pctTenths % 10) + "%";
        int barTw = Math.min(innerW, font.width(barText));
        ScreenChrome.text(g, font, barText, left + (width - barTw) / 2, top + 5, innerW, TEXT);

        // Row 2 (top+21..top+41): Slot 0 sits at left+16..left+34; Slot 12 sits at left+183..left+201.
        // Fuel marker icon at left..left+16, and Center Flux/Output Telemetry Inset at left+38..left+178 (w=140, h=20).
        String mode = extraString("reactorFuelMode", "crystal");
        ScreenChrome.icon(g, UiIcon.FUEL, left, top + 23, "crystal".equals(mode) ? EMBER : MUTED);

        int teleX = left + 38;
        int teleW = 140;
        int teleY = top + 21;
        ScreenChrome.inset(g, teleX, teleY, teleW, 20);
        String genText = "+" + Quantities.compact(genRate) + " FE/t";
        ScreenChrome.text(g, font, genText, teleX + 5, teleY + 3, 82, genRate > 0L ? ACCENT : MUTED);
        String fluxText = tr("flux_short", flux / 10).getString();
        int fluxTw = Math.min(50, font.width(fluxText));
        ScreenChrome.text(g, font, fluxText, teleX + teleW - 5 - fluxTw, teleY + 3, 50,
                flux >= 800 ? AMBER : flux > 0 ? EMBER : MUTED);
        int fluxBarW = teleW - 10;
        int fluxFill = Math.clamp(flux * fluxBarW / EnergyReactorRecipes.MAX_FLUX, 0, fluxBarW);
        g.fill(teleX + 5, teleY + 13, teleX + 5 + fluxBarW, teleY + 17, HIGHLIGHT);
        if (fluxFill > 0) {
            int fluxCol = cooled ? mix(EMBER, ACCENT, 0.45F) : mix(EMBER, AMBER, flux / 1000F);
            g.fill(teleX + 5, teleY + 13, teleX + 5 + fluxFill, teleY + 17, fluxCol);
        }

        // Row 3 (top+44..top+60): 3 Fuel Mode buttons (rendered by widgets)

        // Row 4 (top+63..top+79, w=200, h=16): Coolant Mode button (left..left+102) + Mode Filter button / Crystal Module inset (left+106..left+200)
        if ("crystal".equals(mode)) {
            ScreenChrome.inset(g, left + 106, top + 63, 94, 16);
            String modLine = tr("modules_short", cores, coils).getString();
            int modW = Math.min(86, font.width(modLine));
            ScreenChrome.text(g, font, modLine, left + 106 + (94 - modW) / 2, top + 67, 86, TEXT);
        }

        // Row 5 (top+82..top+99): Run/Pause button (w=76) + Status icon & label (left+80..left+200)
        String status = menu.snapshot().status();
        ScreenChrome.icon(g, StatusText.icon(status, menu.kind()), left + 80, top + 83, -1);
        int statusCol = status.equals("generating") ? EMBER
                : status.equals("energy_full") ? ACCENT
                : status.equals("paused") || status.equals("idle") ? MUTED : WARNING;
        ScreenChrome.text(g, font, StatusText.of(status, 0, 0).getString(), left + 97, top + 87, 103, statusCol);

        // Hover tooltips
        if (inside(mouseX, mouseY, left, top, width, 18)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("screen.rematter.energy_buffer", Quantities.number(energy), Quantities.number(capacity)));
            lines.add(tr("max_out_exact", Quantities.number(maxOut)));
            lines.add(tr("gen_out_exact", Quantities.number(genRate), Quantities.number(outRate)));
            lines.add(tr("modules_detail", cores, coils, coilBonusPct));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, teleX, teleY, teleW, 20)) {
            List<Component> lines = new ArrayList<>();
            lines.add(tr("flux_detail", flux / 10, coilBonusPct));
            lines.add(tr("gen_out_exact", Quantities.number(genRate), Quantities.number(outRate)));
            if (!activeFuel.isEmpty()) {
                lines.add(tr("active_fuel", formatFuel(activeFuel)));
            }
            if (cooled && !activeCoolant.isEmpty()) {
                int cBonus = (int) extraLong("reactorCoolantBonusPct", 20L);
                int cDisc = (int) extraLong("reactorCoolantDiscountPct", 15L);
                lines.add(tr("cooled_on", cBonus, cDisc, formatCoolant(activeCoolant)));
            }
            lines.add(tr("flux_hint"));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        } else if ("crystal".equals(mode) && inside(mouseX, mouseY, left + 106, top + 63, 94, 16)) {
            List<Component> lines = new ArrayList<>();
            lines.add(tr("modules_detail", cores, coils, coilBonusPct));
            lines.add(tr("rate_line", Quantities.compact(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE * Math.max(1L, cores))));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private static String formatCoolant(String id) {
        return switch (id) {
            case "rematter:water" -> SubstanceNames.substance("rematter:water", "liquid", true).getString();
            case "rematter:nitrogen" -> SubstanceNames.substance("rematter:nitrogen", "gas", true).getString();
            default -> id;
        };
    }

    private static Component formatFuel(String id) {
        return switch (id) {
            case "crystal" -> tr("mode.crystal");
            case "primal" -> tr("mode.primal");
            case "void_condensate" -> SubstanceNames.substance("rematter:void_condensate", "gas", true);
            case "uranium" -> SubstanceNames.substance("rematter:uranium", "solid", true);
            case "lithium_fusion" -> tr("fuel.lithium_fusion");
            default -> Component.literal(id);
        };
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private long extraLong(String key, long fallback) {
        var extras = menu.extras();
        return extras.has(key) ? extras.get(key).getAsLong() : fallback;
    }

    private boolean extraBool(String key, boolean fallback) {
        var extras = menu.extras();
        return extras.has(key) ? extras.get(key).getAsBoolean() : fallback;
    }

    private String extraString(String key, String fallback) {
        var extras = menu.extras();
        return extras.has(key) ? extras.get(key).getAsString() : fallback;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.energy_reactor." + key, args);
    }
}
