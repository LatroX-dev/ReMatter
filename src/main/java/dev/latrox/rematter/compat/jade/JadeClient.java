package dev.latrox.rematter.compat.jade;

import dev.latrox.rematter.content.MachineBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ProgressView;
import snownee.jade.api.view.ViewGroup;

/** Kept separate so dedicated servers never resolve Jade's tooltip implementation. */
final class JadeClient {
    private JadeClient() {}

    static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new StatusTooltip(), MachineBlock.class);
        registration.registerItemStorageClient(new EmptyView<ItemStack, ItemView>(ReMatterJadePlugin.ITEMS));
        registration.registerFluidStorageClient(new EmptyView<FluidView.Data, FluidView>(ReMatterJadePlugin.FLUIDS));
        registration.registerEnergyStorageClient(new EmptyView<EnergyView.Data, EnergyView>(ReMatterJadePlugin.ENERGY));
        registration.registerProgressClient(new EmptyView<ProgressView.Data, ProgressView>(ReMatterJadePlugin.PROGRESS));
    }

    private record EmptyView<T, V>(Identifier uid) implements IClientExtensionProvider<T, V> {
        @Override public Identifier getUid() { return uid; }
        @Override public List<ClientViewGroup<V>> getClientGroups(Accessor<?> accessor, List<ViewGroup<T>> groups) { return List.of(); }
    }

    private static final class StatusTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.STATUS; }
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(MachineDataProvider.DATA_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(MachineDataProvider.DATA_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            tooltip.add(Component.translatable("status.rematter." + data.getStringOr("status", "idle")));
            tooltip.add(Component.literal(data.getIntOr("energy", 0) + " / " + data.getIntOr("energyCapacity", 0) + " FE"));
            long capacity = data.getLongOr("capacity", 0);
            if (capacity > 0) tooltip.add(Component.literal("Matter: " + data.getLongOr("matter", 0) + " / " + capacity + " u"));
            int duration = data.getIntOr("duration", 0);
            if (duration > 0) tooltip.add(Component.literal("Progress: " + data.getIntOr("progress", 0) + " / " + duration + " ticks"));
            if (data.getIntOr("fluidAmount", 0) > 0) {
                tooltip.add(Component.literal(data.getStringOr("fluidName", "Fluid") + ": " + data.getIntOr("fluidAmount", 0) + " mB"));
            }
            tooltip.add(Component.translatable(data.getBooleanOr("automation", false)
                    ? "screen.rematter.automation_on" : "screen.rematter.automation_off").withStyle(ChatFormatting.GRAY));
            int shown = Math.clamp(data.getIntOr("shown", 0), 0, 6);
            for (int i = 0; i < shown; i++) {
                CompoundTag entry = data.getCompoundOrEmpty("entry" + i);
                tooltip.add(Component.literal(entry.getStringOr("name", "") + " (" + entry.getStringOr("phase", "")
                        + "): " + entry.getLongOr("amount", 0) + " u").withStyle(ChatFormatting.AQUA));
            }
            int remaining = data.getIntOr("substances", 0) - shown;
            if (shown > 0 && remaining > 0) tooltip.add(Component.literal("+" + remaining + " more substances").withStyle(ChatFormatting.GRAY));
        }
    }
}
