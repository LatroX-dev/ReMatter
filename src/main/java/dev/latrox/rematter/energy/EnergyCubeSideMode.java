package dev.latrox.rematter.energy;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Ordinals 0..2 remain fixed for save compatibility; INPUT_OUTPUT occupies 2-bit value 3. */
public enum EnergyCubeSideMode {
    INPUT,OUTPUT,DISABLED,INPUT_OUTPUT;
    private static final EnergyCubeSideMode[] CYCLE={INPUT,OUTPUT,INPUT_OUTPUT,DISABLED};
    public EnergyCubeSideMode next(){return step(1);}
    public EnergyCubeSideMode previous(){return step(CYCLE.length-1);}
    private EnergyCubeSideMode step(int delta){
        for(int i=0;i<CYCLE.length;i++)if(CYCLE[i]==this)return CYCLE[(i+delta)%CYCLE.length];
        return INPUT;
    }
    public boolean allowsInput(){return this==INPUT||this==INPUT_OUTPUT;}
    public boolean allowsOutput(){return this==OUTPUT||this==INPUT_OUTPUT;}
    /** One sentence on what a side with this role does. */
    public Component meaning(){return Component.translatable("screen.rematter.energy_cube.meaning."+name().toLowerCase(Locale.ROOT));}
    public Component label(){return Component.translatable("screen.rematter.energy_cube.mode."+name().toLowerCase(Locale.ROOT));}
}
