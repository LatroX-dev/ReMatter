package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferStationBlock;
import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlock;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Applies {@link SecurityMode} from a {@link SecurityCardItem} to either a single ReMatter device (Right-Click)
 * or every manageable ReMatter device connected to the same loaded network (Sneak + Right-Click).
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class SecurityCardInteractions {
    public static final int MAX_SWEEP_SCAN = MachineMatterNetwork.SCAN_BUDGET;

    public record SweepResult(int totalOwned, int changed, int skippedOthers) {}

    private SecurityCardInteractions() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof SecurityCardItem)) return;
        InteractionResult result = interact(event.getEntity(), event.getLevel(), event.getPos(), event.getItemStack(), event.getHitVec());
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    public static boolean supportsSecurityCard(Block block) {
        return block instanceof MachineBlock
                || block instanceof EnergyCubeBlock
                || block instanceof CellTransferStationBlock
                || block instanceof WirelessAccessPointBlock
                || block instanceof MatterLevelEmitterBlock
                || block instanceof EntangledLinkBlock
                || block instanceof MachinePortBlock
                || block instanceof MatterCableBlock;
    }

    public static InteractionResult interact(@Nullable Player player, Level level, BlockPos pos, ItemStack stack, BlockHitResult hit) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!supportsSecurityCard(block)) return InteractionResult.PASS;
        if (player == null || player.isSpectator() || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        SecurityMode mode = SecurityCardItem.mode(stack);
        if (player.isShiftKeyDown()) {
            return applyNetwork(player, server, pos, mode);
        }
        if (block instanceof MatterCableBlock) {
            player.sendOverlayMessage(Component.translatable("message.rematter.security_card.cable_hint"));
            return InteractionResult.SUCCESS;
        }
        return applySingle(player, server, pos, hit, mode);
    }

    private static InteractionResult applySingle(Player player, ServerLevel server, BlockPos pos, BlockHitResult hit, SecurityMode mode) {
        BlockEntity be = server.getBlockEntity(pos);
        if (be instanceof MachinePortBlockEntity port) {
            be = port.controller(hit.getDirection());
            if (be == null) be = port.controller(null);
            if (be == null) {
                player.sendOverlayMessage(Component.translatable("message.rematter.security_card.unformed_port"));
                return InteractionResult.FAIL;
            }
        }
        if (be == null || !isOwnedSecurityDevice(be)) return InteractionResult.FAIL;
        if (!canAccess(be, player)) {
            player.sendOverlayMessage(Component.translatable("message.rematter.private_machine"));
            return InteractionResult.FAIL;
        }
        if (!canManage(be, player)) {
            player.sendOverlayMessage(Component.translatable("message.rematter.security.owner_only"));
            return InteractionResult.FAIL;
        }
        if (!applyMode(be, player, mode)) return InteractionResult.FAIL;
        Component name = deviceName(server, be);
        player.sendOverlayMessage(Component.translatable("message.rematter.security_card.applied", name, mode.coloredLabel()));
        ModSounds.play(server, be.getBlockPos(), ModSounds.CONFIG_CARD_WRITE, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult applyNetwork(Player player, ServerLevel server, BlockPos origin, SecurityMode mode) {
        SweepResult result = sweepNetwork(server, player, origin, mode);
        if (result.totalOwned() == 0) {
            player.sendOverlayMessage(Component.translatable(result.skippedOthers() > 0
                    ? "message.rematter.security.owner_only"
                    : "message.rematter.security_card.network_none"));
            return InteractionResult.FAIL;
        }
        if (result.changed() == 0) {
            player.sendOverlayMessage(Component.translatable(
                    "message.rematter.security_card.network_unchanged", result.totalOwned(), mode.coloredLabel()));
            ModSounds.play(server, origin, ModSounds.CONFIG_CARD_READ, 1.0F, 0.95F);
            return InteractionResult.SUCCESS;
        }
        if (result.skippedOthers() > 0) {
            player.sendOverlayMessage(Component.translatable(
                    "message.rematter.security_card.network_applied_skipped",
                    result.changed(), mode.coloredLabel(), result.skippedOthers()));
        } else {
            player.sendOverlayMessage(Component.translatable(
                    "message.rematter.security_card.network_applied", result.changed(), mode.coloredLabel()));
        }
        ModSounds.play(server, origin, ModSounds.CONFIG_CARD_WRITE, 1.0F, 0.92F);
        return InteractionResult.SUCCESS;
    }

    /**
     * Bounded BFS across loaded ReMatter cables, devices, industrial ports, and paired Entangled Links.
     * Only devices where {@code player} is owner or operator are modified; foreign devices are never touched.
     */
    public static SweepResult sweepNetwork(ServerLevel server, Player player, BlockPos origin, SecurityMode mode) {
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        var manageable = Collections.newSetFromMap(new IdentityHashMap<BlockEntity, Boolean>());
        var manageableCables = new ArrayList<BlockPos>();
        var skipped = Collections.newSetFromMap(new IdentityHashMap<BlockEntity, Boolean>());

        BlockPos start = origin.immutable();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty() && visited.size() <= MAX_SWEEP_SCAN) {
            BlockPos current = queue.removeFirst();
            if (server.getChunkSource().getChunkNow(current.getX() >> 4, current.getZ() >> 4) == null) continue;
            BlockState state = server.getBlockState(current);
            Block block = state.getBlock();
            if (!supportsSecurityCard(block)) continue;

            BlockEntity be = server.getBlockEntity(current);
            if (block instanceof MatterCableBlock) {
                var cableOwner = CableOwners.owner(server, current);
                var cableMode = CableOwners.mode(server, current);
                if (!MachineAccess.allows(server, cableOwner, cableMode, player)) continue;
                if (cableOwner != null && MachineAccess.canManage(cableOwner, player)) {
                    manageableCables.add(current);
                }
            } else if (be instanceof MachinePortBlockEntity port) {
                var controller = port.controller(null);
                if (controller != null && !controller.canAccess(player)) {
                    skipped.add(controller);
                    continue;
                }
            } else if (be != null && isOwnedSecurityDevice(be)) {
                if (canManage(be, player)) {
                    manageable.add(be);
                } else {
                    skipped.add(be);
                    if (!canAccess(be, player)) continue;
                }
            } else {
                continue;
            }

            for (BlockPos next : connectedNeighbors(server, player, current, block, be)) {
                BlockPos imm = next.immutable();
                if (visited.size() >= MAX_SWEEP_SCAN) break;
                if (visited.add(imm)) queue.addLast(imm);
            }
        }

        int changed = 0;
        for (BlockEntity device : manageable) {
            SecurityMode before = currentMode(device);
            if (applyMode(device, player, mode) && before != mode) {
                changed++;
            }
        }
        int cablesChanged = 0;
        for (BlockPos cablePos : manageableCables) {
            SecurityMode before = CableOwners.mode(server, cablePos);
            if (CableOwners.setSecurityMode(server, cablePos, player, mode) && before != mode) {
                cablesChanged++;
            }
        }
        int totalOwned = manageable.isEmpty() ? manageableCables.size() : manageable.size();
        int totalChanged = manageable.isEmpty() ? cablesChanged : changed;
        return new SweepResult(totalOwned, totalChanged, skipped.size());
    }

    private static List<BlockPos> connectedNeighbors(
            ServerLevel server, Player player, BlockPos pos, Block block, @Nullable BlockEntity be) {
        var neighbors = new ArrayList<BlockPos>(8);
        if (be instanceof MachineBlockEntity machine && IndustrialStructure.isIndustrial(machine.kind())) {
            var formed = IndustrialStructure.inspect(machine);
            if (formed.formed()) {
                for (var port : formed.ports()) neighbors.add(port.position());
                return neighbors;
            }
        }
        for (Direction dir : Direction.values()) neighbors.add(pos.relative(dir));
        if (be instanceof MachinePortBlockEntity port) {
            var controller = port.controller(null);
            if (controller != null) neighbors.add(controller.getBlockPos());
        } else if (be instanceof EntangledLinkBlockEntity) {
            EntangledLinks.linkedPeerForPlayer(server, pos, player).ifPresent(neighbors::add);
        }
        return neighbors;
    }

    private static boolean isOwnedSecurityDevice(BlockEntity be) {
        return be instanceof MachineBlockEntity
                || be instanceof EnergyCubeBlockEntity
                || be instanceof CellTransferStationBlockEntity
                || be instanceof WirelessAccessPointBlockEntity
                || be instanceof MatterLevelEmitterBlockEntity
                || be instanceof EntangledLinkBlockEntity;
    }

    private static boolean canAccess(BlockEntity be, Player player) {
        if (be instanceof MachineBlockEntity machine) return machine.canAccess(player) && machine.stillValid(player);
        if (be instanceof EnergyCubeBlockEntity cube) return cube.canAccess(player) && !cube.locked();
        if (be instanceof CellTransferStationBlockEntity station) return station.canAccess(player) && !station.locked();
        if (be instanceof WirelessAccessPointBlockEntity ap) return ap.canAccess(player);
        if (be instanceof MatterLevelEmitterBlockEntity emitter) return emitter.canAccess(player);
        if (be instanceof EntangledLinkBlockEntity link) return link.canAccess(player);
        return false;
    }

    private static boolean canManage(BlockEntity be, Player player) {
        if (be instanceof MachineBlockEntity machine) return machine.canManageSecurity(player);
        if (be instanceof EnergyCubeBlockEntity cube) return !cube.locked() && cube.canManageSecurity(player);
        if (be instanceof CellTransferStationBlockEntity station) return !station.locked() && station.canManageSecurity(player);
        if (be instanceof WirelessAccessPointBlockEntity ap) return ap.canManageSecurity(player);
        if (be instanceof MatterLevelEmitterBlockEntity emitter) return emitter.canManageSecurity(player);
        if (be instanceof EntangledLinkBlockEntity link) return link.canManageSecurity(player);
        return false;
    }

    private static SecurityMode currentMode(BlockEntity be) {
        if (be instanceof MachineBlockEntity machine) return machine.securityMode();
        if (be instanceof EnergyCubeBlockEntity cube) return cube.securityMode();
        if (be instanceof CellTransferStationBlockEntity station) return station.securityMode();
        if (be instanceof WirelessAccessPointBlockEntity ap) return ap.securityMode();
        if (be instanceof MatterLevelEmitterBlockEntity emitter) return emitter.securityMode();
        if (be instanceof EntangledLinkBlockEntity link) return link.securityMode();
        return SecurityMode.PUBLIC;
    }

    private static boolean applyMode(BlockEntity be, Player player, SecurityMode mode) {
        if (be instanceof MachineBlockEntity machine) return machine.setSecurityMode(player, mode);
        if (be instanceof EnergyCubeBlockEntity cube) return cube.setSecurityMode(player, mode);
        if (be instanceof CellTransferStationBlockEntity station) return station.setSecurityMode(player, mode);
        if (be instanceof WirelessAccessPointBlockEntity ap) return ap.setSecurityMode(player, mode);
        if (be instanceof MatterLevelEmitterBlockEntity emitter) return emitter.setSecurityMode(player, mode);
        if (be instanceof EntangledLinkBlockEntity link) return link.setSecurityMode(player, mode);
        return false;
    }

    private static Component deviceName(ServerLevel server, BlockEntity be) {
        if (be instanceof MachineBlockEntity machine) return machine.getDisplayName();
        if (be instanceof EnergyCubeBlockEntity cube) return cube.getDisplayName();
        if (be instanceof CellTransferStationBlockEntity station) return station.getDisplayName();
        if (be instanceof WirelessAccessPointBlockEntity ap) return ap.getDisplayName();
        if (be instanceof MatterLevelEmitterBlockEntity emitter) return emitter.getDisplayName();
        return server.getBlockState(be.getBlockPos()).getBlock().getName();
    }
}
