package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.content.MachineEnergyPolicy;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Industrial sampling returns exactly one unchanged physical item after the Analyzer's research work. */
public record AssayPlan(String itemId,ItemStack sample,long energy,int ticks) {
    public AssayPlan { sample=sample.copy(); }
    @Override public ItemStack sample(){return sample.copy();}
    public static Optional<AssayPlan> create(ChemistryRegistry registry,ItemStack input,boolean alreadyKnown) {
        if(input.isEmpty()||!input.getComponentsPatch().isEmpty())return Optional.empty();
        String id=BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
        var profile=registry.profile(id).orElse(null);if(profile==null)return Optional.empty();
        long energy;int ticks;
        try { energy=alreadyKnown?0:Math.multiplyExact(profile.decompositionEnergy(),2);
            ticks=alreadyKnown?20:Math.multiplyExact(profile.ticks(),4);
        } catch(ArithmeticException invalidPrice) { return Optional.empty(); }
        if(energy>MachineEnergyPolicy.MAX_BATCH_ENERGY||ticks>PreparationWork.MAX_TICKS)return Optional.empty();
        var sample=input.copyWithCount(1);
        return Optional.of(new AssayPlan(id,sample,energy,ticks));
    }
    public boolean matches(ItemStack current){return current.getCount()>=1&&ItemStack.isSameItemSameComponents(sample,current);}
}
