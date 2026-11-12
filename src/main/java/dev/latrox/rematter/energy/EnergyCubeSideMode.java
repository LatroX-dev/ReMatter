package dev.latrox.rematter.energy;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** A face has one role; two connected buffers cannot oscillate energy through an automatic face. */
public enum EnergyCubeSideMode {
    INPUT,OUTPUT,DISABLED;
    public EnergyCubeSideMode next(){return values()[(ordinal()+1)%values().length];}
    public EnergyCubeSideMode previous(){return values()[(ordinal()+values().length-1)%values().length];}
    /** One sentence on what a side with this role does. */
    public Component meaning(){return Component.translatable("screen.rematter.energy_cube.meaning."+name().toLowerCase(Locale.ROOT));}
    public Component label(){return Component.translatable("screen.rematter.energy_cube.mode."+name().toLowerCase(Locale.ROOT));}
}
