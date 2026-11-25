package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MachineSoundLevel;
import dev.latrox.rematter.content.SecurityMode;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Shared "General" settings sub-panel for fixed per-device controls across every configurable ReMatter screen:
 * Custom Name (text field + Save + Clear), Security (Public / Trusted / Private) with its explanation directly
 * underneath the button, and optional Sound Volume below that.
 */
final class DeviceGeneralPanel {
    private final Font font;
    private final int left, top, width;
    private final boolean hasSound;
    private final Supplier<SecurityMode> securityMode;
    private final BooleanSupplier canManageSecurity;
    private final BooleanSupplier canEdit;
    private final Consumer<Boolean> cycleSecurity;
    private final Supplier<String> currentCustomName;
    private final Consumer<String> onRename;
    private final @Nullable Supplier<MachineSoundLevel> soundLevel;
    private final @Nullable Consumer<Boolean> cycleSound;

    private final EditBox nameBox;
    private final DarkButton saveBtn;
    private final DarkButton clearBtn;
    private final DarkButton securityBtn;
    private final @Nullable DarkButton soundBtn;

    private boolean visible;
    private boolean dirty;
    private boolean updating;
    private String lastSyncedName = "";
    private int accent;

    DeviceGeneralPanel(Font font, Consumer<AbstractWidget> addWidget,
                       int left, int top, int width, int accent, Component defaultTitle,
                       Supplier<SecurityMode> securityMode, BooleanSupplier canManageSecurity,
                       BooleanSupplier canEdit, Consumer<Boolean> cycleSecurity,
                       Supplier<String> currentCustomName, Consumer<String> onRename,
                       @Nullable Supplier<MachineSoundLevel> soundLevel, @Nullable Consumer<Boolean> cycleSound) {
        this.font = font;
        this.left = left;
        this.top = top;
        this.width = width;
        this.accent = accent;
        this.hasSound = soundLevel != null && cycleSound != null;
        this.securityMode = securityMode;
        this.canManageSecurity = canManageSecurity;
        this.canEdit = canEdit;
        this.cycleSecurity = cycleSecurity;
        this.currentCustomName = currentCustomName;
        this.onRename = onRename;
        this.soundLevel = soundLevel;
        this.cycleSound = cycleSound;

        int boxOuterW = Math.max(60, width - 40);
        nameBox = new EditBox(font, left + 4, top + 15, boxOuterW - 8, 10, tr("custom_name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(CustomNames.MAX_LENGTH);
        nameBox.setTextColor(TEXT);
        nameBox.setHint(defaultTitle.copy().withStyle(s -> s.withColor(rgb(MUTED))));
        String initialName = safe(currentCustomName.get());
        lastSyncedName = initialName;
        updating = true;
        nameBox.setValue(initialName);
        updating = false;
        nameBox.setResponder(v -> {
            if (!updating) dirty = !CustomNames.sanitize(v).equals(lastSyncedName);
        });
        addWidget.accept(nameBox);

        saveBtn = DarkButton.icon(UiIcon.CONFIRM, tr("save"), tr("save_hint"),
                left + width - 38, top + 10, 18, 18, b -> commitName());
        saveBtn.setAccent(accent);
        addWidget.accept(saveBtn);

        clearBtn = DarkButton.icon(UiIcon.CLEAR, tr("clear"), tr("clear_hint"),
                left + width - 18, top + 10, 18, 18, b -> clearName());
        clearBtn.setAccent(accent);
        addWidget.accept(clearBtn);

        var initialSec = securityMode.get();
        securityBtn = DarkButton.labelIcon(SecurityUi.icon(initialSec),
                SecurityUi.label(initialSec), initialSec.meaning(),
                left, top + 32, width, 18, b -> {});
        securityBtn.setStepAction(forward -> {
            if (canEdit.getAsBoolean() && canManageSecurity.getAsBoolean()) {
                cycleSecurity.accept(forward);
            }
        });
        securityBtn.setAccent(accent);
        addWidget.accept(securityBtn);

        if (hasSound) {
            soundBtn = DarkButton.labelIcon(UiIcon.SOUND_ON,
                    Component.translatable("screen.rematter.settings.sound",
                            Component.translatable("screen.rematter.settings.sound_high")),
                    Component.translatable("screen.rematter.settings.sound_hint"),
                    left, top + 72, width, 18, b -> {});
            soundBtn.setStepAction(forward -> {
                if (canEdit.getAsBoolean()) {
                    cycleSound.accept(forward);
                }
            });
            soundBtn.setAccent(accent);
            addWidget.accept(soundBtn);
        } else {
            soundBtn = null;
        }

        refresh(false, accent);
    }

    static DarkButton createSettingsTab(Consumer<AbstractWidget> addWidget, int leftPos, int topPos, int row,
                                        int accent, Runnable onSelect) {
        var btn = DarkButton.icon(UiIcon.SETTINGS,
                Component.translatable("screen.rematter.ui.tab_settings"),
                Component.translatable("screen.rematter.ui.tab_settings_hint"),
                leftPos - 22, topPos + 18 + row * 24, 24, 22, b -> onSelect.run());
        btn.setAccent(accent);
        addWidget.accept(btn);
        return btn;
    }

    static DarkButton createSubTab(Consumer<AbstractWidget> addWidget, UiIcon icon, Component label, Component hint,
                                   int x, int y, int accent, Runnable onSelect) {
        var btn = DarkButton.icon(icon, label, hint, x, y, 22, 16, b -> onSelect.run());
        btn.setAccent(accent);
        addWidget.accept(btn);
        return btn;
    }

    static SideDiagram.Look portLook(MachinePortMode.Side side, boolean configurable, MachinePortMode mode,
                                     SideDiagram.@Nullable Neighbor neighbor) {
        if (!configurable) {
            String key = side == MachinePortMode.Side.FRONT ? "front" : "emitter_top";
            Component name = Component.translatable("port.rematter.closed." + key);
            Component hint = Component.translatable("port.rematter.closed_hint." + key);
            return new SideDiagram.Look(
                    side == MachinePortMode.Side.TOP ? SideDiagram.Face.OPEN : SideDiagram.Face.PLAIN,
                    MUTED, null, true,
                    side.label().copy().append("\n").append(name).append("\n").append(hint), neighbor);
        }
        return new SideDiagram.Look(
                mode == MachinePortMode.DISABLED ? SideDiagram.Face.PLAIN : SideDiagram.Face.PLATE,
                mode.ring(), null, false,
                Component.translatable("screen.rematter.settings.side_hint", side.label(), mode.label(), mode.meaning()),
                neighbor);
    }

    static void drawPortReadout(GuiGraphicsExtractor g, Font font, MachinePortMode.Side side, boolean configurable,
                                MachinePortMode mode, int x, int y, int width) {
        if (!configurable) {
            String key = side == MachinePortMode.Side.FRONT ? "front" : "emitter_top";
            ScreenChrome.icon(g, UiIcon.LOCKED, x, y - 3, MUTED);
            ScreenChrome.text(g, font, Component.translatable("port.rematter.closed." + key).getString(),
                    x + 18, y + 1, width - 18, MUTED);
            ScreenChrome.twoLines(g, font, Component.translatable("port.rematter.closed_hint." + key).getString(),
                    x, y + 11, width, MUTED);
            return;
        }
        ScreenChrome.icon(g, UiIcon.port(mode), x, y - 3, mode.color());
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.settings.side_state",
                side.label(), mode.label()).getString(), x + 18, y + 1, width - 18, mode.color());
        ScreenChrome.twoLines(g, font, mode.meaning().getString(), x, y + 11, width, MUTED);
    }

