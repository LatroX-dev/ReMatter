package dev.latrox.rematter.content;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** A port's baked appearance, separate from the roles that authorize transfers. Never stored as configuration. */
public enum PortAppearance implements StringRepresentable {
    AUTOMATIC, ITEM_INPUT, ITEM_OUTPUT, FLUID_INPUT, FLUID_OUTPUT, POWER, MATTER, CHEMICAL, DISABLED,
    ENERGY_INPUT, ENERGY_OUTPUT;

    public static final com.mojang.serialization.Codec<PortAppearance> CODEC = StringRepresentable.fromEnum(PortAppearance::values);
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public static PortAppearance of(MachinePortMode role) { return valueOf(role.name()); }
    public static PortAppearance byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : DISABLED;
    }
}
