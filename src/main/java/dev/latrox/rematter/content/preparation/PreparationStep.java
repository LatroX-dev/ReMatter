package dev.latrox.rematter.content.preparation;

import com.mojang.serialization.Codec;
import java.util.Locale;

/** Hardware needed by a physical recipe, independently of its processing speed. */
public enum PreparationStep {
    GRIND, MIX, SEPARATE;
    public static final Codec<PreparationStep> CODEC = Codec.STRING.xmap(
            value -> valueOf(value.toUpperCase(Locale.ROOT)), PreparationStep::id);
    public String id() { return name().toLowerCase(Locale.ROOT); }
}
