package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.guide.ItemGuidance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ItemGuidanceTooltips {
    private ItemGuidanceTooltips() {}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        String item = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
        if (event.getItemStack().getComponentsPatch().isEmpty()) {
            dev.latrox.rematter.network.ClientCrystalFuels.fuel(item).ifPresent(fuel -> {
                Component yieldLine = fuel.count() == 1
                        ? Component.translatable("tooltip.rematter.crystal_fuel.yield", Quantities.number(fuel.energy()))
                        : Component.translatable("tooltip.rematter.crystal_fuel.yield_batch",
                                Quantities.number(fuel.count()), Quantities.number(fuel.energy()));
                event.getToolTip().add(yieldLine.copy().withStyle(ChatFormatting.GOLD));
                long standardTicks = (fuel.energy() + 63L) / 64L;
                long industrialTicks = (fuel.energy() + 255L) / 256L;
                event.getToolTip().add(Component.translatable("tooltip.rematter.crystal_fuel.rates",
                        Quantities.number(64), Quantities.number(standardTicks),
                        Quantities.number(256), Quantities.number(industrialTicks)).withStyle(ChatFormatting.DARK_GRAY));
            });
        }
        ItemGuidance.forItem(item).ifPresent(spec -> {
            event.getToolTip().add(Component.translatable(spec.role().key()).withStyle(ChatFormatting.AQUA));
            for (var hint : spec.hints()) event.getToolTip().add(Component.translatable(hint.key(), hint.arguments().toArray()).withStyle(ChatFormatting.GRAY));
        });
        if (item.equals("rematter:hand_preparator") && event.getItemStack().has(DataComponents.BLOCK_ENTITY_DATA)) {
            var data = event.getItemStack().get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId();
            var items = data.getList("items");
            if (items.isEmpty() || items.get().stream().anyMatch(value -> !(value instanceof net.minecraft.nbt.CompoundTag tag) || !tag.isEmpty()))
                event.getToolTip().add(Component.translatable("tooltip.rematter.guidance.hand_contents").withStyle(ChatFormatting.YELLOW));
        }
        appendLensSpectrometry(event, item);
    }

    private static void appendLensSpectrometry(ItemTooltipEvent event, String itemId) {
        var player = event.getEntity();
        if (player == null || !carriesDiscoveryLens(player)) return;
        var stack = event.getItemStack();
        if (stack.is(dev.latrox.rematter.primal.PrimalContent.DISCOVERY_LENS.get())) return;

        var chemistry = dev.latrox.rematter.network.ClientChemistry.current();
        boolean primal = dev.latrox.rematter.chemistry.ReservedSubstances.primalItem(itemId);
        var salvage = primal ? java.util.Optional.<dev.latrox.rematter.content.ToolSalvage.Plan>empty()
                : dev.latrox.rematter.content.ToolSalvage.plan(chemistry, stack);
        if (!primal && salvage.isEmpty()) return;

        if (!net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
            event.getToolTip().add(Component.translatable("tooltip.rematter.discovery_lens.hold_shift")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }

        event.getToolTip().add(Component.translatable("tooltip.rematter.discovery_lens.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (primal) {
            event.getToolTip().add(Component.translatable("hud.rematter.discovery_lens.primal_signature")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }

        var plan = salvage.get();
        var report = DiscoveryLensInspector.inspectCurrent(
                chemistry,
                plan.profile().itemId(),
                false,
                plan.profile().itemCount(),
                plan.damaged(),
                plan.matter());
        if (report.unanalyzed()) {
            event.getToolTip().add(Component.translatable("hud.rematter.discovery_lens.unanalyzed")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        if (report.batchCount() > 1) {
            event.getToolTip().add(Component.translatable("hud.rematter.discovery_lens.item_batch", Quantities.number(report.batchCount()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (report.durabilityScaled()) {
            event.getToolTip().add(Component.translatable("tooltip.rematter.discovery_lens.durability_scaled")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        for (var trace : report.knownTraces()) {
            Component line = Component.translatable(
                    "hud.rematter.discovery_lens.trace_line",
                    SubstanceNames.labelled(trace.key()),
                    SubstanceNames.phase(trace.key().phase()),
                    Quantities.number(trace.amount()));
            ChatFormatting color = switch (trace.key().phase()) {
                case SOLID -> ChatFormatting.AQUA;
                case LIQUID -> ChatFormatting.BLUE;
                case GAS -> ChatFormatting.LIGHT_PURPLE;
            };
            event.getToolTip().add(line.copy().withStyle(color));
        }
        if (report.hiddenTraceCount() > 0) {
            event.getToolTip().add(Component.translatable("hud.rematter.discovery_lens.unknown_traces", report.hiddenTraceCount())
                    .withStyle(ChatFormatting.GOLD));
        }
        switch (report.synthesisStatus()) {
            case SYNTHESIS_UNLOCKED -> event.getToolTip().add(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_unlocked").withStyle(ChatFormatting.GREEN));
            case SYNTHESIS_LOCKED -> event.getToolTip().add(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_locked").withStyle(ChatFormatting.YELLOW));
            case DECOMPOSITION_ONLY -> event.getToolTip().add(
                    Component.translatable("hud.rematter.discovery_lens.decomposition_only").withStyle(ChatFormatting.DARK_GRAY));
            case NONE -> {}
        }
    }

    private static boolean carriesDiscoveryLens(net.minecraft.world.entity.player.Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(dev.latrox.rematter.primal.PrimalContent.DISCOVERY_LENS.get())) {
                return true;
            }
        }
        return false;
    }
}
