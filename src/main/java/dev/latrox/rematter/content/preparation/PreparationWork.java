package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.content.MachineEnergyPolicy;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.Identifier;

/** Shared bounds and physical batch readiness. No material or power is consumed until completion. */
public final class PreparationWork {
    /** The industrial tier doubles a preparation price, so its base must stay within half of a machine batch. */
    public static final int MAX_ENERGY=(int)(MachineEnergyPolicy.MAX_BATCH_ENERGY/2), MAX_TICKS=1_000_000;
    // Production recipes have four times the work; keep the hand workshop's click cadence.
    public static final int CRANK_WORK=80, CRANK_COOLDOWN=5;
    public static final int MAX_RECIPES=256;
    private PreparationWork() {}
    /** A catalogue is usable only as a whole: bounded in size, with unique and bounded identifiers. */
    public static boolean validCatalogue(List<Identifier> ids) {
        if(ids.size()>MAX_RECIPES)return false;
        var seen=new HashSet<Identifier>();
        for(var id:ids)if(id.toString().length()>512||!seen.add(id))return false;
        return true;
    }
    public static int stage(int progress,int duration) {
        if(duration<3||progress<1||progress>duration)return -1;
        return (int)Math.min(2,((long)progress-1)*3/duration);
    }
    public static void validate(int outputCount,int outputLimit,int energy,int ticks) {
        if(outputCount<1||outputCount>64||outputCount>outputLimit||energy<1||energy>MAX_ENERGY||ticks<3||ticks>MAX_TICKS)
            throw new IllegalArgumentException("Invalid preparation batch");
    }
    public static boolean hasRequiredCounts(int primary,int secondary,int tertiary,int primaryRequired,int secondaryRequired,int tertiaryRequired) {
        return primary>=primaryRequired&&secondary>=secondaryRequired&&tertiary>=tertiaryRequired;
    }
    public static void validateInputs(int primary,int secondary,int tertiary,boolean hasSecondary,boolean hasTertiary,String process) {
        if(primary<1||primary>64||secondary<1||secondary>64||tertiary<1||tertiary>64)
            throw new IllegalArgumentException("Ingredient count outside stack bounds");
        if(hasTertiary&&!hasSecondary)throw new IllegalArgumentException("Third input needs a second input");
        if(!"preparation".equals(process)&&!"sintering".equals(process))throw new IllegalArgumentException("Unknown physical process");
        if("sintering".equals(process)&&(hasSecondary||hasTertiary))throw new IllegalArgumentException("Sintering takes one prepared batch");
    }
    public static String stageName(int stage,boolean mixing,boolean sintering) {
        return sintering?switch(stage){case 0->"pressing";case 1->"sintering";default->"cooling";}:stageName(stage,mixing);
    }
    public static String readiness(boolean primary,boolean secondary,boolean plain,boolean matches,boolean fits,long energy,long cost) {
        return readiness(primary,secondary,true,plain,matches,fits,energy,cost);
    }
    public static String readiness(boolean primary,boolean secondary,boolean needsSecondary,boolean plain,boolean matches,boolean fits,long energy,long cost) {
        if(!primary)return "input";
        if(needsSecondary&&!secondary)return "secondary_input";
        if(!needsSecondary&&secondary)return "secondary_slot_occupied";
        if(!plain)return "unsupported_components";
        if(!matches)return "unsupported_input";
        if(!fits)return "output_blocked";
        return energy<cost?"energy":"ready";
    }
    public static String stageName(int stage,boolean mixing){
        return mixing?switch(stage){case 0->"dosing";case 1->"mixing";default->"packing";}
                :switch(stage){case 0->"grinding";case 1->"sieving";default->"collecting";};
    }
}
