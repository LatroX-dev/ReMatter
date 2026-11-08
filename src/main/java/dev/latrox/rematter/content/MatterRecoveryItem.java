package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/** Emergency transport of already-owned quantities. It is neither a craftable cell nor a matter source. */
public final class MatterRecoveryItem extends Item {
    public MatterRecoveryItem(Properties properties) { super(properties.stacksTo(1)); }
    public static ItemStack create(SubstanceKey key,long amount) {
        if(amount<=0)throw new IllegalArgumentException("Invalid recovered quantity");
        var stack=new ItemStack(ModContent.RECOVERED_MATTER.get());write(stack,key,amount);return stack;
    }
    private static void write(ItemStack stack,SubstanceKey key,long amount) {
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putString("substance",key.id().toString());
        tag.putString("phase",key.phase().name());tag.putLong("amount",amount);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static Optional<SubstanceStack> read(ItemStack stack) {
        try {
            var component=stack.get(DataComponents.CUSTOM_DATA);if(component==null||stack.getCount()!=1)return Optional.empty();
            var tag=component.copyTag();
            if(!tag.keySet().equals(Set.of("schema","substance","phase","amount"))||!(tag.get("schema") instanceof IntTag)
                    ||tag.getIntOr("schema",-1)!=1||!(tag.get("amount") instanceof LongTag))return Optional.empty();
            return Optional.of(new SubstanceStack(SubstanceKey.of(tag.getString("substance").orElseThrow(),Phase.valueOf(tag.getString("phase").orElseThrow())),tag.getLong("amount").orElseThrow()));
        } catch(RuntimeException invalid) {return Optional.empty();}
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();var stack=context.getItemInHand();var value=read(stack);
        if(player==null||value.isEmpty()||!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MachineBlockEntity machine))return InteractionResult.PASS;
        if(context.getLevel().isClientSide())return InteractionResult.SUCCESS;
        long accepted=machine.recoverMatter(player.getUUID(),value.get().key(),value.get().quantity());
        if(accepted<=0)return InteractionResult.FAIL;
        long remaining=value.get().quantity()-accepted;
        if(remaining==0)stack.shrink(1);else write(stack,value.get().key(),remaining);
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> tooltip,TooltipFlag flags) {
        var value=read(stack);
        if(value.isEmpty()){tooltip.accept(Component.translatable("item.rematter.recovered_matter.locked"));return;}
        var substance=value.get();
        tooltip.accept(ChemistryText.row(ChemistryText.catalogue(context.level()),substance.key(),substance.quantity()));
        tooltip.accept(Component.translatable("item.rematter.recovered_matter.hint"));
    }
}
