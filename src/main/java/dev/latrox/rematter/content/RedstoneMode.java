package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** Optional redstone gating for powered machines and generators. */
public enum RedstoneMode implements StringRepresentable {
    IGNORED,
    HIGH,
    LOW,
    PULSE;

    private static final RedstoneMode[] VALUES = values();

    public static boolean controllable(MachineKind kind) {
        return (kind.processes() || kind.generator()) && !PreparationTier.manual(kind);
    }

    public static RedstoneMode byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : IGNORED;
    }

    public static RedstoneMode load(String raw) {
        for (var value : VALUES) {
            if (value.getSerializedName().equals(raw)) return value;
        }
        throw new IllegalArgumentException("Unknown redstone mode: " + raw);
    }

    public RedstoneMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public boolean permits(boolean powered, boolean pulsePending) {
        return switch (this) {
            case IGNORED -> true;
            case HIGH -> powered;
            case LOW -> !powered;
            case PULSE -> pulsePending;
        };
    }

    public Component label() {
        return Component.translatable("screen.rematter.redstone." + getSerializedName());
    }

    public Component meaning() {
        return Component.translatable("screen.rematter.redstone_hint." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
