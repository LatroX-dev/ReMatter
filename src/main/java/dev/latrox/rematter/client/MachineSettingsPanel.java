package dev.latrox.rematter.client;

import dev.latrox.rematter.content.IndustrialBlueprint;
import dev.latrox.rematter.content.IndustrialStructure;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MachineSettings;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.MachineSoundLevel;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.PortSides;
import dev.latrox.rematter.content.RedstoneMode;
import dev.latrox.rematter.content.SecurityMode;
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
    enum Section { STRUCTURE, SIDES, PRODUCTION, ACCESS }
    private static final int CONTENT_Y = 24, ROW = 23;
    private final MachineMenu menu;
    private final Minecraft minecraft;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final int left, top, width;
    private final EnumMap<Section, List<AbstractWidget>> groups = new EnumMap<>(Section.class);
    private final EnumMap<Section, PanelButton> tabs = new EnumMap<>(Section.class);
    private SideDiagram diagram;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private Section section;
    private List<MachineSnapshot.Amount> products = List.of();
    private List<String> gases = List.of();
    private MachineSettings lastServerSettings;
    private EditBox target;
    private PanelButton apply, product, gasCycle, vent, automation, autoEject, jobs, redstone, backend, release, soundMute, securityButton;
    private PanelButton previewToggle, blueprintBuild, blueprintReset, portDown, portUp, widthDown, widthUp, depthDown, depthUp, heightDown, heightUp;
    private final PanelButton[] modDown = new PanelButton[3];
    private final PanelButton[] modUp = new PanelButton[3];
    private int productIndex, gasIndex, accessHintY, visibleTabCount;
    private boolean visible = true, dirty, updating;
    private Component error = Component.empty();

    private record ModuleSlot(IndustrialBlueprint.Parameter parameter, String blockId, ItemStack icon, String benefitKey) {}

    MachineSettingsPanel(MachineMenu menu, Minecraft minecraft, Font font, Consumer<AbstractWidget> addWidget,
            Runnable rebuildHost, int left, int top, int width) {
        this.menu = menu; this.minecraft = minecraft; this.font = font; this.addWidget = addWidget;
        this.left = left; this.top = top; this.width = width;
        for (var value : Section.values()) groups.put(value, new ArrayList<>());
        this.section = menu.unformedIndustrial() ? Section.STRUCTURE
                : MachinePortMode.configurable(menu.kind()) ? Section.SIDES
                : productionAvailable() ? Section.PRODUCTION : Section.ACCESS;
    }

    void initWidgets() {
        widgets.clear(); tabs.clear(); groups.values().forEach(List::clear);
        for (var value : Section.values()) {
            var tab = button(tr("settings.section." + value.name().toLowerCase(java.util.Locale.ROOT)),
                    left, top, 18, 16, b -> setSection(value));
            tab.setTooltip(Tooltip.create(tr("settings.section_hint." + value.name().toLowerCase(java.util.Locale.ROOT))));
            tab.iconOnly = true;
            tab.icon = switch (value) {
                case STRUCTURE -> UiIcon.STRUCTURE;
                case SIDES -> UiIcon.SIDES;
                case PRODUCTION -> UiIcon.TARGET;
                case ACCESS -> UiIcon.ACCESS;
            };
            tab.selected = value == section; tabs.put(value, tab);
        }
        soundMute = button(Component.empty(), left + width - 20, top, 20, 16, b -> {});
        soundMute.iconOnly = true;
        soundMute.allowRightClick = true;
        soundMute.inputAction = input -> {
            boolean backward = input.hasShiftDown() || input instanceof MouseButtonEvent click && click.button() == 1;
            sendMenu(backward ? MachineMenu.CYCLE_SOUND_BACK : MachineMenu.TOGGLE_SOUND_MUTE);
        };
        securityButton = button(Component.empty(), left + width - 42, top, 20, 16, b -> {});
        securityButton.iconOnly = true;
        securityButton.allowRightClick = true;
        securityButton.inputAction = input -> {
            if (!menu.canManageSecurity()) return;
            boolean backward = input.hasShiftDown() || input instanceof MouseButtonEvent click && click.button() == 1;
            sendMenu(backward ? MachineMenu.CYCLE_SECURITY_BACK : MachineMenu.CYCLE_SECURITY);
        };

        // Structure blueprint controls (only shown on Industrial Reactor & Industrial Preparator).
        int row0Y = top + 19, row1Y = top + 37;
        previewToggle = grouped(Section.STRUCTURE, button(Component.empty(), left, row0Y, 20, 16, b -> {
            if (menu.structurePreviewEnabled()) ClientStructureHints.clear();
            sendMenu(MachineMenu.TOGGLE_STRUCTURE_PREVIEW);
        }));
        previewToggle.icon = UiIcon.LOCATE;
        previewToggle.iconOnly = true;
        blueprintBuild = grouped(Section.STRUCTURE, button(tr("blueprint.build"), left + 22, row0Y, 74, 16,
                b -> sendBlueprintAction(MachineActionPayload.Action.BLUEPRINT_BUILD, "", 0)));
        blueprintBuild.icon = UiIcon.STRUCTURE;
        portDown = stepperButton(left + 113, row0Y, 13, 16, false, () -> IndustrialBlueprint.Parameter.PORTS);
        portUp = stepperButton(left + 144, row0Y, 13, 16, true, () -> IndustrialBlueprint.Parameter.PORTS);
        blueprintReset = grouped(Section.STRUCTURE, button(tr("blueprint.reset"), left + 161, row0Y, 19, 16,
                b -> sendBlueprintAction(MachineActionPayload.Action.BLUEPRINT_RESET, "", 0)));
        blueprintReset.icon = UiIcon.CANCEL;
        blueprintReset.iconOnly = true;

        widthDown = stepperButton(left, row1Y, 13, 16, false, () -> IndustrialBlueprint.Parameter.WIDTH);
        widthUp = stepperButton(left + 45, row1Y, 13, 16, true, () -> IndustrialBlueprint.Parameter.WIDTH);
        depthDown = stepperButton(left + 61, row1Y, 13, 16, false, () -> IndustrialBlueprint.Parameter.DEPTH);
        depthUp = stepperButton(left + 106, row1Y, 13, 16, true, () -> IndustrialBlueprint.Parameter.DEPTH);
        heightDown = stepperButton(left + 122, row1Y, 13, 16, false, () -> IndustrialBlueprint.Parameter.HEIGHT);
        heightUp = stepperButton(left + 167, row1Y, 13, 16, true, () -> IndustrialBlueprint.Parameter.HEIGHT);

        for (int i = 0; i < 3; i++) {
            int rowIndex = i;
            int rowY = top + 55 + i * 18;
            modDown[i] = stepperButton(left + 126, rowY, 14, 16, false, () -> moduleParam(rowIndex));
            modUp[i] = stepperButton(left + 166, rowY, 14, 16, true, () -> moduleParam(rowIndex));
        }

        // The sides are a diagram of their own: it decides per side whether there is anything to click.
        diagram = new SideDiagram(new SideDiagram.Host() {
            @Override public SideDiagram.Look look(MachinePortMode.Side side) { return sideLook(side); }
            @Override public void step(MachinePortMode.Side side, boolean forward) {
                sendMenu((forward ? MachineMenu.CYCLE_PORT : MachineMenu.CYCLE_PORT_BACK) + side.ordinal());
            }
            @Override public boolean editable() { return canEdit(); }
        }, font, left, top + CONTENT_Y, widget -> { widgets.add(widget); addWidget.accept(widget); });
        target = new EditBox(font, left, top + 34, width - 28, 18, tr("stock_target"));
        target.setMaxLength(13); target.setValue("0"); target.setFilter(value -> value.matches("[0-9]*"));
        target.setTextColor(UiPalette.TEXT); target.setHint(Component.literal("0"));
        target.setResponder(value -> { if (!updating) { dirty = true; error = Component.empty(); } });
        register(target, Section.PRODUCTION);
        apply = grouped(Section.PRODUCTION, button(tr("apply"), left + width - 24, top + 34, 24, 18, b -> saveTarget()));
        product = grouped(Section.PRODUCTION, button(Component.empty(), left, top + 56, width, 18, b -> {
            if (!products.isEmpty()) { productIndex = (productIndex + 1) % products.size(); dirty = true; error = Component.empty(); updateWidgets(); }
        }));
        gasCycle = grouped(Section.PRODUCTION, button(Component.literal("\u203a"), left, top + 78, 18, 18, b -> {
            if (!gases.isEmpty()) { gasIndex = (gasIndex + 1) % gases.size(); updateWidgets(); }
        }));
        vent = grouped(Section.PRODUCTION, button(Component.empty(), left + 22, top + 78, width - 22, 18, b -> toggleVent()));
        automation = grouped(Section.ACCESS, button(Component.empty(), left, top + CONTENT_Y, width, 18, b -> sendMenu(3)));
        autoEject = grouped(Section.ACCESS, button(Component.empty(), left, top + CONTENT_Y + 21, width, 18, b -> sendMenu(MachineMenu.TOGGLE_AUTO_EJECT)));
        jobs = grouped(Section.ACCESS, button(Component.empty(), left, top + CONTENT_Y + ROW, width, 18, b -> sendMenu(8)));
        redstone = grouped(Section.ACCESS, button(Component.empty(), left, top + CONTENT_Y + ROW, width, 18, b -> sendMenu(MachineMenu.CYCLE_REDSTONE)));
        backend = grouped(Section.ACCESS, button(Component.empty(), left, top + CONTENT_Y + ROW, width, 18, b -> sendMenu(9)));
        release = grouped(Section.ACCESS, button(tr("settings.recover"), left, top + CONTENT_Y + ROW * 2, width, 18,
                b -> minecraft.pushGuiLayer(new ReleaseConfirmation(menu, menu.snapshot().orders()))));
        apply.icon=UiIcon.CONFIRM;apply.iconOnly=true;product.icon=UiIcon.TARGET;gasCycle.icon=UiIcon.NEXT;gasCycle.iconOnly=true;
        gasCycle.setMessage(tr("settings.gas_next"));
        vent.icon=UiIcon.VENT;automation.icon=UiIcon.HOPPER;autoEject.icon=UiIcon.EJECT;jobs.icon=UiIcon.EXCHANGE;redstone.icon=UiIcon.REDSTONE;backend.icon=UiIcon.NETWORK_ONLINE;release.icon=UiIcon.EJECT;
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
        if (target != null && section != Section.PRODUCTION) target.setFocused(false);
        updateWidgets();
    }
    Section section() { return section; }
    EditBox targetField() { return target; }
    void setVisible(boolean value) {
        visible = value;
        if (!value && target != null) target.setFocused(false);
        updateWidgets();
    }
    boolean isEditing() { return visible && section == Section.PRODUCTION && target != null && target.visible && target.active && target.canConsumeInput(); }

    /** Call before the container's inventory-key handler; text input is delivered by charTyped separately. */
    boolean handlesKey(KeyEvent event) {
        if (!isEditing() || event.isEscape() || event.key() == GLFW.GLFW_KEY_TAB) return false;
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) saveTarget();
        else target.keyPressed(event);
        return true;
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
    private boolean productionAvailable() {
        return menu.kind().processes() && menu.kind() != MachineKind.ANALYZER && !PreparationTier.manual(menu.kind());
    }
    private boolean available(Section value) {
        return switch (value) {
            case STRUCTURE -> menu.unformedIndustrial();
            case SIDES -> MachinePortMode.configurable(menu.kind());
            case PRODUCTION -> productionAvailable();
            case ACCESS -> true;
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
            section = available(Section.STRUCTURE) ? Section.STRUCTURE
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
        boolean hasAudio = dev.latrox.rematter.content.ModSounds.workingSound(menu.kind()) != null;
        soundMute.visible = visible && !singlePage && hasAudio;
        soundMute.active = soundMute.visible && canEdit();
        if (soundMute.visible) {
            MachineSoundLevel level = menu.soundLevel();
            soundMute.icon = switch (level) {
                case HIGH -> UiIcon.SOUND_ON;
                case MEDIUM -> UiIcon.SOUND_MID;
                case LOW -> UiIcon.SOUND_LOW;
                case MUTED -> UiIcon.SOUND_OFF;
            };
            soundMute.selected = level != MachineSoundLevel.HIGH;
            Component stateLabel = tr(switch (level) {
                case HIGH -> "settings.sound_high";
                case MEDIUM -> "settings.sound_medium";
                case LOW -> "settings.sound_low";
                case MUTED -> "settings.sound_muted";
            });
            Component label = tr("settings.sound", stateLabel);
            soundMute.setMessage(label);
            soundMute.setTooltip(Tooltip.create(label.copy().append("\n").append(tr("settings.sound_hint"))));
        }
        securityButton.visible = visible && !singlePage;
        securityButton.setX(left + width - (soundMute.visible ? 42 : 20));
        securityButton.active = securityButton.visible && canEdit() && menu.canManageSecurity();
        if (securityButton.visible) {
            SecurityMode mode = menu.securityMode();
            securityButton.icon = SecurityUi.icon(mode);
            securityButton.selected = mode != SecurityMode.PUBLIC;
            securityButton.setMessage(SecurityUi.label(mode));
            securityButton.setTooltip(Tooltip.create(SecurityUi.tooltip(mode, menu.canManageSecurity())));
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
        boolean productionEdit = visible && section == Section.PRODUCTION && canEdit() && settingsReady() && !menu.snapshot().networkJobs();
        target.active = productionEdit; apply.active = productionEdit;
        product.active = productionEdit && !physical() && products.size() > 1;
        product.setMessage(productLabel()); product.setTooltip(Tooltip.create(productLabel()));
        String count = menu.settings().targetStock() < 0 ? "?" : Quantities.number(menu.settings().targetStock());
        target.setTooltip(Tooltip.create(tr("target_help").copy().append("\n")
                .append(tr("target_limit", Quantities.number(targetLimit()))).append("\n")
                .append(tr(physical() ? "target_local_count" : "target_network_count", count, Quantities.number(menu.settings().settings().target())))));
        apply.setTooltip(Tooltip.create(tr("settings.apply_hint")));
        gasCycle.visible = vent.visible = visible && section == Section.PRODUCTION && !gases.isEmpty();
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
        boolean access = visible && section == Section.ACCESS;
        boolean hasAutoEject = dev.latrox.rematter.content.MachineBlockEntity.supportsAutoEject(menu.kind());
        boolean hasJobs = menu.kind().selectable() || PreparationTier.isPreparation(menu.kind());
        boolean hasRedstone = RedstoneMode.controllable(menu.kind());
        boolean hasInterface = menu.kind() == MachineKind.NETWORK_INTERFACE;
        int accessCount = 1 + (hasAutoEject ? 1 : 0) + (hasJobs ? 1 : 0) + (hasRedstone ? 1 : 0) + (hasInterface ? 2 : 0);
        int nextAccessY = top + (accessCount >= 3 ? 20 : CONTENT_Y);
        int accessStep = accessCount >= 3 ? 20 : ROW;
        automation.visible = access;
        automation.setY(nextAccessY);
        automation.setMessage(tr("settings.pipes", tr(menu.snapshot().automation() ? "settings.allowed" : "settings.blocked")));
        automation.setTooltip(Tooltip.create(tr("automation_hint"))); automation.selected = menu.snapshot().automation();
        nextAccessY += accessStep;
        autoEject.visible = access && hasAutoEject;
        autoEject.active = autoEject.visible && canEdit();
        if (autoEject.visible) {
            autoEject.setY(nextAccessY);
            autoEject.setMessage(tr("settings.auto_eject", tr(menu.autoEject() ? "on" : "off")));
            autoEject.setTooltip(Tooltip.create(tr("auto_eject_hint")));
            autoEject.selected = menu.autoEject();
            nextAccessY += accessStep;
        }
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
        if (!canEdit() || !settingsReady() || !productionAvailable() || menu.snapshot().networkJobs()) return;
        try {
            long amount = Long.parseLong(target.getValue());
            if (amount > targetLimit()) throw new IllegalArgumentException("Target outside output scope");
            String id = "", phase = "";
            if (!physical() && amount > 0) {
                if (products.isEmpty()) { error = tr("target_choose_recipe"); return; }
                var value = products.get(productIndex); id = value.id(); phase = value.phase();
            }
            sendSettings(new MachineSettings(amount, id, phase, menu.settings().settings().ventedGases()));
            dirty = false; error = Component.empty();
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
        try { sendSettings(new MachineSettings(current.target(), current.targetSubstance(), current.targetPhase(), values)); error = Component.empty(); }
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
            int rightEdge = securityButton != null && securityButton.visible ? securityButton.getX() - 2
                    : soundMute != null && soundMute.visible ? soundMute.getX() - 2 : left + width;
            int titleWidth = Math.max(0, rightEdge - titleX);
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
            Component label = error.getString().isEmpty() ? tr("stock_target") : error;
            ScreenChrome.text(g,font,label.getString(),left,top+23,width,error.getString().isEmpty()?UiPalette.MUTED:UiPalette.WARNING);
            if (gases.isEmpty()) g.text(font, tr("no_gas_outputs"), left, top + 83, UiPalette.MUTED, false);
        } else if (section == Section.ACCESS && !jobs.visible && !backend.visible && accessHintY + 18 <= top + 102) {
            ScreenChrome.twoLines(g, font, tr(IndustrialStructure.isIndustrial(menu.kind())
                    ? "settings.industrial_ports" : "automation_hint").getString(), left, accessHintY, width, UiPalette.MUTED);
        }
    }

    private void extractStructureControls(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var bp = currentBlueprint();
        int row0Y = top + 19, row1Y = top + 37;

        // Machine Port icon + count box in Row 0
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(left + 99F, row0Y + 2F);
        pose.scale(12F / 16F, 12F / 16F);
        g.fakeItem(new ItemStack(ModContent.MACHINE_PORT.get()), 0, 0);
        pose.popMatrix();
        centeredBox(g, left + 126, row0Y, 18, 16, Integer.toString(bp.ports()), UiPalette.TEXT);

        // Chamber dimensions in Row 1
        centeredBox(g, left + 13, row1Y, 32, 16, tr("blueprint.dim_w", bp.width()).getString(), UiPalette.TEXT);
        centeredBox(g, left + 74, row1Y, 32, 16, tr("blueprint.dim_d", bp.depth()).getString(), UiPalette.TEXT);
        centeredBox(g, left + 135, row1Y, 32, 16, tr("blueprint.dim_h", bp.height()).getString(), UiPalette.TEXT);

        // Module rows (Rows 2..4)
        var modules = moduleSlots();
        for (int i = 0; i < modules.size(); i++) {
            var slot = modules.get(i);
            int rowY = top + 55 + i * 18;
            g.fakeItem(slot.icon(), left, rowY);
            Component blockName = Component.translatable("block.rematter." + slot.blockId());
            ScreenChrome.text(g, font, blockName.getString(), left + 19, rowY + 4, 104, UiPalette.TEXT);
            int count = moduleCount(bp, slot.parameter());
            centeredBox(g, left + 140, rowY, 26, 16, Integer.toString(count), UiPalette.machineAccent(menu.kind()));
            if (inside(mouseX, mouseY, left, rowY, 124, 16) || inside(mouseX, mouseY, left + 140, rowY, 26, 16)) {
                g.setComponentTooltipForNextFrame(font, List.of(
                        blockName,
                        Component.translatable("screen.rematter.module_detail", count, blockName, tr(slot.benefitKey())),
                        tr("blueprint.auto_expand_hint")), mouseX, mouseY);
            }
        }
        if (modules.size() < 3) {
            int rowY = top + 55 + modules.size() * 18 + 4;
            ScreenChrome.text(g, font, tr("blueprint.mounts", bp.totalModules(), bp.slotCapacity()).getString(),
                    left + 2, rowY, width - 4, UiPalette.MUTED);
        }

        if (inside(mouseX, mouseY, left + 98, row0Y, 28, 16) || inside(mouseX, mouseY, left + 126, row0Y, 18, 16)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("block.rematter.machine_port"),
                    tr("blueprint.ports_hint", bp.ports())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, left + 13, row1Y, 32, 16)) {
            g.setComponentTooltipForNextFrame(font, List.of(tr("blueprint.width_detail", bp.width())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, left + 74, row1Y, 32, 16)) {
            g.setComponentTooltipForNextFrame(font, List.of(tr("blueprint.depth_detail", bp.depth())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, left + 135, row1Y, 32, 16)) {
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
