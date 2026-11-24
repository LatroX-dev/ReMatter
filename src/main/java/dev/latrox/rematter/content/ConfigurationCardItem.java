package dev.latrox.rematter.content;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Configuration card: pairs Entangled Links and copies/pastes Machine, Energy Cube, Level Emitter, and Cell Filter setups. */
public final class ConfigurationCardItem extends Item {
    public ConfigurationCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        return ConfigurationCardInteractions.interact(context.getPlayer(), context.getLevel(), context.getClickedPos(), context.getItemInHand(), hit);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack otherHand = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!player.isSpectator() && ConfigurationCardData.canPartitionCell(otherHand)) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide()) {
                    var spec = CellPartitionData.read(otherHand).orElse(null);
                    if (spec == null) return InteractionResult.FAIL;
                    stack.set(ConfigurationCardData.TYPE.get(), ConfigurationCardData.fromCellFilter(spec));
                    player.sendOverlayMessage(Component.translatable("message.rematter.config_card.cell_filter_copied"));
                    ModSounds.play(level, player.blockPosition(), ModSounds.CONFIG_CARD_READ);
                }
                return InteractionResult.SUCCESS;
            }
            var data = stack.get(ConfigurationCardData.TYPE.get());
            var cellFilter = data == null ? null : data.cellFilterSetup().orElse(null);
            if (cellFilter != null) {
                if (!level.isClientSide()) {
                    if (!ConfigurationCardData.applyCellPartition(otherHand, cellFilter.spec())) return InteractionResult.FAIL;
                    player.sendOverlayMessage(Component.translatable("message.rematter.config_card.cell_filter_pasted"));
                    ModSounds.play(level, player.blockPosition(), ModSounds.CONFIG_CARD_WRITE);
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (player.isShiftKeyDown() && stack.has(ConfigurationCardData.TYPE.get())) {
            if (!level.isClientSide()) {
                stack.remove(ConfigurationCardData.TYPE.get());
                player.sendOverlayMessage(Component.translatable("message.rematter.config_card.cleared"));
                ModSounds.play(level, player.blockPosition(), ModSounds.CONFIG_CARD_CLEAR);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        var data = stack.get(ConfigurationCardData.TYPE.get());
        if (data == null) {
            tooltip.accept(Component.translatable("tooltip.rematter.config_card.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        data.linkOrigin().ifPresentOrElse(link -> tooltip.accept(
                Component.translatable("tooltip.rematter.config_card.link",
                        link.pos().getX(), link.pos().getY(), link.pos().getZ()).withStyle(ChatFormatting.LIGHT_PURPLE)),
                () -> data.machineSetup().ifPresentOrElse(machine -> tooltip.accept(
                        Component.translatable("tooltip.rematter.config_card.machine",
                                Component.translatable("block.rematter." + machine.sourceKind().id())).withStyle(ChatFormatting.GREEN)),
                        () -> data.energyCubeSetup().ifPresentOrElse(cube -> tooltip.accept(
                                Component.translatable("tooltip.rematter.config_card.energy_cube",
                                        Component.translatable("block.rematter." + cube.sourceTier().id())).withStyle(ChatFormatting.GOLD)),
                                () -> data.levelEmitterSetup().ifPresentOrElse(emitter -> tooltip.accept(
                                        Component.translatable("tooltip.rematter.config_card.level_emitter",
                                                Component.translatable("block.rematter.matter_level_emitter")).withStyle(ChatFormatting.AQUA)),
                                        () -> data.cellFilterSetup().ifPresentOrElse(filter -> tooltip.accept(
                                                (filter.spec().restricted()
                                                        ? Component.translatable("tooltip.rematter.config_card.cell_filter", filter.spec().allowed().size())
                                                        : Component.translatable("tooltip.rematter.config_card.cell_filter_any"))
                                                        .withStyle(ChatFormatting.AQUA)),
                                                () -> tooltip.accept(Component.translatable("tooltip.rematter.config_card.empty").withStyle(ChatFormatting.GRAY)))))));
        tooltip.accept(Component.translatable("tooltip.rematter.config_card.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
