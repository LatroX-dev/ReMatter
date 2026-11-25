package dev.latrox.rematter.compat.jade;

import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MatterCableBlock;
import java.util.List;
import net.minecraft.resources.Identifier;
import snownee.jade.api.Accessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/** Jade discovers this entry point only when Jade is installed. */
@WailaPlugin
public final class ReMatterJadePlugin implements IWailaPlugin {
    static final Identifier STATUS = id("machine_status");
    static final Identifier CABLE = id("cable");
    static final Identifier PORT = id("port");
    static final Identifier ENERGY_CUBE = id("energy_cube");
    static final Identifier ACCESS_POINT = id("wireless_access_point");
    static final Identifier EMITTER = id("matter_level_emitter");
    static final Identifier ENTANGLED_LINK = id("entangled_link");
    static final Identifier DIMENSIONAL_LINK = id("dimensional_entangled_link");
    static final Identifier SPATIAL_ANCHOR = id("spatial_anchor");
    static final Identifier CABLE_RELAY = id("matter_cable_relay");
    static final Identifier CELL_TRANSFER_STATION = id("cell_transfer_station");
    static final Identifier ITEMS = id("private_items");
    static final Identifier FLUIDS = id("private_fluids");
    static final Identifier ENERGY = id("private_energy");
    static final Identifier PROGRESS = id("private_progress");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineDataProvider.INSTANCE, MachineBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.ENERGY_CUBE, dev.latrox.rematter.energy.EnergyCubeBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.ACCESS_POINT, dev.latrox.rematter.content.WirelessAccessPointBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.EMITTER, dev.latrox.rematter.content.MatterLevelEmitterBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.ENTANGLED_LINK, dev.latrox.rematter.primal.EntangledLinkBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.DIMENSIONAL_LINK, dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.SPATIAL_ANCHOR, dev.latrox.rematter.content.SpatialAnchorBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.CABLE_RELAY, dev.latrox.rematter.content.MatterCableRelayBlockEntity.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.CABLE, MatterCableBlock.class);
        registration.registerBlockDataProvider(AuxiliaryDataProviders.CELL_TRANSFER_STATION, dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity.class);
        // Stop generic capability/container readers. The guarded provider below supplies this machine's HUD.
        // An empty (not null) result also prevents fallback readers from leaking a locked machine's contents.
        var items = new PrivateStorage<net.minecraft.world.item.ItemStack>(ITEMS);
        var fluids = new PrivateStorage<snownee.jade.api.view.FluidView.Data>(FLUIDS);
        var energy = new PrivateStorage<EnergyView.Data>(ENERGY);
        var progress = new PrivateStorage<snownee.jade.api.view.ProgressView.Data>(PROGRESS);
        registration.registerItemStorage(items, MachineBlockEntity.class);
        registration.registerItemStorage(items, dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity.class);
        registration.registerItemStorage(items, dev.latrox.rematter.energy.EnergyCubeBlockEntity.class);
        registration.registerItemStorage(items, dev.latrox.rematter.content.MachinePortBlockEntity.class);
        registration.registerItemStorage(items, dev.latrox.rematter.content.WirelessAccessPointBlockEntity.class);
        registration.registerItemStorage(items, dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity.class);
        registration.registerFluidStorage(fluids, MachineBlockEntity.class);
        registration.registerFluidStorage(fluids, dev.latrox.rematter.content.MachinePortBlockEntity.class);
        registration.registerEnergyStorage(energy, MachineBlockEntity.class);
        registration.registerEnergyStorage(energy, dev.latrox.rematter.energy.EnergyCubeBlockEntity.class);
        registration.registerEnergyStorage(energy, dev.latrox.rematter.content.MachinePortBlockEntity.class);
        registration.registerEnergyStorage(energy, dev.latrox.rematter.content.WirelessAccessPointBlockEntity.class);
        registration.registerEnergyStorage(energy, dev.latrox.rematter.content.SpatialAnchorBlockEntity.class);
        registration.registerEnergyStorage(energy, dev.latrox.rematter.primal.EntangledLinkBlock.class);
        registration.registerEnergyStorage(energy, dev.latrox.rematter.primal.DimensionalEntangledLinkBlock.class);
        // The cable forwards FE; its API capacity hint must not look like a battery in Jade.
        registration.registerEnergyStorage(energy, MatterCableBlock.class);
        registration.registerProgress(progress, MachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        JadeClient.register(registration);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("rematter", path);
    }

    private record PrivateStorage<T>(Identifier uid) implements IServerExtensionProvider<T> {
        @Override public Identifier getUid() { return uid; }
        @Override public int getDefaultPriority() { return -10_000; }
        @Override public List<ViewGroup<T>> getGroups(Accessor<?> accessor) { return List.of(); }
    }
}
