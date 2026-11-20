package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferStationBlock;
import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlock;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/** Handles Matter Wrench mode dispatch (Ports / Rotate / Dismantle) and generic #c:tools/wrench compatibility. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class WrenchInteractions {
    private WrenchInteractions() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof MatterWrenchItem) && !stack.is(Tags.Items.TOOLS_WRENCH)) return;
        InteractionResult result = interact(event.getEntity(), event.getLevel(), event.getPos(), stack, event.getHitVec());
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    public static boolean wrenchable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof MachineBlock
                || block instanceof EnergyCubeBlock
                || block instanceof CellTransferStationBlock
                || block instanceof WirelessAccessPointBlock
                || block instanceof MatterLevelEmitterBlock
                || block instanceof MatterCableBlock
                || block instanceof MachinePortBlock
                || block instanceof IndustrialModuleBlock
                || block instanceof EntangledLinkBlock
                || ModContent.REACTOR_CASING.isBound() && state.is(ModContent.REACTOR_CASING.get())
                || ModContent.REACTOR_GLASS.isBound() && state.is(ModContent.REACTOR_GLASS.get());
    }

    public static InteractionResult interact(@Nullable Player player, Level level, BlockPos pos, ItemStack stack, BlockHitResult hit) {
        if (stack.getItem() instanceof MatterWrenchItem && player != null && !player.isSpectator() && player.isShiftKeyDown()) {
            if (!level.isClientSide()) MatterWrenchItem.cycleMode(stack, player, level);
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(pos);
        if (!wrenchable(state)) return InteractionResult.PASS;
        if (player == null || player.isSpectator() || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
            return InteractionResult.FAIL;
        }
        if (stack.getItem() instanceof MatterWrenchItem) {
            return switch (MatterWrenchItem.mode(stack)) {
                case PORTS -> configurePortsOnly(player, level, pos, state, hit);
                case ROTATE -> rotateOnly(player, level, pos, state);
                case DISMANTLE -> level instanceof ServerLevel server ? dismantle(player, server, pos, state, stack) : InteractionResult.SUCCESS;
            };
        }
        if (player.isShiftKeyDown()) {
            return level instanceof ServerLevel server ? dismantle(player, server, pos, state, stack) : InteractionResult.SUCCESS;
        }
        return configureOrRotate(player, level, pos, state, hit);
    }

    private static InteractionResult configurePortsOnly(Player player, Level level, BlockPos pos, BlockState state, BlockHitResult hit) {
        Block block = state.getBlock();
        if (block instanceof MachineBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof MachineBlockEntity machine) || !machine.interact(player) || !machine.stillValid(player)) {
                return InteractionResult.FAIL;
            }
            if (IndustrialStructure.isIndustrial(machine.kind())) {
                if (!IndustrialStructure.inspect(machine).formed()) {
                    IndustrialAutoBuilder.build(server, player, machine);
                    return InteractionResult.SUCCESS;
                }
                player.sendOverlayMessage(Component.translatable("message.rematter.wrench_no_ports"));
                return InteractionResult.FAIL;
            }
            if (!MachinePortMode.configurable(machine.kind())) {
                player.sendOverlayMessage(Component.translatable("message.rematter.wrench_no_ports"));
                return InteractionResult.FAIL;
            }
            Direction front = state.getValue(MachineBlock.FACING);
            var side = MachinePortMode.Side.local(front, hit.getDirection());
            var closed = PortSides.closed(machine.kind(), side);
            if (closed != null) {
                player.sendOverlayMessage(Component.translatable("port.rematter.closed." + closed.name().toLowerCase(java.util.Locale.ROOT)));
                return InteractionResult.SUCCESS;
            }
            if (!machine.cyclePort(player, hit.getDirection())) return InteractionResult.FAIL;
            player.sendSystemMessage(Component.translatable("port.rematter.side_mode", side.label(), machine.portMode(side).label()));
            ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof EnergyCubeBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof EnergyCubeBlockEntity cube) || !cube.interact(player)) {
                return InteractionResult.FAIL;
            }
            var side = MachinePortMode.Side.local(state.getValue(EnergyCubeBlock.FACING), hit.getDirection());
            if (!cube.cycleSide(player, side)) return InteractionResult.FAIL;
            player.sendSystemMessage(Component.translatable("port.rematter.side_mode", side.label(), cube.sideMode(side).label()));
            ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof MachinePortBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof MachinePortBlockEntity port)) return InteractionResult.FAIL;
            var controller = port.controller(hit.getDirection());
            if (controller == null) controller = port.controller(null);
            if (controller == null) {
                player.sendOverlayMessage(Component.translatable("message.rematter.wrench_no_ports"));
                return InteractionResult.FAIL;
            }
            Direction outward = port.outward();
            if (!controller.interact(player) || outward == null || !port.cycleMode(player, outward)) return InteractionResult.FAIL;
            player.sendSystemMessage(Component.translatable("port.rematter.mode_message", port.mode().label()));
            ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.rematter.wrench_no_ports"));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult rotateOnly(Player player, Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof MachineBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof MachineBlockEntity machine) || !machine.interact(player) || !machine.stillValid(player)) {
                return InteractionResult.FAIL;
            }
            if (!machine.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction next = state.getValue(MachineBlock.FACING).getClockWise();
            server.setBlock(pos, state.setValue(MachineBlock.FACING, next), Block.UPDATE_ALL);
            machine.afterRotation(server);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof EnergyCubeBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof EnergyCubeBlockEntity cube) || !cube.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!cube.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction next = state.getValue(EnergyCubeBlock.FACING).getClockWise();
            server.setBlock(pos, state.setValue(EnergyCubeBlock.FACING, next), Block.UPDATE_ALL);
            cube.afterRotation(server);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof WirelessAccessPointBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof WirelessAccessPointBlockEntity ap) || !ap.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!ap.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction next = state.getValue(WirelessAccessPointBlock.FACING).getClockWise();
            server.setBlock(pos, state.setValue(WirelessAccessPointBlock.FACING, next), Block.UPDATE_ALL);
            ap.afterRotation(server);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof MatterLevelEmitterBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof MatterLevelEmitterBlockEntity emitter) || !emitter.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!emitter.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction previous = state.getValue(MatterLevelEmitterBlock.FACING);
            Direction next = previous.getClockWise();
            server.setBlock(pos, state.setValue(MatterLevelEmitterBlock.FACING, next), Block.UPDATE_ALL);
            emitter.afterRotation(server, previous);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof MachinePortBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof MachinePortBlockEntity port)) return InteractionResult.FAIL;
            if (port.controller(null) != null) {
                player.sendOverlayMessage(Component.translatable("message.rematter.wrench_not_rotatable"));
                return InteractionResult.FAIL;
            }
            if (IndustrialStructure.controllersUsing(server, pos).stream().anyMatch(m -> !m.canRemove(player))) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction next = Direction.values()[(state.getValue(MachinePortBlock.FACING).ordinal() + 1) % Direction.values().length];
            server.setBlock(pos, state.setValue(MachinePortBlock.FACING, next), Block.UPDATE_ALL);
            ChunkRevisions.touchLoaded(server, pos);
            MatterCableBlock.refreshLoadedAround(server, pos);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof CellTransferStationBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station) || !station.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!station.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            server.setBlock(pos, state.setValue(CellTransferStationBlock.FACING, state.getValue(CellTransferStationBlock.FACING).getClockWise()), Block.UPDATE_ALL);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof IndustrialModuleBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (IndustrialStructure.controllersUsing(server, pos).stream().anyMatch(m -> !m.canRemove(player))) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            server.setBlock(pos, state.setValue(IndustrialModuleBlock.FACING, state.getValue(IndustrialModuleBlock.FACING).getClockWise()), Block.UPDATE_ALL);
            ChunkRevisions.touchLoaded(server, pos);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.rematter.wrench_not_rotatable"));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult configureOrRotate(Player player, Level level, BlockPos pos, BlockState state, BlockHitResult hit) {
        Block block = state.getBlock();
        if (block instanceof MachineBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof MachineBlockEntity machine) || !machine.interact(player) || !machine.stillValid(player)) {
                return InteractionResult.FAIL;
            }
            Direction front = state.getValue(MachineBlock.FACING);
            var side = MachinePortMode.Side.local(front, hit.getDirection());
            if (MachinePortMode.configurable(machine.kind()) && PortSides.available(machine.kind(), side)) {
                if (!machine.cyclePort(player, hit.getDirection())) return InteractionResult.FAIL;
                player.sendSystemMessage(Component.translatable("port.rematter.side_mode", side.label(), machine.portMode(side).label()));
                ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
                return InteractionResult.SUCCESS;
            }
            return rotateOnly(player, level, pos, state);
        }
        if (block instanceof EnergyCubeBlock) {
            return configurePortsOnly(player, level, pos, state, hit);
        }
        if (block instanceof MachinePortBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (server.getBlockEntity(pos) instanceof MachinePortBlockEntity port
                    && (port.controller(hit.getDirection()) != null || port.controller(null) != null)) {
                return configurePortsOnly(player, level, pos, state, hit);
            }
            return rotateOnly(player, level, pos, state);
        }
        if (block instanceof CellTransferStationBlock || block instanceof IndustrialModuleBlock
                || block instanceof WirelessAccessPointBlock || block instanceof MatterLevelEmitterBlock) {
            return rotateOnly(player, level, pos, state);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult dismantle(Player player, ServerLevel server, BlockPos pos, BlockState state, ItemStack wrench) {
        BlockEntity be = server.getBlockEntity(pos);
        if (!canDismantle(player, server, pos, state, be)) return InteractionResult.FAIL;
        BreakBlockEvent breakEvent = NeoForge.EVENT_BUS.post(new BreakBlockEvent(server, pos, state, player));
        if (breakEvent.isCanceled()) return InteractionResult.FAIL;

        List<ItemStack> drops = Block.getDrops(state, server, pos, be, player, wrench);
        boolean keepDrops = !player.isCreative() || hasPortableState(be);
        if (!server.removeBlock(pos, false)) return InteractionResult.FAIL;
        if (keepDrops) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) player.getInventory().placeItemBackInInventory(drop.copy());
            }
        }
        ModSounds.play(server, pos, ModSounds.WRENCH_DISMANTLE, 1.0F, 0.97F + server.getRandom().nextFloat() * 0.06F);
        return InteractionResult.SUCCESS;
    }

    private static boolean canDismantle(Player player, ServerLevel server, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
        if (state.getBlock() instanceof MatterCableBlock && !CableOwners.canRemove(server, pos, player)) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        if (be instanceof MachineBlockEntity machine) {
            if (!machine.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return false;
            }
            if (!machine.canRemoveCell()) return false;
        }
        if (be instanceof EnergyCubeBlockEntity cube && !cube.canRemove(player)) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        if (be instanceof CellTransferStationBlockEntity station && !station.canRemove(player)) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        if (be instanceof WirelessAccessPointBlockEntity ap && !ap.canRemove(player)) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        if (be instanceof MatterLevelEmitterBlockEntity emitter && !emitter.canRemove(player)) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        if (be instanceof EntangledLinkBlockEntity link && !link.canRemove(player)) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        if ((state.is(ModContent.REACTOR_CASING.get()) || state.is(ModContent.REACTOR_GLASS.get())
                || state.is(ModContent.MACHINE_PORT.get()) || state.getBlock() instanceof IndustrialModuleBlock)
                && IndustrialStructure.controllersUsing(server, pos).stream().anyMatch(m -> !m.canRemove(player))) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        return true;
    }

    private static boolean hasPortableState(@Nullable BlockEntity be) {
        return be instanceof MachineBlockEntity m && m.hasStoredResources()
                || be instanceof EnergyCubeBlockEntity c && c.hasStoredContents()
                || be instanceof CellTransferStationBlockEntity s && s.hasStoredContents()
                || be instanceof WirelessAccessPointBlockEntity ap && !ap.isEmpty()
                || be instanceof MachinePortBlockEntity p && p.mode() != MachinePortMode.AUTOMATIC;
    }
}
