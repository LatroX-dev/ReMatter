package dev.latrox.rematter.client;

import dev.latrox.rematter.content.IndustrialBlueprint;
import dev.latrox.rematter.content.IndustrialStructure;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MachineSettings;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.ModSounds;
import dev.latrox.rematter.content.PortSides;
import dev.latrox.rematter.content.RedstoneMode;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.network.MachineActionPayload;
import dev.latrox.rematter.network.MachineConfigurationPayload;
import dev.latrox.rematter.network.ReleaseHeldOrderPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Compact settings content shared by the container tab and its small standalone adapters. */
final class MachineSettingsPanel {
    enum Section { GENERAL, STRUCTURE, SIDES, PRODUCTION, ACCESS }
    private static final int CONTENT_Y = 24, ROW = 23;
    private static final long[] RESERVE_PRESETS = {0L, 1_000L, 10_000L, 50_000L, 250_000L, 1_000_000L};
    private final MachineMenu menu;
    private final Minecraft minecraft;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final int left, top, width;
    private final EnumMap<Section, List<AbstractWidget>> groups = new EnumMap<>(Section.class);
    private final EnumMap<Section, PanelButton> tabs = new EnumMap<>(Section.class);
    private SideDiagram diagram;
    private DeviceGeneralPanel generalPanel;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private Section section;
    private List<MachineSnapshot.Amount> products = List.of();
    private List<String> gases = List.of();
    private MachineSettings lastServerSettings;
    private EditBox target, reserveField;
    private PanelButton apply, product, gasCycle, vent, reservePreset, reserveApply, automation, autoEject, jobs, redstone, backend, release;
    private PanelButton previewToggle, shellStyleToggle, blueprintBuild, blueprintReset, portDown, portUp, widthDown, widthUp, depthDown, depthUp, heightDown, heightUp;
    private final PanelButton[] modDown = new PanelButton[3];
    private final PanelButton[] modUp = new PanelButton[3];
    private int productIndex, gasIndex, accessHintY, visibleTabCount;
    private boolean visible = true, dirty, updating, reserveDirty, updatingReserve;
    private Component error = Component.empty();

    private record ModuleSlot(IndustrialBlueprint.Parameter parameter, String blockId, ItemStack icon, String benefitKey) {}

    MachineSettingsPanel(MachineMenu menu, Minecraft minecraft, Font font, Consumer<AbstractWidget> addWidget,
            Runnable rebuildHost, int left, int top, int width) {
        this.menu = menu; this.minecraft = minecraft; this.font = font; this.addWidget = addWidget;
        this.left = left; this.top = top; this.width = width;
        for (var value : Section.values()) groups.put(value, new ArrayList<>());
        this.section = menu.unformedIndustrial() ? Section.STRUCTURE : Section.GENERAL;
    }

