package dev.latrox.rematter.content;

import dev.latrox.rematter.network.StructureHintPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Places missing shell and interior module blocks for an unformed Industrial Chemical Reactor or
 * Industrial Preparator controller directly from the player's inventory, obeying protection and collision rules.
 * Aborts atomically without placing any blocks when foreign or non-replaceable blocks obstruct full assembly.
 */
public final class IndustrialAutoBuilder {
    private IndustrialAutoBuilder() {}

    public record Result(int placed, int missingItems, int obstructed, boolean formed) {
        public static final Result DENIED = new Result(0, 0, 0, false);
    }

    public static Result build(ServerLevel level, Player player, MachineBlockEntity controller) {
        if (!IndustrialStructure.isIndustrial(controller.kind())
                || player.isSpectator()
                || !controller.interact(player)
                || !controller.canRemove(player)) {
            return Result.DENIED;
        }
        if (IndustrialStructure.inspect(controller).formed()) {
            return new Result(0, 0, 0, true);
        }

        Direction facing = controller.getBlockState().getValue(MachineBlock.FACING);
        BlockPos origin = controller.getBlockPos();
        IndustrialBlueprint blueprint = controller.industrialBlueprint();
        IndustrialStructure.Materials blocks = (x, y, depth) -> IndustrialStructure.materialAt(
                level,
                controller,
                IndustrialStructure.worldPosition(origin, facing, new IndustrialStructure.Cell(x, y, depth, IndustrialStructure.Role.INTERIOR, null)));

        var obstructedPositions = new LinkedHashSet<BlockPos>();
        for (var cell : IndustrialStructure.layout(blueprint.shape())) {
            BlockPos cellPos = IndustrialStructure.worldPosition(origin, facing, cell);
            if (!level.hasChunkAt(cellPos) || claimedByAnotherChamber(level, player, controller, cellPos)) {
                obstructedPositions.add(cellPos.immutable());
            }
        }

        List<IndustrialStructure.LocalMismatch> mismatches = blueprint.collectAllMismatches(blocks);
        if (mismatches.isEmpty() && obstructedPositions.isEmpty()) {
            return new Result(0, 0, 0, IndustrialStructure.inspect(controller).formed());
        }

        List<IndustrialStructure.LocalMismatch> shellFirst = new ArrayList<>(mismatches.size());
        List<IndustrialStructure.LocalMismatch> modulesSecond = new ArrayList<>();
        var plannedMaterials = new HashMap<Long, IndustrialStructure.Material>(mismatches.size());

        for (var mismatch : mismatches) {
            BlockPos targetPos = IndustrialStructure.worldPosition(origin, facing, mismatch.cell());
            var expected = mismatch.expected();
            if (expected == IndustrialStructure.Material.AIR
                    || expected == IndustrialStructure.Material.CONTROLLER
                    || expected == IndustrialStructure.Material.OTHER
                    || expected == IndustrialStructure.Material.UNLOADED) {
                obstructedPositions.add(targetPos.immutable());
                continue;
            }
            if (!level.hasChunkAt(targetPos)) {
                obstructedPositions.add(targetPos.immutable());
                continue;
            }
            BlockState existing = level.getBlockState(targetPos);
            if (!existing.isAir() && !(existing.canBeReplaced() && existing.getFluidState().isEmpty())) {
                obstructedPositions.add(targetPos.immutable());
                continue;
            }
            Item requiredItem = itemForMaterial(expected);
            BlockState desired = desiredState(facing, mismatch);
            if (requiredItem == null || desired == null) {
                obstructedPositions.add(targetPos.immutable());
                continue;
            }
            if (!level.mayInteract(player, targetPos)
                    || !player.mayUseItemAt(targetPos, Direction.UP, new ItemStack(requiredItem))
                    || !level.isUnobstructed(desired, targetPos, CollisionContext.empty())) {
                obstructedPositions.add(targetPos.immutable());
                continue;
            }
            plannedMaterials.put(cellKey(mismatch.cell()), expected);
            if (expected.module()) {
                modulesSecond.add(mismatch);
            } else {
                shellFirst.add(mismatch);
            }
        }

        if (obstructedPositions.isEmpty()) {
            IndustrialStructure.Materials simulated = (x, y, depth) ->
                    plannedMaterials.getOrDefault(cellKey(x, y, depth), blocks.at(x, y, depth));
            var postInspection = IndustrialStructure.inspectLayout(controller.kind(), blueprint.shape(), simulated);
            if (!postInspection.formed()) {
                BlockPos errorPos = postInspection.errorCell() != null
                        ? IndustrialStructure.worldPosition(origin, facing, postInspection.errorCell())
                        : origin;
                obstructedPositions.add(errorPos.immutable());
            }
        }

        if (!obstructedPositions.isEmpty()) {
            if (!controller.structurePreviewEnabled()) {
                controller.setStructurePreviewEnabled(true);
            }
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                int cid = serverPlayer.containerMenu instanceof MachineMenu menu ? menu.containerId : 0;
                PacketDistributor.sendToPlayer(serverPlayer, IndustrialStructure.hintFor(cid, controller));
            }
            Component warning = formatObstructionWarning(level, obstructedPositions);
            player.sendOverlayMessage(warning);
            player.sendSystemMessage(warning);
            ModSounds.play(level, origin, ModSounds.ACTION_DENIED);
            return new Result(0, 0, obstructedPositions.size(), false);
        }

