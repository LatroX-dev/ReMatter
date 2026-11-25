package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.RedstoneMode;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Shared "Access" settings sub-panel for non-MachineBlock devices:
 * optional Pipes & Hoppers toggle (e.g. Energy Cube) plus Redstone control (Ignored / High / Low)
 * with its 2-line meaning text directly underneath.
 */
final class DeviceAccessPanel {
    private final Font font;
    private final int left, top, width;
    private final BooleanSupplier canEdit;
    private final Supplier<RedstoneMode> redstoneMode;
    private final @Nullable Component redstoneTooltip;
    private final @Nullable BooleanSupplier automationAllowed;
    private final @Nullable DarkButton automationBtn;
    private final DarkButton redstoneBtn;

    private boolean visible;
    private int accent;

    DeviceAccessPanel(Font font, Consumer<AbstractWidget> addWidget,
                      int left, int top, int width, int accent,
                      BooleanSupplier canEdit,
                      Supplier<RedstoneMode> redstoneMode,
                      Consumer<Boolean> cycleRedstone,
                      @Nullable Component redstoneTooltip,
                      @Nullable BooleanSupplier automationAllowed,
                      @Nullable Runnable toggleAutomation) {
        this.font = font;
        this.left = left;
        this.top = top;
        this.width = width;
        this.accent = accent;
        this.canEdit = canEdit;
        this.redstoneMode = redstoneMode;
        this.redstoneTooltip = redstoneTooltip;
        this.automationAllowed = automationAllowed;

        int nextY = top;
        if (automationAllowed != null && toggleAutomation != null) {
            automationBtn = DarkButton.labelIcon(UiIcon.HOPPER,
                    Component.translatable("screen.rematter.settings.pipes",
                            Component.translatable("screen.rematter.settings.allowed")),
                    Component.translatable("screen.rematter.automation_hint"),
                    left, nextY, width, 18, b -> {
                        if (canEdit.getAsBoolean()) toggleAutomation.run();
                    });
            automationBtn.setAccent(accent);
            addWidget.accept(automationBtn);
            nextY += 22;
        } else {
            automationBtn = null;
        }

        RedstoneMode initial = redstoneMode.get();
        redstoneBtn = DarkButton.labelIcon(UiIcon.REDSTONE,
                Component.translatable("screen.rematter.settings.redstone", initial.label()),
                redstoneTooltip != null ? redstoneTooltip : initial.meaning(),
                left, nextY, width, 18, b -> {});
        redstoneBtn.setStepAction(forward -> {
            if (canEdit.getAsBoolean()) cycleRedstone.accept(forward);
        });
        redstoneBtn.setAccent(accent);
        addWidget.accept(redstoneBtn);

        refresh(false, accent);
    }

    void refresh(boolean show, int nextAccent) {
        this.visible = show;
        this.accent = nextAccent;
        boolean editable = show && canEdit.getAsBoolean();

        if (automationBtn != null && automationAllowed != null) {
            boolean allowed = automationAllowed.getAsBoolean();
            automationBtn.visible = show;
            automationBtn.active = editable;
            automationBtn.setAccent(accent);
            automationBtn.setSelected(allowed);
            automationBtn.setMessage(Component.translatable("screen.rematter.settings.pipes",
                    Component.translatable(allowed
                            ? "screen.rematter.settings.allowed"
                            : "screen.rematter.settings.blocked")));
            automationBtn.setTooltip(Tooltip.create(Component.translatable("screen.rematter.automation_hint")));
        }

        RedstoneMode mode = redstoneMode.get();
        redstoneBtn.visible = show;
        redstoneBtn.active = editable;
        redstoneBtn.setAccent(accent);
        redstoneBtn.setSelected(mode != RedstoneMode.IGNORED);
        redstoneBtn.setMessage(Component.translatable("screen.rematter.settings.redstone", mode.label()));
        redstoneBtn.setTooltip(Tooltip.create(redstoneTooltip != null ? redstoneTooltip : mode.meaning()));
    }

    void extractLabels(GuiGraphicsExtractor g, int leftPos, int topPos) {
        if (!visible) return;
        int lx = left - leftPos;
        int meaningY = (redstoneBtn.getY() - topPos) + 22;
        ScreenChrome.twoLines(g, font, redstoneMode.get().meaning().getString(), lx, meaningY, width, MUTED);
    }
}
