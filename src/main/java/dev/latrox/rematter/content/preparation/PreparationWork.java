package dev.latrox.rematter.content.preparation;

/** Shared bounds and physical batch readiness. No material or power is consumed until completion. */
public final class PreparationWork {
    public static final int MAX_ENERGY=1_000_000, MAX_TICKS=1_000_000;
    public static final int CRANK_WORK=20, CRANK_COOLDOWN=5;
    private PreparationWork() {}
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