    void initWidgets() {
        widgets.clear(); tabs.clear(); groups.values().forEach(List::clear);
        for (var value : Section.values()) {
            var tab = button(tr("settings.section." + value.name().toLowerCase(java.util.Locale.ROOT)),
                    left, top, 18, 16, b -> setSection(value));
            tab.setTooltip(Tooltip.create(tr("settings.section_hint." + value.name().toLowerCase(java.util.Locale.ROOT))));
            tab.iconOnly = true;
            tab.icon = switch (value) {
                case GENERAL -> UiIcon.GENERAL;
                case STRUCTURE -> UiIcon.STRUCTURE;
                case SIDES -> UiIcon.SIDES;
                case PRODUCTION -> UiIcon.TARGET;
                case ACCESS -> UiIcon.ACCESS;
            };
            tab.selected = value == section; tabs.put(value, tab);
        }

        // Structure blueprint controls (only shown on Industrial Reactor & Industrial Preparator).
        int row0Y = top + 19, row1Y = top + 37;
        previewToggle = grouped(Section.STRUCTURE, button(Component.empty(), left, row0Y, 20, 16, b -> {
            if (menu.structurePreviewEnabled()) ClientStructureHints.clear();
            sendMenu(MachineMenu.TOGGLE_STRUCTURE_PREVIEW);
        }));
        previewToggle.icon = UiIcon.LOCATE;
        previewToggle.iconOnly = true;
        shellStyleToggle = grouped(Section.STRUCTURE, button(Component.empty(), left + 22, row0Y, 20, 16, b -> {}));
        shellStyleToggle.allowRightClick = true;
        shellStyleToggle.inputAction = input -> {
            boolean reverse = input instanceof net.minecraft.client.input.MouseButtonEvent mbe && mbe.button() == 1;
            sendBlueprintAction(
                    reverse ? MachineActionPayload.Action.BLUEPRINT_STEP_DOWN : MachineActionPayload.Action.BLUEPRINT_STEP_UP,
                    IndustrialBlueprint.Parameter.SHELL_STYLE.name(), 1);
        };
        int buildW = Math.max(60, width - 134);
        blueprintBuild = grouped(Section.STRUCTURE, button(tr("blueprint.build"), left + 44, row0Y, buildW, 16,
                b -> sendBlueprintAction(MachineActionPayload.Action.BLUEPRINT_BUILD, "", 0)));
        blueprintBuild.icon = UiIcon.STRUCTURE;
        int portBase = left + width - 68;
        portDown = stepperButton(portBase, row0Y, 13, 16, false, () -> IndustrialBlueprint.Parameter.PORTS);
        portUp = stepperButton(portBase + 31, row0Y, 13, 16, true, () -> IndustrialBlueprint.Parameter.PORTS);
        blueprintReset = grouped(Section.STRUCTURE, button(tr("blueprint.reset"), left + width - 20, row0Y, 20, 16,
                b -> sendBlueprintAction(MachineActionPayload.Action.BLUEPRINT_RESET, "", 0)));
        blueprintReset.icon = UiIcon.CANCEL;
        blueprintReset.iconOnly = true;

        int dimGap = 4;
        int dimSlot = (width - dimGap * 2) / 3;
        int stepW = 14;
        int dimBoxW = dimSlot - stepW * 2;
        widthDown = stepperButton(left, row1Y, stepW, 16, false, () -> IndustrialBlueprint.Parameter.WIDTH);
        widthUp = stepperButton(left + stepW + dimBoxW, row1Y, stepW, 16, true, () -> IndustrialBlueprint.Parameter.WIDTH);
        int dStart = left + dimSlot + dimGap;
        depthDown = stepperButton(dStart, row1Y, stepW, 16, false, () -> IndustrialBlueprint.Parameter.DEPTH);
        depthUp = stepperButton(dStart + stepW + dimBoxW, row1Y, stepW, 16, true, () -> IndustrialBlueprint.Parameter.DEPTH);
        int hStart = left + width - dimSlot;
        heightDown = stepperButton(hStart, row1Y, stepW, 16, false, () -> IndustrialBlueprint.Parameter.HEIGHT);
        heightUp = stepperButton(hStart + stepW + dimBoxW, row1Y, stepW, 16, true, () -> IndustrialBlueprint.Parameter.HEIGHT);

        int modRight = left + width;
        for (int i = 0; i < 3; i++) {
            int rowIndex = i;
            int rowY = top + 55 + i * 18;
            modDown[i] = stepperButton(modRight - 56, rowY, 14, 16, false, () -> moduleParam(rowIndex));
            modUp[i] = stepperButton(modRight - 14, rowY, 14, 16, true, () -> moduleParam(rowIndex));
        }

        // The sides are a diagram of their own: it decides per side whether there is anything to click.
        diagram = new SideDiagram(new SideDiagram.Host() {
            @Override public SideDiagram.Look look(MachinePortMode.Side side) { return sideLook(side); }
            @Override public void step(MachinePortMode.Side side, boolean forward) {
                sendMenu((forward ? MachineMenu.CYCLE_PORT : MachineMenu.CYCLE_PORT_BACK) + side.ordinal());
            }
            @Override public boolean editable() { return canEdit(); }
        }, font, left, top + CONTENT_Y, widget -> { widgets.add(widget); addWidget.accept(widget); });
        target = new EditBox(font, left, top + 27, width - 26, 16, tr("stock_target"));
        target.setMaxLength(13); target.setValue("0"); target.setFilter(value -> value.matches("[0-9]*"));
        target.setTextColor(UiPalette.TEXT); target.setHint(Component.literal("0"));
        target.setResponder(value -> { if (!updating) { dirty = true; error = Component.empty(); } });
        register(target, Section.PRODUCTION);
        apply = grouped(Section.PRODUCTION, button(tr("apply"), left + width - 24, top + 27, 24, 16, b -> saveTarget()));
        product = grouped(Section.PRODUCTION, button(Component.empty(), left, top + 44, width, 16, b -> {
            if (!products.isEmpty()) { productIndex = (productIndex + 1) % products.size(); dirty = true; error = Component.empty(); updateWidgets(); }
        }));
        gasCycle = grouped(Section.PRODUCTION, button(Component.literal("\u203a"), left, top + 61, 18, 16, b -> {
            if (!gases.isEmpty()) { gasIndex = (gasIndex + 1) % gases.size(); updateWidgets(); }
        }));
        vent = grouped(Section.PRODUCTION, button(Component.empty(), left + 22, top + 61, width - 22, 16, b -> toggleVent()));
        reserveField = new EditBox(font, left, top + 88, width - 78, 16, tr("settings.network_reserve"));
        reserveField.setMaxLength(13); reserveField.setValue("0"); reserveField.setFilter(value -> value.matches("[0-9]*"));
        reserveField.setTextColor(UiPalette.TEXT); reserveField.setHint(Component.literal("0"));
        reserveField.setResponder(value -> { if (!updatingReserve) { reserveDirty = true; error = Component.empty(); } });
        register(reserveField, Section.PRODUCTION);
        reservePreset = grouped(Section.PRODUCTION, button(Component.empty(), left + width - 74, top + 88, 48, 16, b -> cycleReservePreset()));
        reserveApply = grouped(Section.PRODUCTION, button(tr("apply"), left + width - 24, top + 88, 24, 16, b -> saveReserve()));
        automation = grouped(Section.ACCESS, button(Component.empty(), left, top + 20, width, 16, b -> sendMenu(3)));
        autoEject = grouped(Section.ACCESS, button(Component.empty(), left, top + 20, width, 16, b -> sendMenu(MachineMenu.TOGGLE_AUTO_EJECT)));
        jobs = grouped(Section.ACCESS, button(Component.empty(), left, top + 38, width, 16, b -> sendMenu(8)));
        redstone = grouped(Section.ACCESS, button(Component.empty(), left, top + 56, width, 16, b -> sendMenu(MachineMenu.CYCLE_REDSTONE)));
        backend = grouped(Section.ACCESS, button(Component.empty(), left, top + 38, width, 16, b -> sendMenu(9)));
        release = grouped(Section.ACCESS, button(tr("settings.recover"), left, top + 56, width, 16,
                b -> minecraft.pushGuiLayer(new ReleaseConfirmation(menu, menu.snapshot().orders()))));
        apply.icon=UiIcon.CONFIRM;apply.iconOnly=true;reserveApply.icon=UiIcon.CONFIRM;reserveApply.iconOnly=true;
        product.icon=UiIcon.TARGET;gasCycle.icon=UiIcon.NEXT;gasCycle.iconOnly=true;
        gasCycle.setMessage(tr("settings.gas_next"));
        vent.icon=UiIcon.VENT;automation.icon=UiIcon.HOPPER;autoEject.icon=UiIcon.EJECT;jobs.icon=UiIcon.EXCHANGE;redstone.icon=UiIcon.REDSTONE;backend.icon=UiIcon.NETWORK_ONLINE;release.icon=UiIcon.EJECT;
        boolean hasAudio = ModSounds.workingSound(menu.kind()) != null;
        generalPanel = new DeviceGeneralPanel(font, widget -> { widgets.add(widget); addWidget.accept(widget); },
                left, top + 18, width, UiPalette.machineAccent(menu.kind()),
                Component.translatable("block.rematter." + menu.kind().id()),
                menu::securityMode, menu::canManageSecurity, this::canEdit,
                forward -> sendMenu(forward ? MachineMenu.CYCLE_SECURITY : MachineMenu.CYCLE_SECURITY_BACK),
                () -> menu.extras().has("customName") && menu.extras().get("customName").isJsonPrimitive()
                        ? menu.extras().get("customName").getAsString() : "",
                clean -> sendBlueprintAction(MachineActionPayload.Action.SET_CUSTOM_NAME, clean, 0),
                hasAudio ? menu::soundLevel : null,
                hasAudio ? forward -> sendMenu(forward ? MachineMenu.TOGGLE_SOUND_MUTE : MachineMenu.CYCLE_SOUND_BACK) : null);
        tick();
    }

    private PanelButton stepperButton(int x, int y, int w, int h, boolean up, java.util.function.Supplier<IndustrialBlueprint.@Nullable Parameter> paramSupplier) {
        var btn = grouped(Section.STRUCTURE, button(Component.literal(up ? "+" : "-"), x, y, w, h, b -> {}));
        btn.inputAction = input -> {
            var param = paramSupplier.get();
            if (param == null) return;
            int step = input.hasShiftDown() ? 2 : 1;
            sendBlueprintAction(up ? MachineActionPayload.Action.BLUEPRINT_STEP_UP : MachineActionPayload.Action.BLUEPRINT_STEP_DOWN, param.name(), step);
        };
        return btn;
    }

    private IndustrialBlueprint.@Nullable Parameter moduleParam(int index) {
        var modules = moduleSlots();
        return index >= 0 && index < modules.size() ? modules.get(index).parameter() : null;
    }