    static void drawTitle(GuiGraphicsExtractor g, Font font, String customName, Component fallbackTitle, int maxWidth) {
        String shown = customName == null || customName.isEmpty() ? fallbackTitle.getString() : customName;
        ScreenChrome.text(g, font, shown, 8, 6, maxWidth, TEXT);
    }

    private static Component tr(String key) {
        return Component.translatable("screen.rematter.device." + key);
    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s;
    }

    private void commitName() {
        if (!canEdit.getAsBoolean()) return;
        String clean = CustomNames.sanitize(nameBox.getValue());
        lastSyncedName = clean;
        dirty = false;
        updating = true;
        nameBox.setValue(clean);
        updating = false;
        nameBox.setFocused(false);
        onRename.accept(clean);
        refresh(visible, accent);
    }

    private void clearName() {
        if (!canEdit.getAsBoolean()) return;
        lastSyncedName = "";
        dirty = false;
        updating = true;
        nameBox.setValue("");
        updating = false;
        nameBox.setFocused(false);
        onRename.accept("");
        refresh(visible, accent);
    }

    void refresh(boolean show, int nextAccent) {
        this.visible = show;
        this.accent = nextAccent;
        boolean editable = show && canEdit.getAsBoolean();

        String serverName = safe(currentCustomName.get());
        if (!dirty && !nameBox.isFocused() && !serverName.equals(lastSyncedName)) {
            lastSyncedName = serverName;
            updating = true;
            nameBox.setValue(serverName);
            updating = false;
        }
        if (!show && nameBox.isFocused()) {
            nameBox.setFocused(false);
        }

        nameBox.visible = show;
        nameBox.active = editable;

        String typed = CustomNames.sanitize(nameBox.getValue());
        boolean changed = !typed.equals(serverName);
        saveBtn.visible = show;
        saveBtn.active = editable && changed;
        saveBtn.setAccent(accent);
        saveBtn.setSelected(changed);

        clearBtn.visible = show;
        clearBtn.active = editable && (!serverName.isEmpty() || !typed.isEmpty());
        clearBtn.setAccent(accent);

        SecurityMode sec = securityMode.get();
        boolean canManage = canManageSecurity.getAsBoolean();
        securityBtn.visible = show;
        securityBtn.active = editable && canManage;
        securityBtn.setIcon(SecurityUi.icon(sec));
        securityBtn.setMessage(SecurityUi.label(sec));
        securityBtn.setTooltip(Tooltip.create(SecurityUi.tooltip(sec, canManage)));
        securityBtn.setSelected(sec != SecurityMode.PUBLIC);
        securityBtn.setAccent(accent);

        if (soundBtn != null && soundLevel != null) {
            soundBtn.visible = show;
            soundBtn.active = editable;
            soundBtn.setAccent(accent);
            if (show) {
                MachineSoundLevel level = soundLevel.get();
                soundBtn.setIcon(switch (level) {
                    case HIGH -> UiIcon.SOUND_ON;
                    case MEDIUM -> UiIcon.SOUND_MID;
                    case LOW -> UiIcon.SOUND_LOW;
                    case MUTED -> UiIcon.SOUND_OFF;
                });
                soundBtn.setSelected(level != MachineSoundLevel.HIGH);
                Component stateLabel = Component.translatable(switch (level) {
                    case HIGH -> "screen.rematter.settings.sound_high";
                    case MEDIUM -> "screen.rematter.settings.sound_medium";
                    case LOW -> "screen.rematter.settings.sound_low";
                    case MUTED -> "screen.rematter.settings.sound_muted";
                });
                Component label = Component.translatable("screen.rematter.settings.sound", stateLabel);
                soundBtn.setMessage(label);
                soundBtn.setTooltip(Tooltip.create(label.copy().append("\n")
                        .append(Component.translatable("screen.rematter.settings.sound_hint"))));
            }
        }
    }

