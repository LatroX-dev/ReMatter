package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.content.MachineKind;

/** Shared physical-preparation roles; cells and chemistry storage are never part of this path. */
public final class PreparationTier {
    private PreparationTier(){}
    public static boolean isPreparation(MachineKind kind){return kind==MachineKind.PREPARATOR||kind==MachineKind.HAND_PREPARATOR||kind==MachineKind.INDUSTRIAL_PREPARATOR||kind==MachineKind.SINTER_FURNACE;}
    public static boolean manual(MachineKind kind){return kind==MachineKind.HAND_PREPARATOR;}
    public static boolean industrial(MachineKind kind){return kind==MachineKind.INDUSTRIAL_PREPARATOR;}
    public static boolean permits(MachineKind kind,int minimumTier){return permits(kind,minimumTier,PreparationRecipe.PREPARATION);}
    public static boolean permits(MachineKind kind,int minimumTier,String process){
        if(!isPreparation(kind)||minimumTier<0||minimumTier>2)return false;
        if(PreparationRecipe.SINTERING.equals(process))return kind==MachineKind.SINTER_FURNACE&&minimumTier<2;
        return PreparationRecipe.PREPARATION.equals(process)&&kind!=MachineKind.SINTER_FURNACE
                &&(minimumTier<2||industrial(kind))&&(!manual(kind)||minimumTier==0);
    }
    public static int speed(MachineKind kind){return industrial(kind)?4:1;}
    public static int energyMultiplier(MachineKind kind){return manual(kind)?0:industrial(kind)?2:1;}
}