    private List<ModuleSlot> moduleSlots() {
        if (menu.kind() == MachineKind.TRANSMUTATION_REACTOR) {
            return List.of(
                    new ModuleSlot(IndustrialBlueprint.Parameter.CHAMBERS, "particle_emitter",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.PARTICLE_EMITTER).get()), "module_room"),
                    new ModuleSlot(IndustrialBlueprint.Parameter.COILS, "field_coil",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.FIELD_COIL).get()), "module_field"));
        }
        if (menu.kind() == MachineKind.PRIMAL_REACTOR) {
            return List.of(
                    new ModuleSlot(IndustrialBlueprint.Parameter.CHAMBERS, "primal_condenser_core",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.PRIMAL_CONDENSER_CORE).get()), "module_room"),
                    new ModuleSlot(IndustrialBlueprint.Parameter.COILS, "particle_emitter",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.PARTICLE_EMITTER).get()), "module_field"));
        }
        if (menu.kind() == MachineKind.ENERGY_MATRIX) {
            return List.of(
                    new ModuleSlot(IndustrialBlueprint.Parameter.CHAMBERS, "energy_matrix_cell",
                            new ItemStack(ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.BASIC).get()), "module_matrix_capacity"),
                    new ModuleSlot(IndustrialBlueprint.Parameter.COILS, "energy_matrix_provider",
                            new ItemStack(ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.BASIC).get()), "module_matrix_transfer"));
        }
        if (menu.kind() == MachineKind.ENERGY_REACTOR) {
            return List.of(
                    new ModuleSlot(IndustrialBlueprint.Parameter.CHAMBERS, "reactor_fuel_core",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.REACTOR_FUEL_CORE).get()), "module_reactor_output"),
                    new ModuleSlot(IndustrialBlueprint.Parameter.COILS, "reactor_induction_coil",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.REACTOR_INDUCTION_COIL).get()), "module_reactor_transfer"));
        }
        if (IndustrialStructure.usesReactorModules(menu.kind())) {
            return List.of(
                    new ModuleSlot(IndustrialBlueprint.Parameter.CHAMBERS, "reaction_chamber",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.REACTION_CHAMBER).get()), "module_room"),
                    new ModuleSlot(IndustrialBlueprint.Parameter.COILS, "field_coil",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.FIELD_COIL).get()), "module_field"));
        }
        if (menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR) {
            return List.of(
                    new ModuleSlot(IndustrialBlueprint.Parameter.GRINDING, "grinding_unit",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.GRINDING_UNIT).get()), "module_capacity"),
                    new ModuleSlot(IndustrialBlueprint.Parameter.MIXING, "mixing_basin",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.MIXING_BASIN).get()), "module_capacity"),
                    new ModuleSlot(IndustrialBlueprint.Parameter.SEPARATION, "separation_unit",
                            new ItemStack(ModContent.INDUSTRIAL_MODULES.get(dev.latrox.rematter.content.IndustrialModuleKind.SEPARATION_UNIT).get()), "module_precision"));
        }
        return List.of();
    }

    private static int moduleCount(IndustrialBlueprint bp, IndustrialBlueprint.Parameter param) {
        return switch (param) {
            case CHAMBERS -> bp.chambers();
            case COILS -> bp.coils();
            case GRINDING -> bp.grinding();
            case MIXING -> bp.mixing();
            case SEPARATION -> bp.separation();
            default -> 0;
        };
    }

    IndustrialBlueprint currentBlueprint() {
        if (!IndustrialStructure.isIndustrial(menu.kind())) {
            throw new IllegalStateException("Not an industrial controller");
        }
        var extras = menu.extras();
        if (extras.has("blueprint") && extras.get("blueprint").isJsonObject()) {
            try {
                return IndustrialBlueprint.fromJson(menu.kind(), extras.getAsJsonObject("blueprint"));
            } catch (RuntimeException ignored) {}
        }
        var s = menu.snapshot().structure();
        if (s.formed() && s.width() >= 3 && s.depth() >= 3 && s.height() >= IndustrialStructure.height(menu.kind())) {
            return new IndustrialBlueprint(menu.kind(), s.width(), s.height(), s.depth(),
                    IndustrialBlueprint.defaultColumn(s.width()), 2, s.chambers(), s.coils(), s.grinding(), s.mixing(), s.separation());
        }
        return IndustrialBlueprint.defaultFor(menu.kind());
    }

    boolean blueprintCustom() {
        return menu.extras().has("blueprintCustom") && menu.extras().get("blueprintCustom").getAsBoolean();
    }

    List<IndustrialBlueprint.PartRequirement> blueprintBom() {
        if (!IndustrialStructure.isIndustrial(menu.kind())) return List.of();
        var extras = menu.extras();
        if (extras.has("blueprintBom") && extras.get("blueprintBom").isJsonArray()) {
            var out = new ArrayList<IndustrialBlueprint.PartRequirement>();
            for (var el : extras.getAsJsonArray("blueprintBom")) {
                if (!el.isJsonObject()) continue;
                var obj = el.getAsJsonObject();
                try {
                    var mat = IndustrialStructure.Material.valueOf(obj.get("material").getAsString());
                    String blockKey = obj.get("blockKey").getAsString();
                    int req = obj.get("required").getAsInt();
                    int miss = obj.get("missing").getAsInt();
                    out.add(new IndustrialBlueprint.PartRequirement(mat, blockKey, req, miss));
                } catch (RuntimeException ignored) {}
            }
            if (!out.isEmpty()) return List.copyOf(out);
        }
        return currentBlueprint().billOfMaterials(List.of());
    }

    void setSection(Section next) {
        section = next;
        if (section != Section.PRODUCTION) {
            if (target != null) target.setFocused(false);
            if (reserveField != null) reserveField.setFocused(false);
        }
        updateWidgets();
    }
    Section section() { return section; }
    EditBox targetField() { return target; }
    void setVisible(boolean value) {
        visible = value;
        if (!value) {
            if (target != null) target.setFocused(false);
            if (reserveField != null) reserveField.setFocused(false);
        }
        updateWidgets();
    }
    boolean isEditing() {
        if (!visible) return false;
        if (section == Section.GENERAL && generalPanel != null && generalPanel.isTyping()) return true;
        if (section == Section.PRODUCTION) {
            return (target != null && target.visible && target.active && target.canConsumeInput())
                    || (reserveField != null && reserveField.visible && reserveField.active && reserveField.canConsumeInput());
        }
        return false;
    }

    /** Call before the container's inventory-key handler; text input is delivered by charTyped separately. */
    boolean handlesKey(KeyEvent event) {
        if (visible && section == Section.GENERAL && generalPanel != null && generalPanel.handlesKey(event)) return true;
        if (!isEditing() || event.isEscape() || event.key() == GLFW.GLFW_KEY_TAB) return false;
        boolean editingReserve = reserveField != null && reserveField.visible && reserveField.active && reserveField.canConsumeInput();
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (editingReserve) saveReserve(); else saveTarget();
        } else if (editingReserve) {
            reserveField.keyPressed(event);
        } else {
            target.keyPressed(event);
        }
        return true;
    }

    boolean handlesChar(CharacterEvent event) {
        return visible && section == Section.GENERAL && generalPanel != null && generalPanel.handlesChar(event);
    }

    void extractBackground(GuiGraphicsExtractor g) {
        if (visible && section == Section.GENERAL && generalPanel != null) {
            generalPanel.extractBackground(g);
        }
    }

    void tick() {
        if (target == null) return;
        var current = menu.settings().settings();
        var nextProducts = menu.snapshot().preview().outputs();
        boolean recipeChanged = !sameProducts(products, nextProducts);
        products = List.copyOf(nextProducts);
        if (recipeChanged) { dirty = false; productIndex = 0; }
        if (settingsReady() && (recipeChanged || !current.equals(lastServerSettings))) {
            if (!dirty) {
                updating = true; target.setValue(Long.toString(current.target())); updating = false;
                for (int i = 0; i < products.size(); i++) {
                    var value = products.get(i);
                    if (value.id().equals(current.targetSubstance()) && value.phase().equals(current.targetPhase())) productIndex = i;
                }
            }
            if (!reserveDirty && reserveField != null) {
                updatingReserve = true; reserveField.setValue(Long.toString(current.networkReserve())); updatingReserve = false;
            }
            lastServerSettings = current;
        }
        var gasIds = new TreeSet<>(current.ventedGases());
        products.stream().filter(value -> value.phase().equals("gas")).forEach(value -> gasIds.add(value.id()));
        gases = List.copyOf(gasIds);
        productIndex = Math.clamp(productIndex, 0, Math.max(0, products.size() - 1));
        gasIndex = Math.clamp(gasIndex, 0, Math.max(0, gases.size() - 1));
        updateWidgets();
    }

    private static boolean sameProducts(List<MachineSnapshot.Amount> a, List<MachineSnapshot.Amount> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).id().equals(b.get(i).id()) || !a.get(i).phase().equals(b.get(i).phase())) return false;
        return true;
    }
    private boolean hasStockTarget() {
        return menu.kind().processes() && menu.kind() != MachineKind.ANALYZER && !PreparationTier.manual(menu.kind());
    }
    private boolean hasNetworkReserve() {
        return hasStockTarget() || menu.kind().isEnergyReactor() || menu.kind() == MachineKind.PRIMAL_CONVERTER;
    }
    private boolean productionAvailable() {
        return hasNetworkReserve();
    }
    private boolean available(Section value) {
        return switch (value) {
            case STRUCTURE -> menu.unformedIndustrial();
            case SIDES -> MachinePortMode.configurable(menu.kind());
            case PRODUCTION -> productionAvailable();
            case ACCESS -> true;
            case GENERAL -> !menu.wireless();
        };
    }
    private boolean currentMenu() { return minecraft.player != null && minecraft.player.containerMenu == menu; }
    private boolean settingsReady() { return menu.settings().containerId() == menu.containerId; }
    private boolean canEdit() { return currentMenu() && !minecraft.player.isSpectator() && menu.snapshot().containerId() == menu.containerId; }
    private boolean physical() { return menu.kind().synthesis() || PreparationTier.isPreparation(menu.kind()); }
    private long targetLimit() {
        return physical() ? (menu.snapshot().preview().outputKind().equals("fluid")
                ? dev.latrox.rematter.content.MatterLimits.MACHINE_FLUID : 64) : MachineSettings.MAX_TARGET;
    }

    boolean singleStructurePage() {
        return menu.unformedIndustrial();
    }

    private void updateWidgets() {
        if (target == null) return;
        boolean singlePage = singleStructurePage();
        if (singlePage) {
            section = Section.STRUCTURE;
        } else if (!available(section)) {
            section = available(Section.GENERAL) ? Section.GENERAL
                    : available(Section.STRUCTURE) ? Section.STRUCTURE
                    : available(Section.SIDES) ? Section.SIDES
                    : available(Section.PRODUCTION) ? Section.PRODUCTION : Section.ACCESS;
        }
        var available = java.util.Arrays.stream(Section.values()).filter(this::available).toList();
        visibleTabCount = available.size();
        for (var value : Section.values()) {
            var tab = tabs.get(value); int index = available.indexOf(value);
            tab.visible = visible && !singlePage && index >= 0; tab.active = tab.visible; tab.selected = section == value;
            if (index >= 0) { tab.setX(left + index * 24); tab.setWidth(22); }
            for (var widget : groups.get(value)) { widget.visible = visible && section == value; widget.active = widget.visible && canEdit(); }
        }
        if (visible && section == Section.STRUCTURE && IndustrialStructure.isIndustrial(menu.kind())) {
            var bp = currentBlueprint();
            boolean edit = canEdit();
            boolean previewOn = menu.structurePreviewEnabled();
            previewToggle.selected = previewOn;
            previewToggle.setMessage(tr("blueprint.preview", tr(previewOn ? "on" : "off")));
            var previewTip = tr("blueprint.preview_hint").copy();
            if (menu.structureHint().present()) {
                for (var line : MachineScreen.structureHintDetails(menu.structureHint(), previewOn)) {
                    previewTip.append("\n").append(line);
                }
            }
            previewToggle.setTooltip(Tooltip.create(previewTip));
            var style = bp.shellStyle();
            shellStyleToggle.active = edit && !menu.snapshot().structure().formed();
            shellStyleToggle.selected = style != IndustrialBlueprint.ShellStyle.SIDES_GLASS;
            shellStyleToggle.setMessage(Component.empty());
            shellStyleToggle.setTooltip(Tooltip.create(
                    tr("blueprint.shell_style", tr("blueprint.shell_style." + style.id())).copy()
                            .append("\n").append(tr("blueprint.shell_style_desc." + style.id()))
                            .append("\n").append(tr("blueprint.shell_style_cycle"))));
            int missingParts = blueprintBom().stream().mapToInt(IndustrialBlueprint.PartRequirement::missing).sum();
            blueprintBuild.active = edit && !menu.snapshot().structure().formed() && missingParts > 0;
            blueprintBuild.setTooltip(Tooltip.create(tr("blueprint.build_hint")));
            blueprintReset.active = edit && blueprintCustom();
            blueprintReset.setTooltip(Tooltip.create(tr("blueprint.reset_hint")));

            configureStepPair(portDown, portUp, bp, IndustrialBlueprint.Parameter.PORTS, edit, tr("blueprint.step_hint"));
            configureStepPair(widthDown, widthUp, bp, IndustrialBlueprint.Parameter.WIDTH, edit, tr("blueprint.step_hint"));
            configureStepPair(depthDown, depthUp, bp, IndustrialBlueprint.Parameter.DEPTH, edit, tr("blueprint.step_hint"));
            configureStepPair(heightDown, heightUp, bp, IndustrialBlueprint.Parameter.HEIGHT, edit, tr("blueprint.step_hint"));

            var modules = moduleSlots();
            for (int i = 0; i < 3; i++) {
                boolean hasRow = i < modules.size();
                modDown[i].visible = modUp[i].visible = hasRow;
                if (hasRow) {
                    configureStepPair(modDown[i], modUp[i], bp, modules.get(i).parameter(), edit, tr("blueprint.step_hint"));
                }
            }
        }
        diagram.update(visible && section == Section.SIDES);
        if (generalPanel != null) {
            generalPanel.refresh(visible && section == Section.GENERAL, UiPalette.machineAccent(menu.kind()));
        }
        boolean prodVisible = visible && section == Section.PRODUCTION;
        boolean stockVisible = prodVisible && hasStockTarget();
        boolean productionEdit = stockVisible && canEdit() && settingsReady() && !menu.snapshot().networkJobs();
        target.visible = apply.visible = product.visible = stockVisible;
        target.active = productionEdit; apply.active = productionEdit;
        product.active = productionEdit && !physical() && products.size() > 1;
        product.setMessage(productLabel()); product.setTooltip(Tooltip.create(productLabel()));
        String count = menu.settings().targetStock() < 0 ? "?" : Quantities.number(menu.settings().targetStock());
        target.setTooltip(Tooltip.create(tr("target_help").copy().append("\n")
                .append(tr("target_limit", Quantities.number(targetLimit()))).append("\n")
                .append(tr(physical() ? "target_local_count" : "target_network_count", count, Quantities.number(menu.settings().settings().target())))));
        apply.setTooltip(Tooltip.create(tr("settings.apply_hint")));
        gasCycle.visible = vent.visible = stockVisible && !gases.isEmpty();
        gasCycle.active = gasCycle.visible && gases.size() > 1;
        vent.active = gasCycle.visible && productionEdit && menu.settings().mayVent();
        if (!gases.isEmpty()) {
            String gas = gases.get(gasIndex);
            boolean on = menu.settings().settings().ventedGases().contains(gas);
            gasCycle.setTooltip(Tooltip.create(tr("settings.gas_select", gasIndex + 1, gases.size())));
            vent.setMessage(tr("vent_setting", SubstanceNames.substance(gas, "gas", true), tr(on ? "on" : "off")));
            vent.setTooltip(Tooltip.create(tr("settings.vent_hint", SubstanceNames.substance(gas, "gas", true))));
            vent.selected = on;
        }
        int reserveRowY = top + (hasStockTarget() ? (gases.isEmpty() ? 76 : 88) : 33);
        boolean reserveVisible = prodVisible && hasNetworkReserve();
        boolean reserveEdit = reserveVisible && canEdit() && settingsReady();
        reserveField.visible = reservePreset.visible = reserveApply.visible = reserveVisible;
        reserveField.active = reservePreset.active = reserveApply.active = reserveEdit;
        reserveField.setY(reserveRowY);
        reservePreset.setY(reserveRowY);
        reserveApply.setY(reserveRowY);
        long curReserve = parseReserve();
        reserveField.setTooltip(Tooltip.create(tr("settings.network_reserve_hint")));
        reservePreset.setMessage(curReserve == 0L ? tr("off") : Component.literal(Quantities.compact(curReserve)));
        reservePreset.selected = curReserve > 0L;
        reservePreset.setTooltip(Tooltip.create(tr("settings.reserve_preset_hint")));
        reserveApply.setTooltip(Tooltip.create(tr("settings.apply_hint")));
        boolean access = visible && section == Section.ACCESS;
        boolean hasAutoEject = dev.latrox.rematter.content.MachineBlockEntity.supportsAutoEject(menu.kind());
        boolean hasJobs = menu.kind().supportsNetworkJobs();
        boolean hasRedstone = RedstoneMode.controllable(menu.kind());
        boolean hasInterface = menu.kind() == MachineKind.NETWORK_INTERFACE;
        int nextAccessY = top + 20;
        int accessStep = 18;
        automation.visible = access;
        automation.setY(nextAccessY);
        automation.setX(left);
        automation.setWidth(hasAutoEject ? (width - 2) / 2 : width);
        automation.setMessage(tr("settings.pipes", tr(menu.snapshot().automation() ? "settings.allowed" : "settings.blocked")));
        automation.setTooltip(Tooltip.create(tr("automation_hint"))); automation.selected = menu.snapshot().automation();
        autoEject.visible = access && hasAutoEject;
        autoEject.active = autoEject.visible && canEdit();
        if (autoEject.visible) {
            int half = (width - 2) / 2;
            autoEject.setX(left + half + 2);
            autoEject.setY(nextAccessY);
            autoEject.setWidth(width - half - 2);
            autoEject.setMessage(tr("settings.auto_eject", tr(menu.autoEject() ? "on" : "off")));
            autoEject.setTooltip(Tooltip.create(tr("auto_eject_hint")));
            autoEject.selected = menu.autoEject();
        }
        nextAccessY += accessStep;
        jobs.visible = access && hasJobs;
        jobs.active = jobs.visible && canEdit();
        if (jobs.visible) {
            jobs.setY(nextAccessY);
            jobs.setMessage(tr("settings.orders", tr(menu.snapshot().networkJobs() ? "on" : "off")));
            jobs.setTooltip(Tooltip.create(tr("network_jobs_hint"))); jobs.selected = menu.snapshot().networkJobs();
            nextAccessY += accessStep;
        }
        redstone.visible = access && hasRedstone;
        redstone.active = redstone.visible && canEdit();
        if (redstone.visible) {
            var mode = menu.redstoneMode();
            redstone.setY(nextAccessY);
            redstone.setMessage(tr("settings.redstone", mode.label()));
            redstone.setTooltip(Tooltip.create(mode.meaning()));
            redstone.selected = mode != RedstoneMode.IGNORED;
            nextAccessY += accessStep;
        }
        backend.visible = access && hasInterface;
        backend.active = backend.visible && canEdit();
        if (backend.visible) {
            backend.setY(nextAccessY);
            backend.setMessage(tr("settings.bridge", tr(switch (menu.snapshot().backend()) {
                case "ae2" -> "backend_ae2"; case "rs" -> "backend_rs"; default -> "backend_none";
            })));
            backend.icon=StatusText.icon(menu.snapshot().status(),menu.kind())==UiIcon.NETWORK_ONLINE?UiIcon.NETWORK_ONLINE:UiIcon.NETWORK_OFFLINE;
            backend.setTooltip(Tooltip.create(tr("backend_hint")));
            nextAccessY += accessStep;
        }
        release.visible = access && hasInterface;
        release.active = release.visible && canEdit() && !menu.snapshot().orders().held().isEmpty();
        if (release.visible) {
            release.setY(nextAccessY);
            release.setTooltip(Tooltip.create(tr("release_held_hint")));
            nextAccessY += accessStep;
        }
        accessHintY = nextAccessY + 2;
    }

    private static void configureStepPair(PanelButton down, PanelButton up, IndustrialBlueprint bp,
            IndustrialBlueprint.Parameter param, boolean edit, Component hint) {
        down.active = edit && !bp.adjust(param, -1).equals(bp);
        up.active = edit && !bp.adjust(param, 1).equals(bp);
        down.setTooltip(Tooltip.create(hint));
        up.setTooltip(Tooltip.create(hint));
    }

    private Component productLabel() {
        if (physical()) return tr(menu.snapshot().preview().outputKind().equals("fluid") ? "target_local_fluid" : "target_local_items");
        if (products.isEmpty()) return tr("target_choose_recipe");
        var value = products.get(productIndex);
        return tr("target_network", SubstanceNames.substance(value.id(), value.phase(), value.known()));
    }
    private void saveTarget() {
        if (!canEdit() || !settingsReady() || !hasStockTarget() || menu.snapshot().networkJobs()) return;
        try {
            long amount = Long.parseLong(target.getValue());
            if (amount > targetLimit()) throw new IllegalArgumentException("Target outside output scope");
            String id = "", phase = "";
            if (!physical() && amount > 0) {
                if (products.isEmpty()) { error = tr("target_choose_recipe"); return; }
                var value = products.get(productIndex); id = value.id(); phase = value.phase();
            }
            var cur = menu.settings().settings();
            sendSettings(new MachineSettings(amount, id, phase, cur.ventedGases(), cur.networkReserve()));
            dirty = false; error = Component.empty();
        } catch (IllegalArgumentException invalid) { error = tr("invalid_target"); }
    }
    private long parseReserve() {
        if (reserveField == null) return 0L;
        String raw = reserveField.getValue().strip();
        if (raw.isEmpty()) return 0L;
        try { return Math.clamp(Long.parseLong(raw), 0L, MachineSettings.MAX_TARGET); }
        catch (NumberFormatException ignored) { return 0L; }
    }
    private void cycleReservePreset() {
        if (!canEdit() || !settingsReady() || !hasNetworkReserve()) return;
        long cur = parseReserve();
        long next = RESERVE_PRESETS[0];
        for (long p : RESERVE_PRESETS) {
            if (p > cur) { next = p; break; }
        }
        updatingReserve = true;
        reserveField.setValue(Long.toString(next));
        updatingReserve = false;
        saveReserve();
    }
    private void saveReserve() {
        if (!canEdit() || !settingsReady() || !hasNetworkReserve()) return;
        String raw = reserveField.getValue().strip();
        try {
            long amount = raw.isEmpty() ? 0L : Long.parseLong(raw);
            if (amount < 0L || amount > MachineSettings.MAX_TARGET) throw new IllegalArgumentException("Reserve out of range");
            var cur = menu.settings().settings();
            if (hasStockTarget()) {
                sendSettings(new MachineSettings(cur.target(), cur.targetSubstance(), cur.targetPhase(), cur.ventedGases(), amount));
            } else {
                sendSettings(new MachineSettings(0L, "", "", Set.of(), amount));
            }
            reserveDirty = false; error = Component.empty();
            updateWidgets();
        } catch (IllegalArgumentException invalid) { error = tr("invalid_target"); }
    }
    private void toggleVent() {
        if (gases.isEmpty() || !canEdit() || !menu.settings().mayVent()) return;
        String gas = gases.get(gasIndex);
        if (menu.settings().settings().ventedGases().contains(gas)) changeVent(gas, false);
        else minecraft.pushGuiLayer(new VentConfirmation(menu, gas, () -> changeVent(gas, true)));
    }
    private void changeVent(String gas, boolean on) {
        if (!canEdit() || !menu.settings().mayVent()) return;
        var current = menu.settings().settings(); Set<String> values = new HashSet<>(current.ventedGases());
        if (on) values.add(gas); else values.remove(gas);
        try { sendSettings(new MachineSettings(current.target(), current.targetSubstance(), current.targetPhase(), values, current.networkReserve())); error = Component.empty(); }
        catch (IllegalArgumentException invalid) { error = tr("settings.target_cannot_vent"); }
    }
    private void sendSettings(MachineSettings next) {
        if (currentMenu()) ClientPacketDistributor.sendToServer(new MachineConfigurationPayload(menu.containerId, next));
    }
    private void sendBlueprintAction(MachineActionPayload.Action action, String target, long amount) {
        if (canEdit()) ClientPacketDistributor.sendToServer(new MachineActionPayload(menu.containerId, action, target, amount));
    }
    private void sendMenu(int action) {
        if (canEdit() && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!visible) return;
        if (singleStructurePage()) {
            var s = menu.snapshot();
            String diag = (s.status().equals("structure")
                    ? Component.translatable("status.rematter." + s.structure().diagnostic())
                    : StatusText.of(s.status(), s.orders().stale(), s.orders().quarantined())).getString();
            String header = tr("settings.section.structure").getString() + " \u00b7 " + diag;
            ScreenChrome.icon(g, UiIcon.STRUCTURE, left, top, UiPalette.machineAccent(menu.kind()));
            ScreenChrome.text(g, font, header, left + 18, top + 4, width - 18, UiPalette.TEXT);
        } else {
            int titleX = left + visibleTabCount * 24 + 2;
            int titleWidth = Math.max(0, left + width - titleX);
            ScreenChrome.text(g, font, tr("settings.section." + section.name().toLowerCase(java.util.Locale.ROOT)).getString(), titleX, top + 5, titleWidth, UiPalette.TEXT);
        }
        if (section == Section.STRUCTURE && IndustrialStructure.isIndustrial(menu.kind())) {
            extractStructureControls(g, mouseX, mouseY);
        } else if (section == Section.SIDES) {
            // Below the diagram: the side under the pointer, or the one changed last, in words.
            var side = diagram.shown(); var closed = PortSides.closed(menu.kind(), side); var mode = menu.portMode(side);
            int y = top + CONTENT_Y + SideDiagram.HEIGHT + 2;
            if (closed != null) {
                ScreenChrome.icon(g, UiIcon.LOCKED, left, y - 3, UiPalette.MUTED);
                ScreenChrome.text(g, font, closedName(closed).getString(), left + 18, y + 1, width - 18, UiPalette.MUTED);
                ScreenChrome.twoLines(g, font, closedHint(closed).getString(), left, y + 12, width, UiPalette.MUTED);
            } else {
                ScreenChrome.icon(g, UiIcon.port(mode), left, y - 3, mode.color());
                ScreenChrome.text(g, font, tr("settings.side_state", side.label(), mode.label()).getString(), left + 18, y + 1, width - 18, mode.color());
                ScreenChrome.twoLines(g, font, mode.meaning().getString(), left, y + 12, width, UiPalette.MUTED);
            }
        } else if (section == Section.PRODUCTION) {
            if (hasStockTarget()) {
                Component label = error.getString().isEmpty() ? tr("stock_target") : error;
                ScreenChrome.text(g, font, label.getString(), left, top + 18, width, error.getString().isEmpty() ? UiPalette.MUTED : UiPalette.WARNING);
                int reserveLabelY = top + (gases.isEmpty() ? 66 : 79);
                ScreenChrome.text(g, font, tr("settings.network_reserve").getString(), left, reserveLabelY, width, UiPalette.MUTED);
            } else {
                Component label = error.getString().isEmpty() ? tr("settings.network_reserve") : error;
                ScreenChrome.text(g, font, label.getString(), left, top + 22, width, error.getString().isEmpty() ? UiPalette.MUTED : UiPalette.WARNING);
                ScreenChrome.twoLines(g, font, tr("settings.network_reserve_hint").getString(), left, top + 56, width, UiPalette.MUTED);
            }
        } else if (section == Section.ACCESS && !jobs.visible && !backend.visible && accessHintY + 18 <= top + 102) {
            ScreenChrome.twoLines(g, font, tr(IndustrialStructure.isIndustrial(menu.kind())
                    ? "settings.industrial_ports" : "automation_hint").getString(), left, accessHintY, width, UiPalette.MUTED);
        } else if (section == Section.GENERAL && generalPanel != null) {
            generalPanel.extractLabels(g, 0, 0);
        }
    }

    private void extractStructureControls(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var bp = currentBlueprint();
        int row0Y = top + 19, row1Y = top + 37;
        int portBase = left + width - 68;
        int dimGap = 4;
        int dimSlot = (width - dimGap * 2) / 3;
        int stepW = 14;
        int dimBoxW = dimSlot - stepW * 2;
        int dStart = left + dimSlot + dimGap;
        int hStart = left + width - dimSlot;
        int modRight = left + width;

        // Shell fill style icon inside shellStyleToggle (left + 22, row0Y, 20x16)
        var pose = g.pose();
        var style = bp.shellStyle();
        var styleStack = new ItemStack(style == IndustrialBlueprint.ShellStyle.FULL_CASING
                ? ModContent.reactorCasing(menu.kind())
                : ModContent.reactorGlass(menu.kind()));
        pose.pushMatrix();
        pose.translate(left + 26F, row0Y + 2F);
        pose.scale(12F / 16F, 12F / 16F);
        g.fakeItem(styleStack, 0, 0);
        pose.popMatrix();
        if (style == IndustrialBlueprint.ShellStyle.SIDES_GLASS) {
            // Subtle top & bottom solid casing caps to distinguish Sides Glass from All Glass at a glance.
            g.fill(left + 26, row0Y + 2, left + 38, row0Y + 3, UiPalette.MUTED);
            g.fill(left + 26, row0Y + 13, left + 38, row0Y + 14, UiPalette.MUTED);
        }

        // Machine Port icon + count box in Row 0
        pose.pushMatrix();
        pose.translate(portBase - 14F, row0Y + 2F);
        pose.scale(12F / 16F, 12F / 16F);
        g.fakeItem(new ItemStack(ModContent.machinePort(menu.kind())), 0, 0);
        pose.popMatrix();
        centeredBox(g, portBase + 13, row0Y, 18, 16, Integer.toString(bp.ports()), UiPalette.TEXT);

        // Chamber dimensions in Row 1
        centeredBox(g, left + stepW, row1Y, dimBoxW, 16, tr("blueprint.dim_w", bp.width()).getString(), UiPalette.TEXT);
        centeredBox(g, dStart + stepW, row1Y, dimBoxW, 16, tr("blueprint.dim_d", bp.depth()).getString(), UiPalette.TEXT);
        centeredBox(g, hStart + stepW, row1Y, dimBoxW, 16, tr("blueprint.dim_h", bp.height()).getString(), UiPalette.TEXT);

        // Module rows (Rows 2..4)
        var modules = moduleSlots();
        int labelW = width - 77;
        for (int i = 0; i < modules.size(); i++) {
            var slot = modules.get(i);
            int rowY = top + 55 + i * 18;
            g.fakeItem(slot.icon(), left, rowY);
            Component blockName = Component.translatable("block.rematter." + slot.blockId());
            ScreenChrome.text(g, font, blockName.getString(), left + 19, rowY + 4, labelW, UiPalette.TEXT);
            int count = moduleCount(bp, slot.parameter());
            centeredBox(g, modRight - 42, rowY, 28, 16, Integer.toString(count), UiPalette.machineAccent(menu.kind()));
            if (inside(mouseX, mouseY, left, rowY, labelW + 19, 16) || inside(mouseX, mouseY, modRight - 42, rowY, 28, 16)) {
                g.setComponentTooltipForNextFrame(font, List.of(
                        blockName,
                        Component.translatable("screen.rematter.module_detail", count, blockName, tr(slot.benefitKey())),
                        tr("blueprint.auto_expand_hint")), mouseX, mouseY);
            }
        }
        if (modules.size() < 3 && !menu.kind().isEnergyMatrix() && !menu.kind().isEnergyReactor()) {
            int rowY = top + 55 + modules.size() * 18 + 4;
            ScreenChrome.text(g, font, tr("blueprint.mounts", bp.totalModules(), bp.slotCapacity()).getString(),
                    left + 2, rowY, width - 4, UiPalette.MUTED);
        }

        if (inside(mouseX, mouseY, portBase - 15, row0Y, 46, 16)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable(IndustrialStructure.expectedBlockKey(menu.kind(), IndustrialStructure.Material.PORT)),
                    tr("blueprint.ports_hint", bp.ports())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, left + stepW, row1Y, dimBoxW, 16)) {
            g.setComponentTooltipForNextFrame(font, List.of(tr("blueprint.width_detail", bp.width())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, dStart + stepW, row1Y, dimBoxW, 16)) {
            g.setComponentTooltipForNextFrame(font, List.of(tr("blueprint.depth_detail", bp.depth())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, hStart + stepW, row1Y, dimBoxW, 16)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("blueprint.height_detail", bp.height(), bp.laneRoom())), mouseX, mouseY);
        }
    }

    private void centeredBox(GuiGraphicsExtractor g, int x, int y, int w, int h, String value, int color) {
        ScreenChrome.inset(g, x, y, w, h);
        int textW = Math.min(w - 4, font.width(value));
        ScreenChrome.text(g, font, value, x + (w - textW) / 2, y + (h - 8) / 2, w - 4, color);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private PanelButton button(Component label, int x, int y, int w, int h, Button.OnPress action) {
        var button = new PanelButton(Button.builder(label, action).bounds(x, y, w, h), font, menu);
        widgets.add(button); addWidget.accept(button); return button;
    }
    private <T extends AbstractWidget> T grouped(Section group, T widget) { groups.get(group).add(widget); return widget; }
    private void register(AbstractWidget widget, Section group) { widgets.add(widget); groups.get(group).add(widget); addWidget.accept(widget); }
    private static Component tr(String key, Object... args) { return Component.translatable("screen.rematter." + key, args); }
    private static Component closedName(PortSides.Closed closed) {
        return Component.translatable("port.rematter.closed." + closed.name().toLowerCase(java.util.Locale.ROOT));
    }
    private static Component closedHint(PortSides.Closed closed) {
        return Component.translatable("port.rematter.closed_hint." + closed.name().toLowerCase(java.util.Locale.ROOT));
    }
    /** What the block shows on this side, and the words for it. */
    private SideDiagram.Look sideLook(MachinePortMode.Side side) {
        SideDiagram.Neighbor neighbor = inspectNeighbor(side);
        var closed = PortSides.closed(menu.kind(), side);
        if (closed != null) return new SideDiagram.Look(closed == PortSides.Closed.OPEN_TOP ? SideDiagram.Face.OPEN : SideDiagram.Face.PLAIN,
                UiPalette.MUTED, null, true, side.label().copy().append("\n").append(closedName(closed)).append("\n").append(closedHint(closed)), neighbor);
        var mode = menu.portMode(side);
        return new SideDiagram.Look(mode == MachinePortMode.DISABLED ? SideDiagram.Face.PLAIN : SideDiagram.Face.PLATE, mode.ring(), null, false,
                tr("settings.side_hint", side.label(), mode.label(), mode.meaning()), neighbor);
    }

    private SideDiagram.@Nullable Neighbor inspectNeighbor(MachinePortMode.Side side) {
        var extras = menu.extras();
        if (minecraft.level == null || !extras.has("blockX") || !extras.has("blockY") || !extras.has("blockZ")) return null;
        var pos = new net.minecraft.core.BlockPos(extras.get("blockX").getAsInt(), extras.get("blockY").getAsInt(), extras.get("blockZ").getAsInt());
        if (!minecraft.level.hasChunkAt(pos)) return null;
        var state = minecraft.level.getBlockState(pos);
        if (!state.hasProperty(dev.latrox.rematter.content.MachineBlock.FACING)) return null;
        return SideDiagram.inspectNeighbor(minecraft.level, pos, state.getValue(dev.latrox.rematter.content.MachineBlock.FACING), side);
    }

    static final class PanelButton extends Button {
        private final Font font; private final MachineMenu menu;
        private boolean selected, iconOnly, allowRightClick;
        private UiIcon icon;
        private @Nullable Consumer<InputWithModifiers> inputAction;
        PanelButton(Builder builder, Font font, MachineMenu menu) { super(builder); this.font = font; this.menu = menu; }
        @Override protected boolean isValidClickButton(MouseButtonInfo info) {
            return info.button() == 0 || allowRightClick && info.button() == 1;
        }
        @Override public void onPress(InputWithModifiers input) {
            if (inputAction != null) {
                inputAction.accept(input);
                return;
            }
            super.onPress(input);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            ScreenChrome.raised(g, x, y, w, h, isHoveredOrFocused() || selected, active);
            if (selected) g.fill(x + 1, y + 3, x + 3, y + h - 3, UiPalette.machineAccent(menu.kind()));
            int color=active?UiPalette.TEXT:UiPalette.MUTED;
            if(iconOnly) {
                ScreenChrome.icon(g,icon,x+(w-16)/2,y+(h-16)/2,selected&&active?UiPalette.machineAccent(menu.kind()):color);
                return;
            }
            String label=getMessage().getString();
            int labelWidth=Math.min(font.width(label),w-(icon==null?4:18));
            int gx=x+(w-labelWidth-(icon==null?0:16))/2;
            if(icon!=null)ScreenChrome.icon(g,icon,gx-2,y+(h-16)/2,selected&&active?UiPalette.machineAccent(menu.kind()):color);
            ScreenChrome.text(g,font,label,gx+(icon==null?0:16),y+(h-8)/2,labelWidth,color);
        }
    }

    private static final class VentConfirmation extends Screen {
        private final MachineMenu menu; private final String gas; private final Runnable accept;
        private int x, y;
        VentConfirmation(MachineMenu menu, String gas, Runnable accept) { super(tr("vent_title")); this.menu = menu; this.gas = gas; this.accept = accept; }
        @Override protected void init() {
            x = (width - 196) / 2; y = (height - 142) / 2;
            var yes=addRenderableWidget(new PanelButton(Button.builder(CommonComponents.GUI_YES, b -> { if (current()) accept.run(); onClose(); })
                    .bounds(x + 8, y + 116, 86, 18), font, menu));
            var cancel=addRenderableWidget(new PanelButton(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                    .bounds(x + 102, y + 116, 86, 18), font, menu));
            yes.icon=UiIcon.CONFIRM;cancel.icon=UiIcon.CANCEL;
        }
        private boolean current() { return minecraft.player != null && minecraft.player.containerMenu == menu; }
        @Override public void tick() { if (!current()) onClose(); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
            ScreenChrome.panel(g, x, y, 196, 142);
            ScreenChrome.icon(g,UiIcon.WARNING,x+6,y+5,UiPalette.WARNING);
            ScreenChrome.text(g,font,title.getString(),x+24,y+9,164,UiPalette.TEXT);
            ScreenChrome.text(g,font,SubstanceNames.substance(gas,"gas",true).getString(),x+8,y+25,180,UiPalette.GAS);
            int line = 0; for (var text : font.split(tr("settings.vent_warning"), 180)) {
                if (line == 6) break; g.text(font, text, x + 8, y + 43 + line++ * 10, UiPalette.WARNING, false);
            }
            super.extractRenderState(g, mx, my, partial);
        }
    }

    private static final class ReleaseConfirmation extends Screen {
        private static final int SHOWN = 3;
        private final MachineMenu menu; private final MachineSnapshot.OrdersView order;
        private final List<Component> lines = new ArrayList<>();
        private int x, y;
        ReleaseConfirmation(MachineMenu menu, MachineSnapshot.OrdersView order) {
            super(tr("release_title")); this.menu = menu; this.order = order;
            order.matter().forEach(value -> lines.add(SubstanceNames.substance(value.id(), value.phase(), value.known()).copy()
                    .append(" · ").append(Quantities.matter(Quantities.number(value.required())))));
            order.resources().forEach(value -> lines.add(value.kind().equals("fluid")
                    ? SubstanceNames.fluid(value.id()).copy().append(" · ").append(Quantities.fluid(Quantities.number(value.amount())))
                    : Component.translatable("gui.rematter.choice.items", Quantities.number(value.amount()), SubstanceNames.item(value.id()))));
        }
        @Override protected void init() {
            x = (width - 196) / 2; y = (height - 184) / 2;
            var confirm=addRenderableWidget(new PanelButton(Button.builder(tr("release_confirm"), b -> confirm()).bounds(x + 8, y + 158, 112, 18), font, menu));
            var cancel=addRenderableWidget(new PanelButton(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 124, y + 158, 64, 18), font, menu));
            confirm.icon=UiIcon.EJECT;cancel.icon=UiIcon.CANCEL;
        }
        private boolean current() { return minecraft.player != null && minecraft.player.containerMenu == menu && menu.snapshot().orders().held().equals(order.held()); }
        private void confirm() {
            if (current() && !minecraft.player.isSpectator() && !order.held().isEmpty())
                ClientPacketDistributor.sendToServer(new ReleaseHeldOrderPayload(menu.containerId, UUID.fromString(order.held())));
            onClose();
        }
        @Override public void tick() { if (!current()) onClose(); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
            ScreenChrome.panel(g, x, y, 196, 184);
            ScreenChrome.icon(g,UiIcon.WARNING,x+6,y+5,UiPalette.WARNING);
            ScreenChrome.text(g,font,title.getString(),x+24,y+9,164,UiPalette.TEXT);
            ScreenChrome.twoLines(g, font, tr("release_explain").getString(), x + 8, y + 28, 180, UiPalette.MUTED);
            for (int i = 0; i < Math.min(SHOWN, lines.size()); i++) ScreenChrome.text(g,font,lines.get(i).getString(),x+8,y+54+i*12,180,UiPalette.TEXT);
            if (lines.size() > SHOWN || !order.complete()) g.text(font, tr("release_more"), x + 8, y + 94, UiPalette.MUTED, false);
            int line = 0; for (var text : font.split(tr("release_warning"), 180)) {
                if (line == 5) break; g.text(font, text, x + 8, y + 106 + line++ * 10, UiPalette.WARNING, false);
            }
            super.extractRenderState(g, mx, my, partial);
        }
    }
}