    void extractBackground(GuiGraphicsExtractor g) {
        if (!visible) return;
        int boxOuterW = Math.max(60, width - 40);
        ScreenChrome.inset(g, left, top + 10, boxOuterW, 18);
        if (nameBox.isFocused()) {
            g.fill(left, top + 27, left + boxOuterW, top + 28, accent);
        }
    }

    void extractLabels(GuiGraphicsExtractor g, int leftPos, int topPos) {
        if (!visible) return;
        int lx = left - leftPos;
        int ly = top - topPos;
        ScreenChrome.text(g, font, tr("custom_name").getString(), lx, ly, width, MUTED);
        SecurityMode sec = securityMode.get();
        // Directly below the Security button (top+32..50), before the optional Sound button (top+72..90).
        ScreenChrome.twoLines(g, font, sec.meaning().getString(), lx, ly + 52, width, MUTED);
        if (!hasSound) {
            ScreenChrome.text(g, font, tr("name_note").getString(), lx, ly + 72, width, MUTED);
        }
    }

    boolean isTyping() {
        return visible && nameBox.isFocused();
    }

    boolean handlesKey(KeyEvent event) {
        if (!visible || !nameBox.isFocused()) return false;
        if (event.isEscape()) {
            nameBox.setFocused(false);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commitName();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_TAB) return true;
        nameBox.keyPressed(event);
        return true;
    }

    boolean handlesChar(CharacterEvent event) {
        if (!visible || !nameBox.isFocused()) return false;
        return nameBox.charTyped(event);
    }
}