        shellFirst.addAll(modulesSecond);

        int placed = 0;
        int missingItems = 0;
        for (var mismatch : shellFirst) {
            BlockPos targetPos = IndustrialStructure.worldPosition(origin, facing, mismatch.cell());
            Item requiredItem = itemForMaterial(mismatch.expected());
            BlockState desired = desiredState(facing, mismatch);
            if (requiredItem == null || desired == null) {
                continue;
            }
            if (mismatch.expected().module() && !hasModuleSupport(level, targetPos, mismatch.orientation())) {
                missingItems++;
                continue;
            }
            int slot = -1;
            if (!player.isCreative()) {
                slot = findInventorySlot(player.getInventory(), requiredItem);
                if (slot < 0) {
                    missingItems++;
                    continue;
                }
            }
            if (!level.setBlock(targetPos, desired, Block.UPDATE_ALL)) {
                missingItems++;
                continue;
            }
            if (slot >= 0) {
                player.getInventory().removeItem(slot, 1);
            }
            ChunkRevisions.touchLoaded(level, targetPos);
            if (mismatch.expected() == IndustrialStructure.Material.PORT) {
                MatterCableBlock.refreshLoadedAround(level, targetPos);
            }
            placed++;
        }

        if (placed > 0) {
            ChunkRevisions.touchLoaded(level, origin);
            player.getInventory().setChanged();
            controller.serverTick();
            for (var port : IndustrialStructure.inspect(controller).ports()) {
                if (level.getBlockEntity(port.position()) instanceof MachinePortBlockEntity portEntity) {
                    portEntity.refreshNow();
                }
            }
        }

