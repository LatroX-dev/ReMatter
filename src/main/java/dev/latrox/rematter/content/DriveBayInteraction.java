package dev.latrox.rematter.content;

import dev.latrox.rematter.primal.PrimalCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Direct cell insertion and extraction on the front 2x3 bay grid of a Matter Drive. */
final class DriveBayInteraction {
    private DriveBayInteraction() {}

    static boolean isCell(ItemStack stack) {
        return !stack.isEmpty() && MachineCellAccess.isMountableBayCell(stack);
    }

    /** Maps a front-face hit to bay 0..5 matching DriveBays (col 0 = viewer-left, rows 0..2 top to bottom). */
    static int hitBay(BlockPos pos, Direction facing, BlockHitResult hit) {
        Vec3 rel = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        Direction left = facing.getClockWise();
        double modelX = (0.5 + (rel.x - 0.5) * left.getStepX() + (rel.z - 0.5) * left.getStepZ()) * 16.0;
        double modelY = rel.y * 16.0;
        int col = modelX >= 8.0 ? 0 : 1;
        int row = modelY >= 9.5 ? 0 : modelY >= 5.5 ? 1 : 2;
        return col * MachineCellAccess.DRIVE_ROWS + row;
    }

    static InteractionResult tryInsert(ItemStack held, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit, MachineBlockEntity machine) {
        Direction facing = state.getValue(MachineBlock.FACING);
        if (machine.kind() != MachineKind.DRIVE || hit.getDirection() != facing || player.isShiftKeyDown() || !isCell(held)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!machine.interact(player) || !machine.stillValid(player) || player.isSpectator()) return InteractionResult.FAIL;

        int preferredBay = hitBay(pos, facing, hit);
        int targetSlot = chooseInsertSlot(machine, preferredBay, held);
        if (targetSlot < 0) return InteractionResult.CONSUME;

        ItemStack single = held.copyWithCount(1);
        if (!machine.canPlaceItem(targetSlot, single)) return InteractionResult.CONSUME;
        machine.setItem(targetSlot, single);
        if (!ItemStack.isSameItemSameComponents(machine.getItem(targetSlot), single)) return InteractionResult.CONSUME;
        if (!player.isCreative()) held.shrink(1);
        ModSounds.play(level, pos, ModSounds.CELL_INSERT);
        return InteractionResult.SUCCESS;
    }

    static boolean tryExtract(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit, MachineBlockEntity machine) {
        Direction facing = state.getValue(MachineBlock.FACING);
        if (machine.kind() != MachineKind.DRIVE || hit.getDirection() != facing || !player.isShiftKeyDown()) return false;
        if (!machine.stillValid(player) || player.isSpectator()) return false;

        int preferredBay = hitBay(pos, facing, hit);
        int targetSlot = chooseExtractSlot(machine, preferredBay);
        if (targetSlot < 0) return false;

        ItemStack removed = machine.removeItem(targetSlot, 1);
        if (removed.isEmpty()) return false;
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, removed);
        } else {
            player.getInventory().placeItemBackInInventory(removed);
        }
        ModSounds.play(level, pos, ModSounds.CELL_EXTRACT);
        return true;
    }

    private static int chooseInsertSlot(MachineBlockEntity machine, int preferredBay, ItemStack held) {
        ItemStack probe = held.copyWithCount(1);
        int preferredSlot = MachineCellAccess.slotForBay(MachineKind.DRIVE, preferredBay);
        if (machine.getItem(preferredSlot).isEmpty() && machine.canPlaceItem(preferredSlot, probe)) return preferredSlot;
        for (int bay = 0; bay < MachineCellAccess.DRIVE_BAYS; bay++) {
            int slot = MachineCellAccess.slotForBay(MachineKind.DRIVE, bay);
            if (machine.getItem(slot).isEmpty() && machine.canPlaceItem(slot, probe)) return slot;
        }
        return -1;
    }

    private static int chooseExtractSlot(MachineBlockEntity machine, int preferredBay) {
        int preferredSlot = MachineCellAccess.slotForBay(MachineKind.DRIVE, preferredBay);
        if (!machine.getItem(preferredSlot).isEmpty() && machine.canRemoveCell(preferredSlot)) return preferredSlot;
        for (int bay = MachineCellAccess.DRIVE_BAYS - 1; bay >= 0; bay--) {
            int slot = MachineCellAccess.slotForBay(MachineKind.DRIVE, bay);
            if (!machine.getItem(slot).isEmpty() && machine.canRemoveCell(slot)) return slot;
        }
        return -1;
    }
}
