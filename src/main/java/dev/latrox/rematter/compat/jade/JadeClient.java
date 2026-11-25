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
        registration.registerBlockComponent(new EnergyCubeTooltip(), dev.latrox.rematter.energy.EnergyCubeBlock.class);
        registration.registerBlockComponent(new AccessPointTooltip(), dev.latrox.rematter.content.WirelessAccessPointBlock.class);
        registration.registerBlockComponent(new EmitterTooltip(), dev.latrox.rematter.content.MatterLevelEmitterBlock.class);
        registration.registerBlockComponent(new EntangledLinkTooltip(), dev.latrox.rematter.primal.EntangledLinkBlock.class);
        registration.registerBlockComponent(new DimensionalLinkTooltip(), dev.latrox.rematter.primal.DimensionalEntangledLinkBlock.class);
        registration.registerBlockComponent(new SpatialAnchorTooltip(), dev.latrox.rematter.content.SpatialAnchorBlock.class);
        registration.registerBlockComponent(new CableRelayTooltip(), dev.latrox.rematter.content.MatterCableRelayBlock.class);
        registration.registerBlockComponent(new CellTransferTooltip(), dev.latrox.rematter.celltransfer.CellTransferStationBlock.class);
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
            if (accessor.getServerData().contains(AuxiliaryDataProviders.CABLE_KEY)) {
                int mask = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.CABLE_KEY).getIntOr("blockedMask", 0);
                if (mask != 0) {
                    var sides = new java.util.ArrayList<String>();
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                        if ((mask & (1 << d.get3DDataValue())) != 0) {
                            sides.add(Component.translatable("direction.rematter." + d.getName()).getString());
                        }
                    }
                    if (!sides.isEmpty()) {
                        tooltip.add(Component.translatable("jade.rematter.cable_blocked_sides", String.join(", ", sides))
                                .withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            if (accessor.showDetails()) {
                tooltip.add(Component.translatable("jade.rematter.cable_connections", MatterCableBlock.connections(accessor.getBlockState())));
                tooltip.add(Component.translatable("jade.rematter.cable_disconnect_hint").withStyle(ChatFormatting.GRAY));
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
            String customName = data.getStringOr("customName", "");
            if (!customName.isEmpty()) {
                tooltip.add(Component.literal(customName).withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(StatusText.of(data.getStringOr("status", "idle"), data.getIntOr("staleOrders", 0), data.getIntOr("heldOrders", 0)));
            if (data.getBooleanOr("anchorInstalled", false)) {
                boolean active = data.getBooleanOr("anchorActive", false);
                tooltip.add(Component.translatable(active ? "jade.rematter.anchor_active" : "jade.rematter.anchor_inactive",
                        data.getIntOr("anchorForced", 0), data.getIntOr("anchorTotal", 0), data.getIntOr("anchorMax", 16))
                        .withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GOLD));
            }
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
            var machineKind = accessor.getBlockState().getBlock() instanceof MachineBlock block ? block.kind() : null;
            boolean industrial = machineKind != null && IndustrialStructure.isIndustrial(machineKind);
            if (data.getBooleanOr("energyMatrix", false)) {
                long mEnergy = data.getLongOr("matrixEnergy", 0L);
                long mCap = data.getLongOr("matrixCapacity", 0L);
                tooltip.add(Component.translatable("jade.rematter.energy",
                        dev.latrox.rematter.client.Quantities.compact(mEnergy),
                        dev.latrox.rematter.client.Quantities.compact(mCap)).withStyle(ChatFormatting.GOLD));
                long inRate = data.getLongOr("matrixInRate", 0L);
                long outRate = data.getLongOr("matrixOutRate", 0L);
                long maxRate = data.getLongOr("matrixTransferRate", 0L);
                if (inRate > 0 || outRate > 0) {
                    tooltip.add(Component.translatable("jade.rematter.matrix_flow",
                            dev.latrox.rematter.client.Quantities.compact(inRate),
                            dev.latrox.rematter.client.Quantities.compact(outRate)).withStyle(ChatFormatting.AQUA));
                }
                if (maxRate > 0) {
                    tooltip.add(Component.translatable("jade.rematter.cube_rate",
                            dev.latrox.rematter.client.Quantities.compact(maxRate)).withStyle(ChatFormatting.GRAY));
                }
            }
            if (data.getBooleanOr("energyReactor", false)) {
                long rEnergy = data.getLongOr("reactorEnergy", 0L);
                long rCap = data.getLongOr("reactorCapacity", 0L);
                tooltip.add(Component.translatable("jade.rematter.energy",
                        dev.latrox.rematter.client.Quantities.compact(rEnergy),
                        dev.latrox.rematter.client.Quantities.compact(rCap)).withStyle(ChatFormatting.GOLD));
                long gen = data.getLongOr("reactorGenRate", 0L);
                if (gen > 0L) {
                    tooltip.add(Component.translatable("jade.rematter.energy_rate_gen",
                            dev.latrox.rematter.client.Quantities.compact(gen)).withStyle(ChatFormatting.GREEN));
                }
                int flux = data.getIntOr("reactorFlux", 0);
                String mode = data.getStringOr("reactorFuelMode", "crystal");
                boolean cooled = data.getBooleanOr("reactorCooled", false);
                tooltip.add(Component.translatable(cooled ? "jade.rematter.reactor_status_cooled" : "jade.rematter.reactor_status",
                        Component.translatable("screen.rematter.energy_reactor.mode." + mode),
                        flux / 10).withStyle(cooled ? ChatFormatting.AQUA : ChatFormatting.GOLD));
            }
            if (machineKind == dev.latrox.rematter.content.MachineKind.PRIMAL_REACTOR && data.contains("primalStability")) {
                int stab = data.getIntOr("primalStability", 0);
                int maxStab = data.getIntOr("primalMaxStability", 0);
                tooltip.add(Component.translatable("jade.rematter.primal_stability", stab / 10, maxStab / 10)
                        .withStyle(maxStab > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
            }
            if (industrial && data.getIntOr("structureWidth", 0) > 0) {
                tooltip.add(Component.translatable("screen.rematter.structure_size", data.getIntOr("structureWidth", 0),
                        data.getIntOr("structureDepth", 0), data.getIntOr("structureHeight", 0)).withStyle(ChatFormatting.AQUA));
                if (accessor.showDetails() && data.getIntOr("structureColumns", 0) > 0) {
                    tooltip.add(Component.translatable("screen.rematter.structure_columns", data.getIntOr("structureColumns", 0)));
                    if (machineKind != dev.latrox.rematter.content.MachineKind.ENERGY_MATRIX
                            && machineKind != dev.latrox.rematter.content.MachineKind.ENERGY_REACTOR) {
                        tooltip.add(Component.translatable("screen.rematter.structure_speed", data.getIntOr("structureRate", 0)));
                    }
                    if (machineKind == dev.latrox.rematter.content.MachineKind.INDUSTRIAL_PREPARATOR) {
                        tooltip.add(Component.translatable("screen.rematter.structure_precision", data.getIntOr("separation", 0)));
                    } else if (machineKind == dev.latrox.rematter.content.MachineKind.ENERGY_MATRIX) {
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("chambers", 0), Component.translatable("block.rematter.energy_matrix_cell")));
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("coils", 0), Component.translatable("block.rematter.energy_matrix_provider")));
                    } else if (machineKind == dev.latrox.rematter.content.MachineKind.ENERGY_REACTOR) {
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("chambers", 0), Component.translatable("block.rematter.reactor_fuel_core")));
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("coils", 0), Component.translatable("block.rematter.reactor_induction_coil")));
                    } else {
                        int field = machineKind == dev.latrox.rematter.content.MachineKind.TRANSMUTATION_REACTOR
                                ? data.getIntOr("coils", 0) + data.getIntOr("chambers", 0)
                                : data.getIntOr("coils", 0);
                        tooltip.add(Component.translatable("screen.rematter.structure_field", field));
                        tooltip.add(Component.translatable("screen.rematter.structure_room", dev.latrox.rematter.client.Quantities.number(16_000L * data.getIntOr("chambers", 0))));
                    }
                    if (machineKind == dev.latrox.rematter.content.MachineKind.INDUSTRIAL_PREPARATOR) {
                        String[][] modules = {{"grinding", "grinding_unit"}, {"mixing", "mixing_basin"}, {"separation", "separation_unit"}};
                        for (var module : modules) {
                            int count = data.getIntOr(module[0], 0);
                            if (count > 0) tooltip.add(Component.translatable("jade.rematter.module_count", count, Component.translatable("block.rematter." + module[1])));
                        }
                    } else if (machineKind == dev.latrox.rematter.content.MachineKind.TRANSMUTATION_REACTOR) {
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("chambers", 0), Component.translatable("block.rematter.particle_emitter")));
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("coils", 0), Component.translatable("block.rematter.field_coil")));
                    } else if (machineKind == dev.latrox.rematter.content.MachineKind.PRIMAL_REACTOR) {
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("chambers", 0), Component.translatable("block.rematter.primal_condenser_core")));
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("coils", 0), Component.translatable("block.rematter.particle_emitter")));
                    } else if (machineKind != dev.latrox.rematter.content.MachineKind.ENERGY_MATRIX
                            && machineKind != dev.latrox.rematter.content.MachineKind.ENERGY_REACTOR) {
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("chambers", 0), Component.translatable("block.rematter.reaction_chamber")));
                        tooltip.add(Component.translatable("jade.rematter.module_count", data.getIntOr("coils", 0), Component.translatable("block.rematter.field_coil")));
                    }
                }
            }
            String structureDiagnostic = data.getStringOr("structureDiagnostic", "none");
            if (industrial && !structureDiagnostic.equals("none") && !structureDiagnostic.equals("ready")
                    && !structureDiagnostic.equals(data.getStringOr("status", "idle")))
                tooltip.add(Component.translatable("status.rematter." + structureDiagnostic).withStyle(ChatFormatting.GOLD));
            if (data.getBooleanOr("hasEnergy", data.getBooleanOr("processes", false))) {
                tooltip.add(Component.translatable("jade.rematter.energy",
                        dev.latrox.rematter.client.Quantities.number(data.getIntOr("energy", 0)),
                        dev.latrox.rematter.client.Quantities.number(data.getIntOr("energyCapacity", 0))));
                long rate = data.getLongOr("energyRate", 0);
                if (rate > 0) {
                    boolean generator = data.getBooleanOr("generator", false);
                    tooltip.add(Component.translatable(generator ? "jade.rematter.energy_rate_gen" : "jade.rematter.energy_rate_draw",
                            dev.latrox.rematter.client.Quantities.number(rate))
                            .withStyle(generator ? ChatFormatting.GREEN : ChatFormatting.GOLD));
                }
            }
            if (accessor.showDetails()) {
                int spd = data.getIntOr("speedUpgrades", 0);
                int eff = data.getIntOr("efficiencyUpgrades", 0);
                if (spd > 0 || eff > 0) {
                    tooltip.add(Component.translatable("jade.rematter.upgrades",
                            spd, dev.latrox.rematter.content.MachineEnergyPolicy.MAX_UPGRADES,
                            eff, dev.latrox.rematter.content.MachineEnergyPolicy.MAX_UPGRADES)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
            if (data.getBooleanOr("pressureInstalled", false)) {
                tooltip.add(Component.translatable("jade.rematter.pressure_installed").withStyle(ChatFormatting.AQUA));
            }
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

    private static final class EnergyCubeTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.ENERGY_CUBE; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.CUBE_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.CUBE_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            tooltip.add(Component.translatable("jade.rematter.energy",
                    dev.latrox.rematter.client.Quantities.number(data.getLongOr("energy", 0L)),
                    dev.latrox.rematter.client.Quantities.number(data.getLongOr("capacity", 0L))));
            tooltip.add(Component.translatable("jade.rematter.cube_rate",
                    dev.latrox.rematter.client.Quantities.number(data.getIntOr("rate", 0))).withStyle(ChatFormatting.AQUA));
            String flow = data.getStringOr("flow", "none");
            if (!flow.equals("none")) {
                tooltip.add(Component.translatable("jade.rematter.cube_flow." + flow)
                        .withStyle(flow.equals("in") ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            }
            String sideMode = data.getStringOr("sideMode", "");
            if (!sideMode.isEmpty()) {
                tooltip.add(Component.translatable("port.rematter.mode_message",
                        Component.translatable("screen.rematter.energy_cube.mode." + sideMode)).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static final class AccessPointTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.ACCESS_POINT; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.ACCESS_POINT_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.ACCESS_POINT_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            String customName = data.getStringOr("customName", "");
            if (!customName.isEmpty()) {
                tooltip.add(Component.literal(customName).withStyle(ChatFormatting.AQUA));
            }
            boolean powered = data.getBooleanOr("powered", false);
            boolean matterReady = data.getBooleanOr("matterReady", false);
            String status = powered ? (matterReady ? "ready" : "missing_network") : "missing_power";
            tooltip.add(StatusText.of(status, 0, 0));
            tooltip.add(Component.translatable("jade.rematter.energy",
                    dev.latrox.rematter.client.Quantities.number(data.getIntOr("energy", 0)),
                    dev.latrox.rematter.client.Quantities.number(data.getIntOr("capacity", 0))));
            if (data.getBooleanOr("dimensional", false)) {
                tooltip.add(Component.translatable("jade.rematter.wireless_dimensional").withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable("jade.rematter.wireless_range",
                        data.getIntOr("range", 0), data.getIntOr("boosters", 0)).withStyle(ChatFormatting.AQUA));
            }
            if (data.getBooleanOr("anchorActive", false)) {
                tooltip.add(Component.translatable("jade.rematter.anchor_loaded_single").withStyle(ChatFormatting.AQUA));
            }
            if (accessor.showDetails()) {
                tooltip.add(Component.translatable("jade.rematter.energy_rate_draw",
                        dev.latrox.rematter.client.Quantities.number(data.getIntOr("drain", 0))).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static final class EmitterTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.EMITTER; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.EMITTER_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.EMITTER_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            boolean emitting = data.getBooleanOr("emitting", false);
            boolean ready = data.getBooleanOr("networkReady", false);
            tooltip.add(StatusText.of(emitting ? "emitting" : (ready ? "ready" : "missing_network"), 0, 0));
            String targetId = data.getStringOr("targetId", "");
            if (!targetId.isEmpty()) {
                String phase = data.getStringOr("targetPhase", "solid");
                Component name = SubstanceLabels.withSymbol(targetId, SubstanceNames.substance(targetId, phase, true));
                String op = data.getBooleanOr("below", false) ? "<" : "≥";
                tooltip.add(Component.translatable("jade.rematter.emitter_rule", name, op,
                        dev.latrox.rematter.client.Quantities.number(data.getLongOr("threshold", 0))).withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.translatable("jade.rematter.emitter_stock",
                        dev.latrox.rematter.client.Quantities.number(data.getLongOr("stock", 0))).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("jade.rematter.emitter_unset").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static final class EntangledLinkTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.ENTANGLED_LINK; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.LINK_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.LINK_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            String customName = data.getStringOr("customName", "");
            if (!customName.isEmpty()) tooltip.add(Component.literal(customName).withStyle(ChatFormatting.AQUA));
            String state = data.getStringOr("state", "unpaired");
            int dist = data.getIntOr("distance", 0);
            if (state.equals("paired")) {
                tooltip.add(Component.translatable("screen.rematter.wrench_hud.link_paired", dist).withStyle(ChatFormatting.GREEN));
            } else if (state.equals("inactive")) {
                tooltip.add(Component.translatable("screen.rematter.wrench_hud.link_inactive", dist).withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(Component.translatable("screen.rematter.wrench_hud.link_unpaired").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static final class DimensionalLinkTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.DIMENSIONAL_LINK; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.DIMENSIONAL_LINK_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.DIMENSIONAL_LINK_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            String customName = data.getStringOr("customName", "");
            if (!customName.isEmpty()) tooltip.add(Component.literal(customName).withStyle(ChatFormatting.AQUA));
            String state = data.getStringOr("state", "unpaired");
            if (state.equals("paired")) {
                tooltip.add(Component.translatable("screen.rematter.dimensional_link.state_linked").withStyle(ChatFormatting.GREEN));
            } else if (state.equals("inactive")) {
                tooltip.add(Component.translatable("screen.rematter.dimensional_link.state_remote_unloaded").withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(Component.translatable("screen.rematter.dimensional_link.state_unpaired").withStyle(ChatFormatting.GRAY));
            }
            String peerDim = data.getStringOr("peerDim", "");
            if (!peerDim.isEmpty()) {
                var pos = net.minecraft.core.BlockPos.of(data.getLongOr("peerPos", 0L));
                String dimLabel = dev.latrox.rematter.content.CustomNames.shortDimensionLabel(peerDim);
                tooltip.add(Component.literal((dimLabel.isEmpty() ? "Overworld" : dimLabel)
                        + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")").withStyle(ChatFormatting.GRAY));
            }
            if (data.getBooleanOr("anchorActive", false)) {
                tooltip.add(Component.translatable("jade.rematter.anchor_loaded_single").withStyle(ChatFormatting.AQUA));
            }
        }
    }

    private static final class SpatialAnchorTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.SPATIAL_ANCHOR; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.SPATIAL_ANCHOR_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.SPATIAL_ANCHOR_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            String customName = data.getStringOr("customName", "");
            if (!customName.isEmpty()) tooltip.add(Component.literal(customName).withStyle(ChatFormatting.AQUA));
            boolean active = data.getBooleanOr("active", false);
            tooltip.add(Component.translatable("screen.rematter.spatial_anchor.reason." + data.getStringOr("reason", "idle"))
                    .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            tooltip.add(Component.translatable("jade.rematter.anchor_active",
                    data.getIntOr("forced", 0), data.getIntOr("total", 0), data.getIntOr("max", 32))
                    .withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jade.rematter.energy",
                    dev.latrox.rematter.client.Quantities.number(data.getIntOr("energy", 0)),
                    dev.latrox.rematter.client.Quantities.number(data.getIntOr("capacity", 0))));
        }
    }

    private static final class CableRelayTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.CABLE_RELAY; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.CABLE_RELAY_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.CABLE_RELAY_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            String customName = data.getStringOr("customName", "");
            if (!customName.isEmpty()) tooltip.add(Component.literal(customName).withStyle(ChatFormatting.AQUA));
            boolean open = data.getBooleanOr("open", true);
            tooltip.add(Component.translatable(open ? "screen.rematter.cable_relay.state_open" : "screen.rematter.cable_relay.state_closed")
                    .withStyle(open ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            String direction = data.getStringOr("direction", "bidirectional");
            if (direction.equals("one_way")) {
                tooltip.add(Component.translatable("screen.rematter.cable_relay.flow_endpoints",
                        Component.translatable("direction.rematter." + data.getStringOr("inSide", "south")),
                        Component.translatable("direction.rematter." + data.getStringOr("outSide", "north")))
                        .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.translatable("screen.rematter.cable_relay.direction.bidirectional")
                        .withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.translatable("screen.rematter.cable_relay.channel." + data.getStringOr("channel", "both"))
                    .withStyle(ChatFormatting.AQUA));
            if (data.getBooleanOr("filterActive", false)) {
                boolean wl = data.getBooleanOr("whitelist", false);
                int count = data.getIntOr("filterCount", 0);
                tooltip.add(Component.translatable("jade.rematter.cable_relay_filter",
                        Component.translatable(wl ? "screen.rematter.cable_relay.filter_whitelist"
                                : "screen.rematter.cable_relay.filter_blacklist"),
                        count).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static final class CellTransferTooltip implements IBlockComponentProvider {
        @Override public Identifier getUid() { return ReMatterJadePlugin.CELL_TRANSFER_STATION; }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AuxiliaryDataProviders.STATION_KEY)) return;
            CompoundTag data = accessor.getServerData().getCompoundOrEmpty(AuxiliaryDataProviders.STATION_KEY);
            if (data.getBooleanOr("locked", true)) {
                tooltip.add(Component.translatable("message.rematter.private_machine").withStyle(ChatFormatting.GRAY));
                return;
            }
            tooltip.add(Component.translatable("screen.rematter.transfer.status." + data.getStringOr("status", "insert_cells"))
                    .withStyle(ChatFormatting.AQUA));
            if (data.getBooleanOr("srcInstalled", false)) {
                tooltip.add(Component.translatable("jade.rematter.transfer_source",
                        dev.latrox.rematter.client.Quantities.number(data.getLongOr("srcAmount", 0)),
                        dev.latrox.rematter.client.Quantities.number(data.getLongOr("srcCapacity", 0))).withStyle(ChatFormatting.GRAY));
            }
            if (data.getBooleanOr("dstInstalled", false)) {
                tooltip.add(Component.translatable("jade.rematter.transfer_target",
                        dev.latrox.rematter.client.Quantities.number(data.getLongOr("dstAmount", 0)),
                        dev.latrox.rematter.client.Quantities.number(data.getLongOr("dstCapacity", 0))).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