        var finalFormation = IndustrialStructure.inspect(controller);
        boolean formed = finalFormation.formed();
        if (formed) {
            controller.setStructurePreviewEnabled(false);
            if (player instanceof ServerPlayer serverPlayer) {
                int cid = serverPlayer.containerMenu instanceof MachineMenu mm ? mm.containerId : 0;
                PacketDistributor.sendToPlayer(serverPlayer, StructureHintPayload.fromFormation(cid, finalFormation, false));
            }
            player.sendOverlayMessage(Component.translatable("message.rematter.auto_build.complete", placed));
            ModSounds.play(level, origin, ModSounds.STRUCTURE_FORMED);
        } else if (placed > 0) {
            player.sendOverlayMessage(Component.translatable("message.rematter.auto_build.partial", placed, Math.max(1, missingItems)));
        } else {
            Component missingMsg = Component.translatable("message.rematter.auto_build.missing_items").withStyle(ChatFormatting.GOLD);
            player.sendOverlayMessage(missingMsg);
            player.sendSystemMessage(missingMsg);
        }
        return new Result(placed, missingItems, 0, formed);
    }

    private static boolean claimedByAnotherChamber(
            ServerLevel level, Player player, MachineBlockEntity controller, BlockPos pos) {
        for (var other : IndustrialStructure.controllersUsing(level, pos)) {
            if (other == controller) continue;
            if (IndustrialStructure.inspect(other).formed()
                    || !other.canRemove(player)
                    || !MachineAccess.canConnect(level, other.networkOwner(), other.securityMode(), controller.networkOwner(), controller.securityMode())) {
                return true;
            }
        }
        return false;
    }

    private static Component formatObstructionWarning(ServerLevel level, LinkedHashSet<BlockPos> obstructedPositions) {
        BlockPos first = obstructedPositions.iterator().next();
        int count = obstructedPositions.size();
        if (level.hasChunkAt(first)) {
            BlockState state = level.getBlockState(first);
            if (!state.isAir()) {
                Component blockName = state.getBlock().getName().copy().withStyle(ChatFormatting.GOLD);
                if (count == 1) {
                    return Component.translatable(
                                    "message.rematter.auto_build.obstructed_single",
                                    blockName,
                                    first.getX(),
                                    first.getY(),
                                    first.getZ())
                            .withStyle(ChatFormatting.RED);
                }
                return Component.translatable(
                                "message.rematter.auto_build.obstructed_multi",
                                blockName,
                                first.getX(),
                                first.getY(),
                                first.getZ(),
                                count - 1)
                        .withStyle(ChatFormatting.RED);
            }
        }
        return Component.translatable("message.rematter.auto_build.obstructed", count).withStyle(ChatFormatting.RED);
    }

    private static long cellKey(IndustrialStructure.Cell cell) {
        return cellKey(cell.x(), cell.y(), cell.depth());
    }

    private static long cellKey(int x, int y, int depth) {
        return ((long) (x + 16) << 16) | ((long) (y + 16) << 8) | (depth + 16);
    }

    private static boolean hasModuleSupport(ServerLevel level, BlockPos pos, String orientation) {
        boolean suspended = "down".equals(orientation);
        Direction anchorFace = suspended ? Direction.DOWN : Direction.UP;
        BlockPos anchor = pos.relative(anchorFace.getOpposite());
        if (!level.hasChunkAt(anchor)) return false;
        BlockState support = level.getBlockState(anchor);
        return support.is(ModContent.REACTOR_CASING.get())
                || support.is(ModContent.REACTOR_GLASS.get())
                || support.is(ModContent.MACHINE_PORT.get())
                || support.isFaceSturdy(level, anchor, anchorFace, SupportType.FULL);
    }

    private static int findInventorySlot(Inventory inventory, Item item) {
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item) && stack.getCount() > 0) {
                return i;
            }
        }
        return -1;
    }

    private static @Nullable Item itemForMaterial(IndustrialStructure.Material material) {
        return switch (material) {
            case CASING -> ModContent.REACTOR_CASING_ITEM.get();
            case GLASS -> ModContent.REACTOR_GLASS_ITEM.get();
            case PORT -> ModContent.MACHINE_PORT_ITEM.get();
            case MIXING_BASIN, SEPARATION_UNIT, GRINDING_UNIT, FIELD_COIL, REACTION_CHAMBER ->
                    ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.valueOf(material.name())).get().asItem();
            default -> null;
        };
    }

    private static @Nullable BlockState desiredState(Direction controllerFacing, IndustrialStructure.LocalMismatch mismatch) {
        return switch (mismatch.expected()) {
            case CASING -> ModContent.REACTOR_CASING.get().defaultBlockState();
            case GLASS -> ModContent.REACTOR_GLASS.get().defaultBlockState();
            case PORT -> {
                Direction out = mismatch.cell().outward() != null
                        ? IndustrialStructure.worldDirection(controllerFacing, mismatch.cell().outward())
                        : controllerFacing;
                yield ModContent.MACHINE_PORT.get().defaultBlockState().setValue(MachinePortBlock.FACING, out);
            }
            case MIXING_BASIN, SEPARATION_UNIT, GRINDING_UNIT, FIELD_COIL, REACTION_CHAMBER -> {
                var kind = IndustrialModuleKind.valueOf(mismatch.expected().name());
                boolean suspended = "down".equals(mismatch.orientation());
                yield ModContent.INDUSTRIAL_MODULES.get(kind).get().defaultBlockState()
                        .setValue(IndustrialModuleBlock.FACING, controllerFacing)
                        .setValue(IndustrialModuleBlock.SUSPENDED, suspended);
            }
            default -> null;
        };
    }
}
