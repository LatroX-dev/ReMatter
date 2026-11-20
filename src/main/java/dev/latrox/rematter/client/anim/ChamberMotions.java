package dev.latrox.rematter.client.anim;

import dev.latrox.rematter.content.IndustrialModuleBlock;
import dev.latrox.rematter.content.IndustrialModuleBlockEntity;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** One work envelope per chamber centre: its modules move in step, and its sound cues follow the same motion. */
public final class ChamberMotions {
    private static final Map<Level,Map<ChamberFocus,Chamber>> MOTIONS=new WeakHashMap<>();
    private static final Map<IndustrialModuleBlockEntity,ChamberFocus> LAST_FORMED=new WeakHashMap<>();
    private static final class Chamber {
        final WorkEnvelope work=new WorkEnvelope();
        WeakReference<IndustrialModuleBlockEntity> module=new WeakReference<>(null);
    }
    private ChamberMotions() {}
    /** Modules aimed at the same chamber share one phase, even if a different one comes into view later. */
    public static ChamberFocus focus(IndustrialModuleBlockEntity module){
        var facing=module.getBlockState().getValue(IndustrialModuleBlock.FACING);var pos=module.getBlockPos();
        var focus=ChamberFocus.ofModule(pos.getX(),pos.getY(),pos.getZ(),facing.getStepX(),facing.getStepZ(),module.beamTargetX(),module.beamTargetY(),module.beamTargetZ());
        if(module.visuallyFormed())LAST_FORMED.put(module,focus);
        else focus=LAST_FORMED.getOrDefault(module,focus);
        return focus;
    }
    /** The module renderer's sample. The module is remembered, so the chamber can be sampled again while unseen. */
    public static WorkEnvelope sample(IndustrialModuleBlockEntity module,double clock){
        var chamber=MOTIONS.computeIfAbsent(module.getLevel(),key->new LinkedHashMap<>(16,.75F,true){
            @Override protected boolean removeEldestEntry(Map.Entry<ChamberFocus,Chamber> eldest){return size()>4096;}
        }).computeIfAbsent(focus(module),key->new Chamber());
        if(chamber.module.get()!=module)chamber.module=new WeakReference<>(module);
        return sample(chamber.work,module,clock);
    }
    /**
     * The sound manager's sample, through the module the renderer used last and with the values that module shows now.
     * Null while no module of this chamber has been drawn, or once that module is gone or aims elsewhere.
     */
    public static @Nullable WorkEnvelope resample(Level level,ChamberFocus focus,double clock){
        var chambers=MOTIONS.get(level);var chamber=chambers==null?null:chambers.get(focus);
        if(chamber==null)return null;
        var module=chamber.module.get();
        if(module==null||module.isRemoved()||module.getLevel()!=level||!focus.equals(focus(module))){chamber.module.clear();return null;}
        return sample(chamber.work,module,clock);
    }
    private static WorkEnvelope sample(WorkEnvelope work,IndustrialModuleBlockEntity module,double clock){
        boolean emitting=module.getBlockState().getValue(IndustrialModuleBlock.ACTIVE)&&module.visuallyFormed();
        work.sample(clock,emitting,module.visualWorkDuration()>0,module.visualWorkProgress(),module.visualWorkDuration(),module.visualBaseRate());
        return work;
    }
}
