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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
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

    private record PlacedStep(BlockSnapshot snapshot, @Nullable Item consumedItem) {}

    /**
     * Returns true when an unformed industrial controller rests directly on a solid non-casing floor block
     * (never when air/replaceable space or matching reactor casing is underneath).
     */
    public static boolean shouldLiftFromGround(ServerLevel level, MachineBlockEntity controller) {
        if (!IndustrialStructure.isIndustrial(controller.kind())) return false;
        if (IndustrialStructure.inspect(controller).formed()) return false;
        BlockPos below = controller.getBlockPos().below();
        if (!level.hasChunkAt(below)) return false;
        BlockState belowState = level.getBlockState(below);
        if (belowState.isAir() || (belowState.canBeReplaced() && belowState.getFluidState().isEmpty())) return false;
        if (belowState.is(ModContent.reactorCasing(controller.kind()))) return false;
        return IndustrialStructure.inferPartialBlueprint(controller).isEmpty();
    }

    public static BlockPos effectiveOrigin(ServerLevel level, MachineBlockEntity controller) {
        return shouldLiftFromGround(level, controller) ? controller.getBlockPos().above() : controller.getBlockPos();
    }

    static IndustrialStructure.Materials plannedMaterials(
            ServerLevel level, MachineBlockEntity controller, BlockPos buildOrigin, boolean lifting) {
        Direction facing = controller.getBlockState().getValue(MachineBlock.FACING);
        BlockPos oldOrigin = controller.getBlockPos();
        return (x, y, depth) -> {
            BlockPos worldPos = IndustrialStructure.worldPosition(
                    buildOrigin, facing, new IndustrialStructure.Cell(x, y, depth, IndustrialStructure.Role.INTERIOR, null));
            if (lifting) {
                if (x == 0 && y == 0 && depth == 0) {
                    if (!level.hasChunkAt(worldPos)) return IndustrialStructure.Material.UNLOADED;
                    BlockState above = level.getBlockState(worldPos);
                    return above.isAir() || (above.canBeReplaced() && above.getFluidState().isEmpty())
                            ? IndustrialStructure.Material.CONTROLLER
                            : IndustrialStructure.Material.OTHER;
                }
                if (worldPos.equals(oldOrigin)) {
                    return IndustrialStructure.Material.AIR;
                }
            }
            return IndustrialStructure.materialAt(level, controller, worldPos);
        };
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

        BlockState controllerState = controller.getBlockState();
        Direction facing = controllerState.getValue(MachineBlock.FACING);
        BlockPos oldOrigin = controller.getBlockPos();
        boolean lifting = shouldLiftFromGround(level, controller);
        BlockPos origin = lifting ? oldOrigin.above() : oldOrigin;
        IndustrialBlueprint blueprint = controller.industrialBlueprint();
        IndustrialStructure.Materials blocks = plannedMaterials(level, controller, origin, lifting);

        var obstructedPositions = new LinkedHashSet<BlockPos>();
        if (lifting) {
            if (!level.hasChunkAt(origin)
                    || claimedByAnotherChamber(level, player, controller, origin)
                    || !level.mayInteract(player, origin)
                    || !level.mayInteract(player, oldOrigin)
                    || !player.mayUseItemAt(origin, Direction.UP, new ItemStack(controllerState.getBlock().asItem()))
                    || !level.isUnobstructed(controllerState, origin, CollisionContext.empty())) {
                obstructedPositions.add(origin.immutable());
            } else {
                BlockState aboveState = level.getBlockState(origin);
                if (!aboveState.isAir() && !(aboveState.canBeReplaced() && aboveState.getFluidState().isEmpty())) {
                    obstructedPositions.add(origin.immutable());
                } else if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, oldOrigin, controllerState, player)).isCanceled()) {
                    obstructedPositions.add(oldOrigin.immutable());
                }
            }
        }
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
            if (!(lifting && targetPos.equals(oldOrigin))) {
                BlockState existing = level.getBlockState(targetPos);
                if (!existing.isAir() && !(existing.canBeReplaced() && existing.getFluidState().isEmpty())) {
                    obstructedPositions.add(targetPos.immutable());
                    continue;
                }
            }
            Item requiredItem = itemForMaterial(controller.kind(), expected);
            BlockState desired = desiredState(controller.kind(), facing, mismatch);
            if (requiredItem == null || desired == null) {
                obstructedPositions.add(targetPos.immutable());
                continue;
            }
            if (!level.mayInteract(player, targetPos)
                    || !player.mayUseItemAt(targetPos, Direction.UP, new ItemStack(requiredItem))
                    || (!(lifting && targetPos.equals(oldOrigin)) && !level.isUnobstructed(desired, targetPos, CollisionContext.empty()))) {
                obstructedPositions.add(targetPos.immutable());
                continue;
            }
            plannedMaterials.put(cellKey(mismatch.cell()), expected);
            if (expected.module()) {
                modulesSecond.add(mismatch);
            } else if (lifting && targetPos.equals(oldOrigin)) {
                // Place the floor casing pedestal directly under the lifted controller first.
                shellFirst.addFirst(mismatch);
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
            return abortObstructed(level, player, controller, oldOrigin, obstructedPositions);
        }

        // Never lift the controller into mid-air when a Survival player does not even carry one floor casing.
        if (lifting && !player.isCreative()) {
            Item pedestalCasing = ModContent.reactorCasing(controller.kind()).asItem();
            if (findInventorySlot(player.getInventory(), pedestalCasing) < 0) {
                Component missingMsg = Component.translatable("message.rematter.auto_build.missing_items").withStyle(ChatFormatting.GOLD);
                player.sendOverlayMessage(missingMsg);
                player.sendSystemMessage(missingMsg);
                return new Result(0, mismatches.size(), 0, false);
            }
        }

        MachineBlockEntity activeController = controller;
        CompoundTag savedControllerTag = null;
        BlockSnapshot liftedOriginSnap = null;
        boolean reopenMenu = lifting
                && player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection != null
                && serverPlayer.containerMenu instanceof MachineMenu mm
                && mm.ownsMachine(controller);
        if (lifting) {
            // Re-validate oldOrigin and origin just before moving controller atomically.
            if (!level.getBlockState(oldOrigin).equals(controllerState) || level.getBlockEntity(oldOrigin) != controller) {
                return Result.DENIED;
            }
            BlockState aboveNow = level.getBlockState(origin);
            if (!aboveNow.isAir() && !(aboveNow.canBeReplaced() && aboveNow.getFluidState().isEmpty())) {
                return Result.DENIED;
            }
            savedControllerTag = controller.saveCustomOnly(level.registryAccess());
            savedControllerTag.remove("industrial_claim");
            BlockState placedAgainst = level.getBlockState(oldOrigin);
            liftedOriginSnap = BlockSnapshot.create(level.dimension(), level, origin);
            if (!level.setBlock(origin, controllerState, Block.UPDATE_ALL)
                    || !(level.getBlockEntity(origin) instanceof MachineBlockEntity lifted)) {
                liftedOriginSnap.restore(Block.UPDATE_ALL);
                return Result.DENIED;
            }
            if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(liftedOriginSnap, placedAgainst, player)).isCanceled()) {
                liftedOriginSnap.restore(Block.UPDATE_ALL);
                return abortObstructed(level, player, controller, oldOrigin, new LinkedHashSet<>(List.of(origin.immutable())));
            }
            if (!level.removeBlock(oldOrigin, false)) {
                liftedOriginSnap.restore(Block.UPDATE_ALL);
                return Result.DENIED;
            }
            lifted.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedControllerTag));
            lifted.setChanged();
            ChunkRevisions.touchLoaded(level, oldOrigin);
            ChunkRevisions.touchLoaded(level, origin);
            activeController = lifted;
        }

        shellFirst.addAll(modulesSecond);

        var placedSteps = new ArrayList<PlacedStep>(shellFirst.size());
        int placed = 0;
        int missingItems = 0;
        for (var mismatch : shellFirst) {
            BlockPos targetPos = IndustrialStructure.worldPosition(origin, facing, mismatch.cell());
            Item requiredItem = itemForMaterial(activeController.kind(), mismatch.expected());
            BlockState desired = desiredState(activeController.kind(), facing, mismatch);
            if (requiredItem == null || desired == null) {
                continue;
            }
            BlockState existingNow = level.getBlockState(targetPos);
            if (!existingNow.isAir() && !(existingNow.canBeReplaced() && existingNow.getFluidState().isEmpty())) {
                var restoredCtrl = rollbackPlacement(level, player, placedSteps, lifting, oldOrigin, origin, controllerState, savedControllerTag, liftedOriginSnap, controller);
                return abortObstructed(level, player, restoredCtrl, oldOrigin, new LinkedHashSet<>(List.of(targetPos.immutable())));
            }
            if (mismatch.expected().isReactorModule() && !hasModuleSupport(level, targetPos, mismatch.orientation())) {
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
            BlockSnapshot snap = BlockSnapshot.create(level.dimension(), level, targetPos);
            BlockState placedAgainst = level.getBlockState(targetPos.below());
            if (!level.setBlock(targetPos, desired, Block.UPDATE_ALL)) {
                missingItems++;
                continue;
            }
            if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snap, placedAgainst, player)).isCanceled()) {
                snap.restore(Block.UPDATE_ALL);
                var restoredCtrl = rollbackPlacement(level, player, placedSteps, lifting, oldOrigin, origin, controllerState, savedControllerTag, liftedOriginSnap, controller);
                return abortObstructed(level, player, restoredCtrl, oldOrigin, new LinkedHashSet<>(List.of(targetPos.immutable())));
            }
            Item consumed = null;
            if (slot >= 0) {
                player.getInventory().removeItem(slot, 1);
                consumed = requiredItem;
            }
            placedSteps.add(new PlacedStep(snap, consumed));
            ChunkRevisions.touchLoaded(level, targetPos);
            if (mismatch.expected() == IndustrialStructure.Material.PORT) {
                MatterCableBlock.refreshLoadedAround(level, targetPos);
            }
            placed++;
        }

        if (placed > 0) {
            ChunkRevisions.touchLoaded(level, origin);
            player.getInventory().setChanged();
            activeController.serverTick();
            for (var port : IndustrialStructure.inspect(activeController).ports()) {
                if (level.getBlockEntity(port.position()) instanceof MachinePortBlockEntity portEntity) {
                    portEntity.refreshNow();
                }
            }
        }

        var finalFormation = IndustrialStructure.inspect(activeController);
        boolean formed = finalFormation.formed();
        if (formed) {
            activeController.setStructurePreviewEnabled(false);
        }
        if (reopenMenu && player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            MachineBlockEntity reopened = activeController;
            serverPlayer.openMenu(reopened, buf -> MachineMenu.writeMachineInit(buf, reopened, false, -1));
        }
        if (formed) {
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
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

    private static Result abortObstructed(
            ServerLevel level, Player player, MachineBlockEntity controller, BlockPos soundPos,
            LinkedHashSet<BlockPos> obstructedPositions) {
        if (!controller.isRemoved()) {
            if (!controller.structurePreviewEnabled()) {
                controller.setStructurePreviewEnabled(true);
            }
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                int cid = serverPlayer.containerMenu instanceof MachineMenu menu ? menu.containerId : 0;
                PacketDistributor.sendToPlayer(serverPlayer, IndustrialStructure.hintFor(cid, controller));
            }
        }
        Component warning = formatObstructionWarning(level, obstructedPositions);
        player.sendOverlayMessage(warning);
        player.sendSystemMessage(warning);
        ModSounds.play(level, soundPos, ModSounds.ACTION_DENIED);
        return new Result(0, 0, obstructedPositions.size(), false);
    }

    private static MachineBlockEntity rollbackPlacement(
            ServerLevel level,
            Player player,
            List<PlacedStep> placedSteps,
            boolean lifting,
            BlockPos oldOrigin,
            BlockPos origin,
            BlockState controllerState,
            @Nullable CompoundTag savedControllerTag,
            @Nullable BlockSnapshot liftedOriginSnap,
            MachineBlockEntity originalController) {
        for (int i = placedSteps.size() - 1; i >= 0; i--) {
            PlacedStep step = placedSteps.get(i);
            step.snapshot().restore(Block.UPDATE_ALL);
            ChunkRevisions.touchLoaded(level, step.snapshot().getPos());
            if (step.consumedItem() != null) {
                player.getInventory().placeItemBackInInventory(new ItemStack(step.consumedItem()));
            }
        }
        MachineBlockEntity result = originalController;
        if (lifting) {
            if (liftedOriginSnap != null) liftedOriginSnap.restore(Block.UPDATE_ALL);
            else level.removeBlock(origin, false);
            if (level.setBlock(oldOrigin, controllerState, Block.UPDATE_ALL)
                    && level.getBlockEntity(oldOrigin) instanceof MachineBlockEntity restored) {
                if (savedControllerTag != null) {
                    restored.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedControllerTag));
                    restored.setChanged();
                }
                result = restored;
            }
            ChunkRevisions.touchLoaded(level, origin);
            ChunkRevisions.touchLoaded(level, oldOrigin);
        }
        player.getInventory().setChanged();
        return result;
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
        return ModContent.isAnyReactorCasing(support)
                || (!ModContent.isAnyReactorGlass(support) && support.isFaceSturdy(level, anchor, anchorFace, SupportType.FULL));
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

    private static @Nullable Item itemForMaterial(MachineKind controllerKind, IndustrialStructure.Material material) {
        return switch (material) {
            case CASING -> ModContent.reactorCasing(controllerKind).asItem();
            case GLASS -> ModContent.reactorGlass(controllerKind).asItem();
            case PORT -> ModContent.machinePort(controllerKind).asItem();
            case ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.BASIC).get().asItem();
            case REINFORCED_ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.REINFORCED).get().asItem();
            case RESONANT_ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.RESONANT).get().asItem();
            case PRIMAL_ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.PRIMAL).get().asItem();
            case ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.BASIC).get().asItem();
            case REINFORCED_ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.REINFORCED).get().asItem();
            case RESONANT_ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.RESONANT).get().asItem();
            case PRIMAL_ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.PRIMAL).get().asItem();
            case MIXING_BASIN, SEPARATION_UNIT, GRINDING_UNIT, FIELD_COIL, REACTION_CHAMBER, PARTICLE_EMITTER, PRIMAL_CONDENSER_CORE, REACTOR_FUEL_CORE, REACTOR_INDUCTION_COIL ->
                    ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.valueOf(material.name())).get().asItem();
            default -> null;
        };
    }

    private static @Nullable BlockState desiredState(MachineKind controllerKind, Direction controllerFacing, IndustrialStructure.LocalMismatch mismatch) {
        return switch (mismatch.expected()) {
            case CASING -> ModContent.reactorCasing(controllerKind).defaultBlockState();
            case GLASS -> ModContent.reactorGlass(controllerKind).defaultBlockState();
            case PORT -> {
                Direction out = mismatch.cell().outward() != null
                        ? IndustrialStructure.worldDirection(controllerFacing, mismatch.cell().outward())
                        : controllerFacing;
                yield ModContent.machinePort(controllerKind).defaultBlockState().setValue(MachinePortBlock.FACING, out);
            }
            case ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.BASIC).get().defaultBlockState();
            case REINFORCED_ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.REINFORCED).get().defaultBlockState();
            case RESONANT_ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.RESONANT).get().defaultBlockState();
            case PRIMAL_ENERGY_MATRIX_CELL -> ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.PRIMAL).get().defaultBlockState();
            case ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.BASIC).get().defaultBlockState();
            case REINFORCED_ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.REINFORCED).get().defaultBlockState();
            case RESONANT_ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.RESONANT).get().defaultBlockState();
            case PRIMAL_ENERGY_MATRIX_PROVIDER -> ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.PRIMAL).get().defaultBlockState();
            case MIXING_BASIN, SEPARATION_UNIT, GRINDING_UNIT, FIELD_COIL, REACTION_CHAMBER, PARTICLE_EMITTER, PRIMAL_CONDENSER_CORE, REACTOR_FUEL_CORE, REACTOR_INDUCTION_COIL -> {
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
