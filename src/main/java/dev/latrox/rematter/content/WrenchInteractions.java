package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferStationBlock;
import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyMatrixCellBlock;
import dev.latrox.rematter.energy.EnergyMatrixProviderBlock;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlock;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlock;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import java.util.ArrayList;
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
                || block instanceof MatterCableRelayBlock
                || block instanceof SpatialAnchorBlock
                || block instanceof MatterCableBlock
                || block instanceof MachinePortBlock
                || block instanceof IndustrialModuleBlock
                || block instanceof EnergyMatrixCellBlock
                || block instanceof EnergyMatrixProviderBlock
                || block instanceof EntangledLinkBlock
                || block instanceof DimensionalEntangledLinkBlock
                || ModContent.isAnyReactorCasing(state)
                || ModContent.isAnyReactorGlass(state);
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
        if (block instanceof EntangledLinkBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof EntangledLinkBlockEntity link) || !link.interact(player)) {
                return InteractionResult.FAIL;
            }
            var side = MachinePortMode.Side.local(state.getValue(EntangledLinkBlock.FACING), hit.getDirection());
            if (!EntangledLinkBlockEntity.isConfigurableSide(side)) {
                player.sendOverlayMessage(Component.translatable(side == MachinePortMode.Side.FRONT ? "port.rematter.closed.front" : "port.rematter.closed.emitter_top"));
                return InteractionResult.SUCCESS;
            }
            if (!link.cyclePort(player, side, true)) return InteractionResult.FAIL;
            player.sendSystemMessage(Component.translatable("port.rematter.side_mode", side.label(), link.portMode(side).label()));
            ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof DimensionalEntangledLinkBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof DimensionalEntangledLinkBlockEntity dlink) || !dlink.interact(player)) {
                return InteractionResult.FAIL;
            }
            var side = MachinePortMode.Side.local(state.getValue(DimensionalEntangledLinkBlock.FACING), hit.getDirection());
            if (!DimensionalEntangledLinkBlockEntity.isConfigurableSide(side)) {
                player.sendOverlayMessage(Component.translatable(side == MachinePortMode.Side.FRONT ? "port.rematter.closed.front" : "port.rematter.closed.emitter_top"));
                return InteractionResult.SUCCESS;
            }
            if (!dlink.cyclePort(player, side, true)) return InteractionResult.FAIL;
            player.sendSystemMessage(Component.translatable("port.rematter.side_mode", side.label(), dlink.portMode(side).label()));
            ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof SpatialAnchorBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof SpatialAnchorBlockEntity anchor) || !anchor.interact(player)) {
                return InteractionResult.FAIL;
            }
            var side = MachinePortMode.Side.local(state.getValue(SpatialAnchorBlock.FACING), hit.getDirection());
            if (!SpatialAnchorBlockEntity.isConfigurableSide(side)) {
                player.sendOverlayMessage(Component.translatable(side == MachinePortMode.Side.FRONT ? "port.rematter.closed.front" : "port.rematter.closed.emitter_top"));
                return InteractionResult.SUCCESS;
            }
            if (!anchor.cyclePort(player, side, true)) return InteractionResult.FAIL;
            player.sendSystemMessage(Component.translatable("port.rematter.side_mode", side.label(), anchor.portMode(side).label()));
            ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof MatterCableBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            return MatterCableBlock.toggleSideInteraction(player, server, pos, hit);
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
        if (block instanceof MatterCableRelayBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof MatterCableRelayBlockEntity valve) || !valve.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!valve.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction cur = state.getValue(MatterCableRelayBlock.FACING);
            Direction next;
            if (valve.isOneWay()) {
                next = Direction.values()[(cur.ordinal() + 1) % Direction.values().length];
            } else {
                next = switch (cur.getAxis()) {
                    case Z -> Direction.EAST;
                    case X -> Direction.UP;
                    case Y -> Direction.NORTH;
                };
            }
            server.setBlock(pos, state.setValue(MatterCableRelayBlock.FACING, next), Block.UPDATE_ALL);
            valve.afterRotation(server);
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
        if (block instanceof EntangledLinkBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof EntangledLinkBlockEntity link) || !link.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!link.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction next = state.getValue(EntangledLinkBlock.FACING).getClockWise();
            server.setBlock(pos, state.setValue(EntangledLinkBlock.FACING, next), Block.UPDATE_ALL);
            link.afterRotation(server);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof DimensionalEntangledLinkBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof DimensionalEntangledLinkBlockEntity dlink) || !dlink.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!dlink.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction next = state.getValue(DimensionalEntangledLinkBlock.FACING).getClockWise();
            server.setBlock(pos, state.setValue(DimensionalEntangledLinkBlock.FACING, next), Block.UPDATE_ALL);
            dlink.afterRotation(server);
            ModSounds.play(server, pos, ModSounds.WRENCH_ROTATE, 1.0F, 0.96F + server.getRandom().nextFloat() * 0.08F);
            return InteractionResult.SUCCESS;
        }
        if (block instanceof SpatialAnchorBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!(server.getBlockEntity(pos) instanceof SpatialAnchorBlockEntity anchor) || !anchor.interact(player)) {
                return InteractionResult.FAIL;
            }
            if (!anchor.canRemove(player)) {
                player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
                return InteractionResult.FAIL;
            }
            Direction next = state.getValue(SpatialAnchorBlock.FACING).getClockWise();
            server.setBlock(pos, state.setValue(SpatialAnchorBlock.FACING, next), Block.UPDATE_ALL);
            anchor.afterRotation(server);
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
        if (block instanceof EntangledLinkBlock) {
            var side = MachinePortMode.Side.local(state.getValue(EntangledLinkBlock.FACING), hit.getDirection());
            return EntangledLinkBlockEntity.isConfigurableSide(side) ? configurePortsOnly(player, level, pos, state, hit) : rotateOnly(player, level, pos, state);
        }
        if (block instanceof DimensionalEntangledLinkBlock) {
            var side = MachinePortMode.Side.local(state.getValue(DimensionalEntangledLinkBlock.FACING), hit.getDirection());
            return DimensionalEntangledLinkBlockEntity.isConfigurableSide(side) ? configurePortsOnly(player, level, pos, state, hit) : rotateOnly(player, level, pos, state);
        }
        if (block instanceof SpatialAnchorBlock) {
            var side = MachinePortMode.Side.local(state.getValue(SpatialAnchorBlock.FACING), hit.getDirection());
            return SpatialAnchorBlockEntity.isConfigurableSide(side) ? configurePortsOnly(player, level, pos, state, hit) : rotateOnly(player, level, pos, state);
        }
        if (block instanceof MachinePortBlock) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (server.getBlockEntity(pos) instanceof MachinePortBlockEntity port
                    && (port.controller(hit.getDirection()) != null || port.controller(null) != null)) {
                return configurePortsOnly(player, level, pos, state, hit);
            }
            return rotateOnly(player, level, pos, state);
        }
        if (block instanceof MatterCableBlock) {
            return configurePortsOnly(player, level, pos, state, hit);
        }
        if (block instanceof CellTransferStationBlock || block instanceof IndustrialModuleBlock
                || block instanceof WirelessAccessPointBlock || block instanceof MatterLevelEmitterBlock
                || block instanceof MatterCableRelayBlock) {
            return rotateOnly(player, level, pos, state);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult dismantle(Player player, ServerLevel server, BlockPos pos, BlockState state, ItemStack wrench) {
        BlockEntity be = server.getBlockEntity(pos);
        if (be instanceof MachineBlockEntity machine && IndustrialStructure.isIndustrial(machine.kind())) {
            var formation = IndustrialStructure.inspect(machine);
            if (formation.formed() && formation.shape() != null) {
                return dismantleFormedStructure(player, server, pos, state, machine, formation, wrench);
            }
        }
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

    private record DismantleEntry(BlockPos pos, BlockState state, @Nullable BlockEntity be, boolean isPort) {}

    private static InteractionResult dismantleFormedStructure(
            Player player,
            ServerLevel server,
            BlockPos controllerPos,
            BlockState controllerState,
            MachineBlockEntity controller,
            IndustrialStructure.Formation formation,
            ItemStack wrench) {
        if (!canDismantle(player, server, controllerPos, controllerState, controller)) return InteractionResult.FAIL;
        var shape = formation.shape();
        if (shape == null) return InteractionResult.FAIL;
        Direction facing = controllerState.getValue(MachineBlock.FACING);

        var interiorEntries = new ArrayList<DismantleEntry>();
        var shellEntries = new ArrayList<DismantleEntry>();
        var exactLayout = new java.util.LinkedHashMap<Long, IndustrialStructure.Material>();
        boolean anyGlass = false;
        boolean verticalGlass = false;

        for (var cell : IndustrialStructure.layout(shape)) {
            if (cell.role() == IndustrialStructure.Role.CONTROLLER) continue;
            BlockPos cellPos = IndustrialStructure.worldPosition(controllerPos, facing, cell).immutable();
            if (!server.hasChunkAt(cellPos)) return InteractionResult.FAIL;
            BlockState cellState = server.getBlockState(cellPos);
            if (cellState.isAir()) continue;
            if (!wrenchable(cellState)) return InteractionResult.FAIL;
            BlockEntity cellBe = server.getBlockEntity(cellPos);
            if (!canDismantle(player, server, cellPos, cellState, cellBe)) return InteractionResult.FAIL;
            var exactMat = IndustrialStructure.materialAt(server, controller, cellPos);
            if (exactMat != IndustrialStructure.Material.AIR
                    && exactMat != IndustrialStructure.Material.CONTROLLER
                    && exactMat != IndustrialStructure.Material.OTHER
                    && exactMat != IndustrialStructure.Material.UNLOADED) {
                exactLayout.put(IndustrialBlueprint.localCoordKey(cell.x(), cell.y(), cell.depth()), exactMat);
            }
            if (ModContent.isAnyReactorGlass(cellState)) {
                anyGlass = true;
                if (cell.outward() != null && cell.outward().getAxis().isVertical()) verticalGlass = true;
            }
            var entry = new DismantleEntry(cellPos, cellState, cellBe, cellState.getBlock() instanceof MachinePortBlock);
            if (cell.role() == IndustrialStructure.Role.INTERIOR) interiorEntries.add(entry);
            else shellEntries.add(entry);
        }

        // Validate BreakBlockEvent atomically across the entire structure before removing any block.
        if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(server, controllerPos, controllerState, player)).isCanceled()) {
            return InteractionResult.FAIL;
        }
        for (var entry : interiorEntries) {
            if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(server, entry.pos, entry.state, player)).isCanceled()) {
                return InteractionResult.FAIL;
            }
        }
        for (var entry : shellEntries) {
            if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(server, entry.pos, entry.state, player)).isCanceled()) {
                return InteractionResult.FAIL;
            }
        }

        // Re-validate live world states after BreakBlockEvent hooks before mutating any block or awarding drops.
        if (!server.getBlockState(controllerPos).equals(controllerState) || server.getBlockEntity(controllerPos) != controller) {
            return InteractionResult.FAIL;
        }
        for (var entry : interiorEntries) {
            if (!server.getBlockState(entry.pos).equals(entry.state)) return InteractionResult.FAIL;
        }
        for (var entry : shellEntries) {
            if (!server.getBlockState(entry.pos).equals(entry.state)) return InteractionResult.FAIL;
        }

        // Preserve exact layout (including Matrix Cell/Provider tiers, mixed glass & ports) on the controller item.
        var detectedStyle = !anyGlass ? IndustrialBlueprint.ShellStyle.FULL_CASING
                : verticalGlass ? IndustrialBlueprint.ShellStyle.ALL_GLASS
                : IndustrialBlueprint.ShellStyle.SIDES_GLASS;
        controller.setIndustrialBlueprint(IndustrialBlueprint.fromFormation(controller.kind(), formation)
                .withShellStyle(detectedStyle)
                .withExactLayout(exactLayout));

        // Remove interior -> shell -> controller, awarding drops strictly for blocks whose removal actually succeeded.
        var allDrops = new ArrayList<ItemStack>();
        int removed = 0;
        for (var entry : interiorEntries) {
            BlockEntity liveBe = server.getBlockEntity(entry.pos);
            List<ItemStack> drops = (!player.isCreative() || hasPortableState(liveBe))
                    ? Block.getDrops(entry.state, server, entry.pos, liveBe, player, wrench)
                    : List.of();
            if (server.removeBlock(entry.pos, false)) {
                collectStackedDrops(allDrops, drops);
                ChunkRevisions.touchLoaded(server, entry.pos);
                removed++;
            }
        }
        for (var entry : shellEntries) {
            BlockEntity liveBe = server.getBlockEntity(entry.pos);
            List<ItemStack> drops = (!player.isCreative() || hasPortableState(liveBe))
                    ? Block.getDrops(entry.state, server, entry.pos, liveBe, player, wrench)
                    : List.of();
            if (server.removeBlock(entry.pos, false)) {
                collectStackedDrops(allDrops, drops);
                ChunkRevisions.touchLoaded(server, entry.pos);
                if (entry.isPort) MatterCableBlock.refreshLoadedAround(server, entry.pos);
                removed++;
            }
        }
        List<ItemStack> controllerDrops = (!player.isCreative() || hasPortableState(controller))
                ? Block.getDrops(controllerState, server, controllerPos, controller, player, wrench)
                : List.of();
        if (server.removeBlock(controllerPos, false)) {
            collectStackedDrops(allDrops, controllerDrops);
            ChunkRevisions.touchLoaded(server, controllerPos);
            removed++;
        }

        for (ItemStack drop : allDrops) {
            if (!drop.isEmpty()) player.getInventory().placeItemBackInInventory(drop.copy());
        }
        player.sendOverlayMessage(Component.translatable("message.rematter.wrench_dismantled_structure",
                controllerState.getBlock().getName(), removed));
        ModSounds.play(server, controllerPos, ModSounds.WRENCH_DISMANTLE, 1.0F, 0.92F + server.getRandom().nextFloat() * 0.06F);
        return InteractionResult.SUCCESS;
    }

    private static void collectStackedDrops(List<ItemStack> merged, List<ItemStack> incoming) {
        for (ItemStack raw : incoming) {
            if (raw.isEmpty()) continue;
            ItemStack remaining = raw.copy();
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, remaining) && existing.getCount() < existing.getMaxStackSize()) {
                    int add = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(add);
                    remaining.shrink(add);
                    if (remaining.isEmpty()) break;
                }
            }
            if (!remaining.isEmpty()) merged.add(remaining);
        }
    }

    private static boolean canDismantle(Player player, ServerLevel server, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
        if (!MachineProtection.canBreak(server, pos, state, be, player)) {
            boolean cellBlocked = be instanceof MachineBlockEntity machine && machine.canRemove(player) && !machine.canRemoveCell();
            if (!cellBlocked) player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
            return false;
        }
        return true;
    }

    private static boolean hasPortableState(@Nullable BlockEntity be) {
        return be instanceof MachineBlockEntity m && m.hasStoredResources()
                || be instanceof EnergyCubeBlockEntity c && c.hasStoredContents()
                || be instanceof CellTransferStationBlockEntity s && s.hasStoredContents()
                || be instanceof WirelessAccessPointBlockEntity ap && !ap.isEmpty()
                || be instanceof DimensionalEntangledLinkBlockEntity d && !d.isEmpty()
                || be instanceof MachinePortBlockEntity p && p.mode() != MachinePortMode.AUTOMATIC;
    }
}
