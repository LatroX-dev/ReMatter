package dev.latrox.rematter.client;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.SecurityMode;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Shared Mekanism-style security button presentation for all device screens. */
final class SecurityUi {
    private SecurityUi() {}

    static UiIcon icon(SecurityMode mode) {
        return switch (mode) {
            case PUBLIC -> UiIcon.UNLOCKED;
            case TRUSTED -> UiIcon.ACCESS;
            case PRIVATE -> UiIcon.LOCKED;
        };
    }

    static Component label(SecurityMode mode) {
        return Component.translatable("screen.rematter.settings.security", mode.label());
    }

    static Component tooltip(SecurityMode mode, boolean canManage) {
        return label(mode).copy()
                .append("\n").append(mode.meaning())
                .append("\n").append(Component.translatable(
                        canManage ? "screen.rematter.settings.security_hint" : "screen.rematter.settings.security_owner_only"));
    }

    static void configure(DarkButton button, SecurityMode mode, boolean canManage, boolean editable, int accent) {
        button.setIcon(icon(mode));
        button.setMessage(label(mode));
        button.setTooltip(Tooltip.create(tooltip(mode, canManage)));
        button.setSelected(mode != SecurityMode.PUBLIC);
        button.active = editable && canManage;
        button.setAccent(accent);
    }
}
