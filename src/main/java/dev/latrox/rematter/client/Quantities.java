package dev.latrox.rematter.client;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Locale-independent numbers, with each unit taken from the language file. */
public final class Quantities {
    private Quantities() {}

    public static String number(long value) { return String.format(Locale.ROOT, "%,d", value); }

    public static String compact(long value) {
        if (value >= 1_000_000_000_000L) return String.format(Locale.ROOT, "%.1e", (double) value);
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", value / 1e9);
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        if (value >= 10_000L) return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        return Long.toString(value);
    }

    public static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20.0); }

    public static Component matter(String amount) { return Component.translatable("unit.rematter.matter", amount); }
    public static Component energy(String amount) { return Component.translatable("unit.rematter.energy", amount); }
    public static Component fluid(String amount) { return Component.translatable("unit.rematter.fluid", amount); }
    public static Component ticks(String amount) { return Component.translatable("unit.rematter.ticks", amount); }
    public static Component duration(int ticks) { return Component.translatable("unit.rematter.seconds", seconds(ticks)); }
}
