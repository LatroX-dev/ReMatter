package dev.latrox.rematter.energy;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** A face has one role; two connected buffers cannot oscillate energy through an automatic face. */
public enum EnergyCubeSideMode {
    INPUT,OUTPUT,DISABLED;
    public EnergyCubeSideMode next(){return values()[(ordinal()+1)%values().length];}
    public Component label(){return Component.translatable("screen.rematter.energy_cube.mode."+name().toLowerCase(Locale.ROOT));}
}
