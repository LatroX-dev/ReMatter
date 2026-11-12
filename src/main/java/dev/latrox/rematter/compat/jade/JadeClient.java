package dev.latrox.rematter.compat.jade;

import dev.latrox.rematter.client.SubstanceLabels;
import dev.latrox.rematter.client.StatusText;
import dev.latrox.rematter.client.SubstanceNames;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachinePortBlock;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MatterCableBlock;
import dev.latrox.rematter.content.IndustrialStructure;
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
        registration.registerBlockComponent(new CableTooltip(), MatterCableBlock.class);
        registration.registerBlockComponent(new PortTooltip(), MachinePortBlock.class);
        registration.registerItemStorageClient(new EmptyView<ItemStack, ItemView>(ReMatterJadePlugin.ITEMS));
        registration.registerFluidStorageClient(new EmptyView<FluidView.Data, FluidView>(ReMatterJadePlugin.FLUIDS));
        registration.registerEnergyStorageClient(new EmptyView<EnergyView.Data, EnergyView>(ReMatterJadePlugin.ENERGY));
        registration.registerProgressClient(new EmptyView<ProgressView.Data, ProgressView>(ReMatterJadePlugin.PROGRESS));
    }

    private static final class CableTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.CABLE; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockState().getBlock() instanceof MatterCableBlock cable)) return;
            tooltip.add(Component.translatable("jade.rematter.cable_channels").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("jade.rematter.cable_limit", cable.tier().networkLimit()).withStyle(ChatFormatting.AQUA));
            if (accessor.showDetails()) {
                tooltip.add(Component.translatable("jade.rematter.cable_connections", MatterCableBlock.connections(accessor.getBlockState())));
                tooltip.add(Component.translatable("jade.rematter.cable_limit_hint").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("jade.rematter.cable_activity_hint").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static final class PortTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.PORT; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var state = accessor.getBlockState();
            if (!state.getValue(MachinePortBlock.FORMED)) {
                tooltip.add(Component.translatable("jade.rematter.port_unformed").withStyle(ChatFormatting.GRAY));
                return;
            }
            tooltip.add(Component.translatable("port.rematter.mode_message", state.getValue(MachinePortBlock.MODE).label()));
            if (accessor.showDetails()) tooltip.add(Component.translatable("jade.rematter.port_change_hint").withStyle(ChatFormatting.GRAY));
        }
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
            tooltip.add(StatusText.of(data.getStringOr("status", "idle"), data.getIntOr("staleOrders", 0), data.getIntOr("heldOrders", 0)));
            if(data.getLongOr("target",0)>0)tooltip.add(Component.translatable(data.getBooleanOr("targetNetwork",false)
                    ?"screen.rematter.target_network_count":"screen.rematter.target_local_count",
                    data.getLongOr("targetStock",-1)<0?"?":dev.latrox.rematter.client.Quantities.number(data.getLongOr("targetStock",0)),
                    dev.latrox.rematter.client.Quantities.number(data.getLongOr("target",0))));
            if(data.getIntOr("laneCapacity",1)>1)tooltip.add(Component.translatable("screen.rematter.parallel_state",data.getIntOr("lanes",0),data.getIntOr("laneCapacity",1)));
            if (accessor.showDetails() && accessor.getBlockEntity() instanceof MachineBlockEntity machine
                    && MachinePortMode.configurable(machine.kind())) {
                // A side that can never be a connection names its reason instead of a role.
                var side = MachinePortMode.Side.local(accessor.getBlockState().getValue(MachineBlock.FACING), accessor.getHitResult().getDirection());
                var closed = dev.latrox.rematter.content.PortSides.closed(machine.kind(), side);
                tooltip.add(closed != null ? Component.translatable("port.rematter.closed." + closed.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY)
                        : Component.translatable("port.rematter.mode_message", machine.portMode(side).label()));
            }
            boolean industrial = accessor.getBlockState().getBlock() instanceof MachineBlock block
                    && IndustrialStructure.isIndustrial(block.kind());
            if (industrial && data.getIntOr("structureWidth", 0) > 0) {
                tooltip.add(Component.translatable("screen.rematter.structure_size", data.getIntOr("structureWidth", 0),
                        data.getIntOr("structureDepth", 0), data.getIntOr("structureHeight", 0)).withStyle(ChatFormatting.AQUA));
                if (accessor.showDetails() && data.getIntOr("structureColumns", 0) > 0) {
                    tooltip.add(Component.translatable("screen.rematter.structure_columns", data.getIntOr("structureColumns", 0)));
                    tooltip.add(Component.translatable("screen.rematter.structure_speed", data.getIntOr("structureRate", 0)));
                    if(data.getIntOr("coils",0)>0){
                        tooltip.add(Component.translatable("screen.rematter.structure_field",data.getIntOr("coils",0)));
                        tooltip.add(Component.translatable("screen.rematter.structure_room",dev.latrox.rematter.client.Quantities.number(16_000L*data.getIntOr("chambers",0))));
                    }else tooltip.add(Component.translatable("screen.rematter.structure_precision",data.getIntOr("separation",0)));
                    String[][] modules={{"grinding","grinding_unit"},{"mixing","mixing_basin"},{"separation","separation_unit"},
                            {"coils","field_coil"},{"chambers","reaction_chamber"}};
                    for (var module : modules) {
                        int count=data.getIntOr(module[0],0);
                        if(count>0)tooltip.add(Component.translatable("jade.rematter.module_count",count,Component.translatable("block.rematter."+module[1])));
                    }
                }
            }
            String structureDiagnostic = data.getStringOr("structureDiagnostic", "none");
            if (industrial && !structureDiagnostic.equals("none") && !structureDiagnostic.equals("ready")
                    && !structureDiagnostic.equals(data.getStringOr("status", "idle")))
                tooltip.add(Component.translatable("status.rematter." + structureDiagnostic).withStyle(ChatFormatting.GOLD));
            if (data.getBooleanOr("processes", false))
                tooltip.add(Component.translatable("jade.rematter.energy", data.getIntOr("energy", 0), data.getIntOr("energyCapacity", 0)));
            if(data.getIntOr("cellBays",0)==6) {
                tooltip.add(Component.translatable("screen.rematter.drive_cells",data.getIntOr("cellsInstalled",0)).withStyle(ChatFormatting.AQUA));
                if(data.getBooleanOr("cellUnreadable",false))tooltip.add(Component.translatable("screen.rematter.drive_blocked").withStyle(ChatFormatting.GOLD));
            }
            long capacity = data.getLongOr("capacity", 0);
            if (!data.getStringOr("storageSource", "none").equals("none"))
                tooltip.add(Component.translatable("screen.rematter.source_" + data.getStringOr("storageSource", "none")).withStyle(ChatFormatting.AQUA));
            if (capacity > 0) tooltip.add(Component.translatable("jade.rematter.matter", data.getLongOr("matter", 0), capacity));
            int duration = data.getIntOr("duration", 0);
            if (duration > 0) tooltip.add(Component.translatable("jade.rematter.progress", data.getIntOr("progress", 0), duration));
            if (data.getIntOr("fluidAmount", 0) > 0) {
                tooltip.add(Component.translatable("jade.rematter.fluid", SubstanceNames.fluid(data.getStringOr("fluidId", "")),
                        data.getIntOr("fluidAmount", 0)));
            }
            tooltip.add(Component.translatable(data.getBooleanOr("automation", false)
                    ? "screen.rematter.automation_on" : "screen.rematter.automation_off").withStyle(ChatFormatting.GRAY));
            int shown = Math.clamp(data.getIntOr("shown", 0), 0, MachineDataProvider.DETAIL_ROWS);
            for (int i = 0; i < shown; i++) {
                CompoundTag entry = data.getCompoundOrEmpty("entry" + i);
                boolean known = entry.getBooleanOr("known", false);
                tooltip.add(Component.translatable("jade.rematter.substance", matterName(entry, known), SubstanceNames.phase(entry.getStringOr("phase", "")),
                        entry.getLongOr("amount", 0)).withStyle(known ? ChatFormatting.AQUA : ChatFormatting.GOLD));
            }
            int remaining = data.getIntOr("substances", 0) - shown;
            if (shown > 0 && remaining > 0) tooltip.add(Component.translatable("jade.rematter.more_substances", remaining).withStyle(ChatFormatting.GRAY));
        }

        private static Component matterName(CompoundTag entry, boolean known) {
            String id = entry.getStringOr("id", "");
            Component name = SubstanceNames.substance(id, entry.getStringOr("phase", ""), known);
            return known ? SubstanceLabels.withSymbol(id, name) : name;
        }
    }
}
