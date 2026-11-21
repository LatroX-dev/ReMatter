package dev.latrox.rematter.client.anim;

import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineKind;
import java.util.Map;
import java.util.WeakHashMap;

/** One work envelope per machine, shared by the mechanism that is drawn and the sound that follows it. */
public final class MachineMotions {
    private static final Map<MachineBlockEntity,WorkEnvelope> MOTIONS=new WeakHashMap<>();
    private MachineMotions() {}
    /** The one canonical sample. A second call with the same clock value changes nothing. */
    public static WorkEnvelope sample(MachineBlockEntity machine,double clock){
        var kind=machine.kind();
        double base=dev.latrox.rematter.content.IndustrialStructure.usesReactorModules(kind)?2:kind==MachineKind.INDUSTRIAL_PREPARATOR?4:1;
        var work=MOTIONS.computeIfAbsent(machine,key->new WorkEnvelope());
        work.sample(clock,machine.getBlockState().getValue(MachineBlock.ACTIVE),machine.visualDuration()>0,machine.visualProgress(),machine.visualDuration(),base);
        return work;
    }
}
