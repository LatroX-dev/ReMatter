package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferStationBlock;
import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlock;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/** Copy/paste and Entangled Link pairing behavior for Configuration Cards. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ConfigurationCardInteractions {
    private ConfigurationCardInteractions() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof ConfigurationCardItem)) return;
        InteractionResult result = interact(event.getEntity(), event.getLevel(), event.getPos(), event.getItemStack(), event.getHitVec());
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    public static InteractionResult interact(@Nullable Player player, Level level, BlockPos pos, ItemStack stack, BlockHitResult hit) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof EntangledLinkBlock) && !(block instanceof MachineBlock)
                && !(block instanceof EnergyCubeBlock) && !(block instanceof MatterLevelEmitterBlock)
                && !(block instanceof CellTransferStationBlock)) {
            return InteractionResult.PASS;
        }
        if (player == null || player.isSpectator() || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        if (block instanceof EntangledLinkBlock) {
            return interactLink(player, server, pos, stack);
        }
        if (block instanceof CellTransferStationBlock) {
            return interactCellStation(player, server, pos, stack);
        }
        if (block instanceof MachineBlock) {
            return interactMachine(player, server, pos, state, stack, hit);
        }
        if (block instanceof MatterLevelEmitterBlock) {
            return interactLevelEmitter(player, server, pos, stack);
        }
        return interactEnergyCube(player, server, pos, stack);
    }

    private static InteractionResult interactCellStation(Player player, ServerLevel server, BlockPos pos, ItemStack stack) {
        if (!(server.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station)
                || !station.interact(player) || !station.stillValid(player) || station.locked()) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            var copied = station.copyCardFilter(player).orElse(null);
            if (copied == null) return InteractionResult.FAIL;
            stack.set(ConfigurationCardData.TYPE.get(), ConfigurationCardData.fromCellFilter(copied));
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.cell_filter_copied"));
            ModSounds.play(server, pos, ModSounds.CONFIG_CARD_READ);
            return InteractionResult.SUCCESS;
        }
        var data = stack.get(ConfigurationCardData.TYPE.get());
        var setup = data == null ? null : data.cellFilterSetup().orElse(null);
        if (setup == null) {
            player.sendOverlayMessage(Component.translatable(data == null
                    ? "message.rematter.config_card.empty_hint" : "message.rematter.config_card.incompatible"));
            return InteractionResult.FAIL;
        }
        if (!station.applyCardFilter(player, setup.spec())) return InteractionResult.FAIL;
        player.sendOverlayMessage(Component.translatable("message.rematter.config_card.cell_filter_pasted"));
        ModSounds.play(server, pos, ModSounds.CONFIG_CARD_WRITE);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactLevelEmitter(Player player, ServerLevel server, BlockPos pos, ItemStack stack) {
        if (!(server.getBlockEntity(pos) instanceof MatterLevelEmitterBlockEntity emitter) || !emitter.interact(player) || !emitter.stillValid(player)) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            var setup = new ConfigurationCardData.EmitterSetup(emitter.target(), emitter.threshold(), emitter.emitWhenBelow());
            stack.set(ConfigurationCardData.TYPE.get(), ConfigurationCardData.fromLevelEmitter(setup));
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.copied", emitter.getDisplayName()));
            ModSounds.play(server, pos, ModSounds.CONFIG_CARD_READ);
            return InteractionResult.SUCCESS;
        }
        var data = stack.get(ConfigurationCardData.TYPE.get());
        var setup = data == null ? null : data.levelEmitterSetup().orElse(null);
        if (setup == null) {
            player.sendOverlayMessage(Component.translatable(data == null
                    ? "message.rematter.config_card.empty_hint" : "message.rematter.config_card.incompatible"));
            return InteractionResult.FAIL;
        }
        emitter.configure(setup.target(), setup.threshold(), setup.emitWhenBelow());
        player.sendOverlayMessage(Component.translatable("message.rematter.config_card.pasted", emitter.getDisplayName()));
        ModSounds.play(server, pos, ModSounds.CONFIG_CARD_WRITE);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactLink(Player player, ServerLevel server, BlockPos pos, ItemStack stack) {
        if (!(server.getBlockEntity(pos) instanceof EntangledLinkBlockEntity link)) return InteractionResult.FAIL;
        if (link.owner() == null || !link.owner().equals(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.rematter.private_machine"));
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            stack.set(ConfigurationCardData.TYPE.get(), ConfigurationCardData.fromLink(server.dimension(), pos));
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.link_selected",
                    pos.getX(), pos.getY(), pos.getZ()));
            ModSounds.play(server, pos, ModSounds.CONFIG_CARD_READ);
            return InteractionResult.SUCCESS;
        }
        var data = stack.get(ConfigurationCardData.TYPE.get());
        var origin = data == null ? null : data.linkOrigin().orElse(null);
        if (origin == null) {
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.empty_hint"));
            return InteractionResult.FAIL;
        }
        if (!origin.dimension().equals(server.dimension())) {
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.link_dimension"));
            return InteractionResult.FAIL;
        }
        if (origin.pos().equals(pos)) {
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.link_same"));
            return InteractionResult.FAIL;
        }
        if (!EntangledLinks.withinRange(origin.pos(), pos)) {
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.link_out_of_range", EntangledLinks.MAX_DISTANCE));
            return InteractionResult.FAIL;
        }
        if (EntangledLinks.loaded(server, origin.pos()).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.link_unloaded"));
            return InteractionResult.FAIL;
        }
        if (!EntangledLinks.pair(server, origin.pos(), pos, player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.rematter.private_machine"));
            return InteractionResult.FAIL;
        }
        MatterCableBlock.refreshLoadedAround(server, origin.pos());
        MatterCableBlock.refreshLoadedAround(server, pos);
        stack.remove(ConfigurationCardData.TYPE.get());
        player.sendOverlayMessage(Component.translatable("message.rematter.config_card.link_paired"));
        ModSounds.play(server, pos, ModSounds.ENTANGLED_PAIR);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactMachine(Player player, ServerLevel server, BlockPos pos, BlockState state, ItemStack stack, BlockHitResult hit) {
        if (!(server.getBlockEntity(pos) instanceof MachineBlockEntity machine) || !machine.interact(player) || !machine.stillValid(player)) {
            return InteractionResult.FAIL;
        }
        if (machine.kind() == MachineKind.DRIVE && hit.getDirection() == state.getValue(MachineBlock.FACING)) {
            int bay = DriveBayInteraction.hitBay(pos, state.getValue(MachineBlock.FACING), hit);
            int slot = MachineCellAccess.slotForBay(MachineKind.DRIVE, bay);
            ItemStack bayCell = slot >= 0 ? machine.getItem(slot) : ItemStack.EMPTY;
            if (ConfigurationCardData.canPartitionCell(bayCell)) {
                if (player.isShiftKeyDown()) {
                    var spec = CellPartitionData.read(bayCell).orElse(null);
                    if (spec != null) {
                        stack.set(ConfigurationCardData.TYPE.get(), ConfigurationCardData.fromCellFilter(spec));
                        player.sendOverlayMessage(Component.translatable("message.rematter.config_card.cell_filter_copied"));
                        ModSounds.play(server, pos, ModSounds.CONFIG_CARD_READ);
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    var data = stack.get(ConfigurationCardData.TYPE.get());
                    var cellFilter = data == null ? null : data.cellFilterSetup().orElse(null);
                    if (cellFilter != null) {
                        if (!machine.applyBayCellPartition(player, slot, cellFilter.spec())) return InteractionResult.FAIL;
                        player.sendOverlayMessage(Component.translatable("message.rematter.config_card.cell_filter_pasted"));
                        ModSounds.play(server, pos, ModSounds.CONFIG_CARD_WRITE);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        if (player.isShiftKeyDown()) {
            var setup = new ConfigurationCardData.MachineSetup(
                    machine.kind(),
                    PortSides.pack(machine::portMode),
                    machine.redstoneMode(),
                    machine.running(),
                    machine.runState().repeat(),
                    machine.automation(),
                    machine.networkJobs(),
                    machine.selectedId(),
                    machine.settings(),
                    machine.soundLevel(),
                    machine.customIndustrialBlueprint(),
                    machine.structurePreviewEnabled(),
                    machine.autoEject());
            stack.set(ConfigurationCardData.TYPE.get(), ConfigurationCardData.fromMachine(setup));
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.copied", machine.getDisplayName()));
            ModSounds.play(server, pos, ModSounds.CONFIG_CARD_READ);
            return InteractionResult.SUCCESS;
        }
        var data = stack.get(ConfigurationCardData.TYPE.get());
        var setup = data == null ? null : data.machineSetup().orElse(null);
        if (setup == null) {
            player.sendOverlayMessage(Component.translatable(data == null
                    ? "message.rematter.config_card.empty_hint" : "message.rematter.config_card.incompatible"));
            return InteractionResult.FAIL;
        }
        if (!machine.applyCopiedSetup(player, setup)) return InteractionResult.FAIL;
        player.sendOverlayMessage(Component.translatable("message.rematter.config_card.pasted", machine.getDisplayName()));
        ModSounds.play(server, pos, ModSounds.CONFIG_CARD_WRITE);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactEnergyCube(Player player, ServerLevel server, BlockPos pos, ItemStack stack) {
        if (!(server.getBlockEntity(pos) instanceof EnergyCubeBlockEntity cube) || !cube.interact(player)) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            stack.set(ConfigurationCardData.TYPE.get(), ConfigurationCardData.fromEnergyCube(cube.tier(), cube.packedSideModes()));
            player.sendOverlayMessage(Component.translatable("message.rematter.config_card.copied", cube.getDisplayName()));
            ModSounds.play(server, pos, ModSounds.CONFIG_CARD_READ);
            return InteractionResult.SUCCESS;
        }
        var data = stack.get(ConfigurationCardData.TYPE.get());
        var setup = data == null ? null : data.energyCubeSetup().orElse(null);
        if (setup == null) {
            player.sendOverlayMessage(Component.translatable(data == null
                    ? "message.rematter.config_card.empty_hint" : "message.rematter.config_card.incompatible"));
            return InteractionResult.FAIL;
        }
        if (!cube.applyCopiedSides(player, setup.packedSideModes())) return InteractionResult.FAIL;
        player.sendOverlayMessage(Component.translatable("message.rematter.config_card.pasted", cube.getDisplayName()));
        ModSounds.play(server, pos, ModSounds.CONFIG_CARD_WRITE);
        return InteractionResult.SUCCESS;
    }
}
