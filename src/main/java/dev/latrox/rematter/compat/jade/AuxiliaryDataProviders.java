package dev.latrox.rematter.compat.jade;

import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.compat.TeamAccess;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity;
import dev.latrox.rematter.content.MatterLevelEmitterBlockEntity;
import dev.latrox.rematter.content.SpatialAnchorBlockEntity;
import dev.latrox.rematter.content.WirelessAccessPointBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** Server-authorized Jade data providers for non-MachineBlock ReMatter devices. */
final class AuxiliaryDataProviders {
    static final String CUBE_KEY = "rematter:energy_cube";
    static final String ACCESS_POINT_KEY = "rematter:wireless_access_point";
    static final String EMITTER_KEY = "rematter:matter_level_emitter";
    static final String LINK_KEY = "rematter:entangled_link";
    static final String DIMENSIONAL_LINK_KEY = "rematter:dimensional_entangled_link";
    static final String SPATIAL_ANCHOR_KEY = "rematter:spatial_anchor";
    static final String CABLE_RELAY_KEY = "rematter:matter_cable_relay";
    static final String CABLE_KEY = "rematter:matter_cable";
    static final String STATION_KEY = "rematter:cell_transfer_station";

    private AuxiliaryDataProviders() {}

    static final IServerDataProvider<BlockAccessor> ENERGY_CUBE = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.ENERGY_CUBE; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof EnergyCubeBlockEntity cube)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = cube.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed) {
                if (!cube.customName().isEmpty()) view.putString("customName", cube.customName());
                view.putLong("energy", cube.energy());
                view.putLong("capacity", cube.tier().capacity());
                view.putInt("rate", cube.tier().transferRate());
                view.putString("flow", accessor.getBlockState().getValue(EnergyCubeBlock.FLOW).getSerializedName());
                view.putString("sideMode", cube.sideMode(accessor.getHitResult().getDirection()).name().toLowerCase(java.util.Locale.ROOT));
            }
            data.put(CUBE_KEY, view);
        }
    };

    static final IServerDataProvider<BlockAccessor> ACCESS_POINT = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.ACCESS_POINT; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof WirelessAccessPointBlockEntity ap)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = ap.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed) {
                if (!ap.customName().isEmpty()) view.putString("customName", ap.customName());
                view.putBoolean("powered", ap.powered());
                view.putBoolean("matterReady", ap.matterReady());
                view.putInt("energy", ap.energy());
                view.putInt("capacity", WirelessAccessPointBlockEntity.CAPACITY);
                view.putInt("range", ap.effectiveRange());
                view.putInt("boosters", ap.boosterCount());
                view.putInt("drain", ap.effectiveDrain());
                view.putBoolean("dimensional", ap.hasDimensionalCard());
                view.putBoolean("anchorActive", ap.anchorStatus().active());
            }
            data.put(ACCESS_POINT_KEY, view);
        }
    };

    static final IServerDataProvider<BlockAccessor> EMITTER = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.EMITTER; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MatterLevelEmitterBlockEntity emitter)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = emitter.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed) {
                if (!emitter.customName().isEmpty()) view.putString("customName", emitter.customName());
                view.putBoolean("emitting", emitter.emitting());
                view.putBoolean("networkReady", emitter.networkReady());
                view.putBoolean("below", emitter.emitWhenBelow());
                view.putLong("threshold", emitter.threshold());
                view.putLong("stock", emitter.observedStock());
                var target = emitter.target();
                if (target != null) {
                    view.putString("targetId", target.id().toString());
                    view.putString("targetPhase", target.phase().name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            data.put(EMITTER_KEY, view);
        }
    };

    static final IServerDataProvider<BlockAccessor> ENTANGLED_LINK = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.ENTANGLED_LINK; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof EntangledLinkBlockEntity link)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = link.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed && accessor.getLevel() instanceof ServerLevel server) {
                if (!link.customName().isEmpty()) view.putString("customName", link.customName());
                var diag = EntangledLinks.inspect(server, link);
                view.putString("state", diag.state().name().toLowerCase(java.util.Locale.ROOT));
                view.putInt("distance", diag.distance());
            }
            data.put(LINK_KEY, view);
        }
    };

    static final IServerDataProvider<BlockAccessor> DIMENSIONAL_LINK = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.DIMENSIONAL_LINK; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof DimensionalEntangledLinkBlockEntity link)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = link.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed && accessor.getLevel() instanceof ServerLevel server) {
                if (!link.customName().isEmpty()) view.putString("customName", link.customName());
                var diag = EntangledLinks.inspectDimensional(server, link);
                view.putString("state", diag.state().name().toLowerCase(java.util.Locale.ROOT));
                if (link.peerDimension() != null && link.peerPos() != null) {
                    view.putString("peerDim", link.peerDimension().identifier().toString());
                    view.putLong("peerPos", link.peerPos().asLong());
                }
                view.putBoolean("anchorInstalled", link.hasAnchorUpgrade());
                view.putBoolean("anchorActive", link.anchorStatus().active());
            }
            data.put(DIMENSIONAL_LINK_KEY, view);
        }
    };

    static final IServerDataProvider<BlockAccessor> SPATIAL_ANCHOR = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.SPATIAL_ANCHOR; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof SpatialAnchorBlockEntity anchor)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = anchor.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed) {
                if (!anchor.customName().isEmpty()) view.putString("customName", anchor.customName());
                var ast = anchor.anchorStatus();
                view.putBoolean("active", ast.active());
                view.putString("reason", ast.reason());
                view.putInt("radius", anchor.radius());
                view.putInt("forced", ast.forcedChunks());
                view.putInt("total", ast.ownerTotalChunks());
                view.putInt("max", ast.maxChunks());
                view.putInt("energy", anchor.energy());
                view.putInt("capacity", SpatialAnchorBlockEntity.CAPACITY);
                view.putInt("drain", anchor.drainPerTick());
            }
            data.put(SPATIAL_ANCHOR_KEY, view);
        }
    };

    static final IServerDataProvider<BlockAccessor> CABLE_RELAY = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.CABLE_RELAY; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MatterCableRelayBlockEntity relay)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = relay.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed) {
                if (!relay.customName().isEmpty()) view.putString("customName", relay.customName());
                view.putBoolean("open", relay.isOpen());
                view.putString("channel", relay.channelMode().key());
                view.putString("direction", relay.directionMode().key());
                view.putString("inSide", relay.inputSide().getName());
                view.putString("outSide", relay.outputSide().getName());
                view.putInt("energyLimit", relay.energyLimit());
                view.putInt("matterLimit", relay.matterLimit());
                view.putBoolean("filterActive", relay.hasFilter());
                view.putInt("stateMask", relay.stateMask());
                view.putBoolean("whitelist", relay.filterWhitelist());
                view.putInt("filterCount", relay.filterSubstances().size());
            }
            data.put(CABLE_RELAY_KEY, view);
        }
    };

    static final IServerDataProvider<BlockAccessor> CABLE = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.CABLE; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel server)) return;
            var pos = accessor.getPosition();
            if (!dev.latrox.rematter.content.CableOwners.canAccess(server, pos, accessor.getPlayer())) return;
            int mask = dev.latrox.rematter.content.CableOwners.blockedSides(server, pos);
            if (mask != 0) {
                CompoundTag view = new CompoundTag();
                view.putInt("blockedMask", mask);
                data.put(CABLE_KEY, view);
            }
        }
    };

    static final IServerDataProvider<BlockAccessor> CELL_TRANSFER_STATION = new IServerDataProvider<>() {
        @Override public Identifier getUid() { return ReMatterJadePlugin.CELL_TRANSFER_STATION; }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof CellTransferStationBlockEntity station)) return;
            CompoundTag view = new CompoundTag();
            boolean allowed = station.canAccess(accessor.getPlayer());
            view.putBoolean("locked", !allowed);
            if (allowed) {
                var snap = station.snapshot(0);
                view.putString("status", snap.status());
                if (snap.slots().size() >= 2) {
                    var src = snap.slots().get(0);
                    var dst = snap.slots().get(1);
                    view.putBoolean("srcInstalled", src.kind() != 0);
                    view.putLong("srcAmount", src.amount());
                    view.putLong("srcCapacity", src.capacity());
                    view.putBoolean("dstInstalled", dst.kind() != 0);
                    view.putLong("dstAmount", dst.amount());
                    view.putLong("dstCapacity", dst.capacity());
                }
            }
            data.put(STATION_KEY, view);
        }
    };
}
