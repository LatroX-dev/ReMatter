package dev.latrox.rematter.compat.rs.mixin;

import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity;
import dev.latrox.rematter.compat.rs.RsTaskRecovery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk removal does not call this hook; native task storage remains untouched until actual destruction. */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity", remap = false)
abstract class RsAutocrafterRecoveryMixin {
    @Unique private boolean rematter$matterRecovered;

    @Inject(method = "preRemoveSideEffects", at = @At("HEAD"))
    private void rematter$recoverOwnedMatter(BlockPos position, BlockState oldState, CallbackInfo callback) {
        var machine = (AutocrafterBlockEntity) (Object) this;
        if (rematter$matterRecovered || !(machine.getLevel() instanceof ServerLevel level)
                || !machine.getBlockPos().equals(position) || level.getBlockEntity(position) != machine) return;
        var replacement = level.getBlockState(position);
        if (replacement.is(oldState.getBlock()) || replacement.shouldChangedStateKeepBlockEntity(oldState)) return;
        var recovery = RsTaskRecovery.drops(machine);
        rematter$matterRecovered = true;
        NonNullList<ItemStack> drops = NonNullList.create();
        drops.addAll(recovery);
        Containers.dropContents(level, position, drops);
    }
}
