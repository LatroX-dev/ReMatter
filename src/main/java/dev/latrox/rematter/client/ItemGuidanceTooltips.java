package dev.latrox.rematter.client;

import com.mojang.datafixers.util.Either;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.content.ToolSalvage;
import dev.latrox.rematter.guide.ItemGuidance;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.primal.PrimalContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ItemGuidanceTooltips {
    private ItemGuidanceTooltips() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.getComponentsPatch().isEmpty()) {
            dev.latrox.rematter.network.ClientCrystalFuels.fuel(item).ifPresent(fuel -> {
                Component yieldLine = fuel.count() == 1
                        ? Component.translatable("tooltip.rematter.crystal_fuel.yield", Quantities.number(fuel.energy()))
                        : Component.translatable("tooltip.rematter.crystal_fuel.yield_batch",
                                Quantities.number(fuel.count()), Quantities.number(fuel.energy()));
                event.getToolTip().add(yieldLine.copy().withStyle(ChatFormatting.GOLD));
                long standardTicks = (fuel.energy() + 127L) / 128L;
                long industrialTicks = (fuel.energy() + 511L) / 512L;
                event.getToolTip().add(Component.translatable("tooltip.rematter.crystal_fuel.rates",
                        Quantities.number(128), Quantities.number(standardTicks),
                        Quantities.number(512), Quantities.number(industrialTicks)).withStyle(ChatFormatting.DARK_GRAY));
            });
        }
        ItemGuidance.forItem(item).ifPresent(spec -> {
            event.getToolTip().add(Component.translatable(spec.role().key()).withStyle(ChatFormatting.AQUA));
            for (var hint : spec.hints()) {
                event.getToolTip().add(Component.translatable(hint.key(), hint.arguments().toArray())
                        .withStyle(ChatFormatting.GRAY));
            }
        });
        if (item.equals("rematter:hand_preparator") && stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
            var data = stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId();
            var items = data.getList("items");
            if (items.isEmpty() || items.get().stream()
                    .anyMatch(value -> !(value instanceof net.minecraft.nbt.CompoundTag tag) || !tag.isEmpty())) {
                event.getToolTip().add(Component.translatable("tooltip.rematter.guidance.hand_contents")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (DiscoveryLensState.isSpectrometerLens(stack)) {
            appendLensOwnStatus(event);
            return;
        }
        appendLensShiftHint(event, item);
    }

    @SubscribeEvent
    public static void gatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || DiscoveryLensState.isSpectrometerLens(stack)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !DiscoveryLensState.tooltipActive(mc.player) || !mc.hasShiftDown()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var chemistry = ClientChemistry.current();
        boolean primal = ReservedSubstances.primalItem(itemId);
        var salvage = primal ? java.util.Optional.<ToolSalvage.Plan>empty() : ToolSalvage.plan(chemistry, stack);
        if (!primal && salvage.isEmpty()) return;

        var elements = event.getTooltipElements();
        elements.add(Either.left(Component.translatable("tooltip.rematter.discovery_lens.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE)));
        if (primal) {
            elements.add(Either.left(Component.translatable("hud.rematter.discovery_lens.primal_signature")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)));
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
        if (report.batchCount() > 1) {
            elements.add(Either.left(Component.translatable("hud.rematter.discovery_lens.item_batch",
                    Quantities.number(report.batchCount())).withStyle(ChatFormatting.DARK_GRAY)));
        } else if (report.durabilityScaled()) {
            elements.add(Either.left(Component.translatable("tooltip.rematter.discovery_lens.durability_scaled")
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (!report.knownTraces().isEmpty()) {
            elements.add(Either.right(new DiscoveryLensTooltip.Data(report.knownTraces())));
        }
        switch (report.synthesisStatus()) {
            case SYNTHESIS_UNLOCKED -> elements.add(Either.left(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_unlocked").withStyle(ChatFormatting.GREEN)));
            case SYNTHESIS_LOCKED -> elements.add(Either.left(
                    Component.translatable("hud.rematter.discovery_lens.synthesis_locked").withStyle(ChatFormatting.YELLOW)));
            case DECOMPOSITION_ONLY -> elements.add(Either.left(
                    Component.translatable("hud.rematter.discovery_lens.decomposition_only").withStyle(ChatFormatting.DARK_GRAY)));
            case NONE -> {}
        }
    }

    private static void appendLensOwnStatus(ItemTooltipEvent event) {
        boolean active = DiscoveryLensState.enabled();
        event.getToolTip().add(Component.translatable(active
                        ? "tooltip.rematter.discovery_lens.state_on"
                        : "tooltip.rematter.discovery_lens.state_off")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (!ClientKeybinds.TOGGLE_DISCOVERY_LENS.isUnbound()) {
            event.getToolTip().add(Component.translatable(
                            "tooltip.rematter.discovery_lens.toggle_key_bound",
                            ClientKeybinds.TOGGLE_DISCOVERY_LENS.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            event.getToolTip().add(Component.translatable("tooltip.rematter.discovery_lens.toggle_key_unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendLensShiftHint(ItemTooltipEvent event, String itemId) {
        var player = event.getEntity();
        if (player == null || !DiscoveryLensState.tooltipActive(player)) return;
        if (Minecraft.getInstance().hasShiftDown()) return;

        var chemistry = ClientChemistry.current();
        boolean primal = ReservedSubstances.primalItem(itemId);
        if (!primal && ToolSalvage.plan(chemistry, event.getItemStack()).isEmpty()) return;

        event.getToolTip().add(Component.translatable("tooltip.rematter.discovery_lens.hold_shift")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
