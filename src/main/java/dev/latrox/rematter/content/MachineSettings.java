package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.SubstanceId;
import java.util.Set;

/** Bounded server settings; the target is in local items/mB or selected network substance units. */
public record MachineSettings(long target, String targetSubstance, String targetPhase, Set<String> ventedGases) {
    public static final long MAX_TARGET=1_000_000_000_000L;
    public static final MachineSettings DEFAULT=new MachineSettings(0,"","",Set.of());
    public MachineSettings {
        ventedGases=Set.copyOf(ventedGases);
        if(target<0||target>MAX_TARGET||ventedGases.size()>32)throw new IllegalArgumentException("Invalid machine settings");
        if(!targetSubstance.isEmpty()) {
            SubstanceId.parse(targetSubstance);
            if(!Set.of("solid","liquid","gas").contains(targetPhase))throw new IllegalArgumentException("Invalid target phase");
        } else if(!targetPhase.isEmpty())throw new IllegalArgumentException("Target needs substance identity");
        for(var gas:ventedGases)SubstanceId.parse(gas);
        if(ventedGases.contains(targetSubstance)&&targetPhase.equals("gas")&&target>0)
            throw new IllegalArgumentException("A gas target cannot be vented");
    }
}
