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
