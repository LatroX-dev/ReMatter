package dev.latrox.rematter.content;

import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Shared custom device label helpers (UI rename + vanilla Anvil + Wrench dismantle/placement preservation). */
public final class CustomNames {
    public static final int MAX_LENGTH = 40;

    private CustomNames() {}

    public static String sanitize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return "";
        var sb = new StringBuilder(Math.min(MAX_LENGTH, raw.length()));
        for (int i = 0; i < raw.length() && sb.length() < MAX_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c == '\u00A7') {
                if (i + 1 < raw.length()) i++;
                continue;
            }
            if (c < 0x20 || c == 0x7F) continue;
            sb.append(c);
        }
        return sb.toString().strip();
    }

    public static Component displayName(String customName, String fallbackKey) {
        return customName == null || customName.isEmpty()
                ? Component.translatable(fallbackKey)
                : Component.literal(customName);
    }

    public static String fromStack(ItemStack stack) {
        if (stack.isEmpty()) return "";
        Component comp = stack.get(DataComponents.CUSTOM_NAME);
        return comp == null ? "" : sanitize(comp.getString());
    }

    public static void applyToStack(ItemStack stack, String customName) {
        String clean = sanitize(customName);
        if (clean.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(clean));
        }
    }

    public static String shortDimensionLabel(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty() || dimensionId.equals("minecraft:overworld")) return "";
        return switch (dimensionId) {
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> {
                int idx = dimensionId.indexOf(':');
                String path = idx >= 0 ? dimensionId.substring(idx + 1) : dimensionId;
                if (path.isEmpty()) yield dimensionId;
                yield path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1).replace('_', ' ');
            }
        };
    }
}
