package dev.latrox.rematter.primal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EntangledLinkBlock extends Block implements EntityBlock {
    public EntangledLinkBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EntangledLinkBlockEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer) && level.getBlockEntity(pos) instanceof EntangledLinkBlockEntity link) link.claim(player.getUUID());
    }
}
