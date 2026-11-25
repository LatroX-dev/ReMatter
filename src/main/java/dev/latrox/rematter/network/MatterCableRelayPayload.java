package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.CellPartitionData;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.ChannelMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.DirectionMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.GateMode;
import dev.latrox.rematter.content.MatterCableRelayMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Payloads for Matter Cable Relay C2S configuration and S2C live gate/direction/filter telemetry. */
public final class MatterCableRelayPayload {
    private MatterCableRelayPayload() {}

    private static List<SubstanceKey> readFilter(RegistryFriendlyByteBuf buf) {
        int count = Math.clamp(buf.readVarInt(), 0, MatterCableRelayBlockEntity.MAX_FILTER_ENTRIES);
        var list = new ArrayList<SubstanceKey>(count);
        Phase[] phases = Phase.values();
        for (int i = 0; i < count; i++) {
            String rawId = buf.readUtf(CellPartitionData.MAX_ID_LENGTH);
            int pOrd = buf.readVarInt();
            if (pOrd >= 0 && pOrd < phases.length) {
                try {
                    list.add(SubstanceKey.of(rawId, phases[pOrd]));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return MatterCableRelayBlockEntity.sanitizeFilter(list);
    }

    private static void writeFilter(RegistryFriendlyByteBuf buf, List<SubstanceKey> keys) {
        buf.writeVarInt(keys.size());
        for (var key : keys) {
            buf.writeUtf(key.id().toString(), CellPartitionData.MAX_ID_LENGTH);
            buf.writeVarInt(key.phase().ordinal());
        }
    }

    public record Configure(int containerId, GateMode gateMode, ChannelMode channelMode, DirectionMode directionMode,
                            int energyLimit, int matterLimit, int stateMask, boolean filterWhitelist,
                            List<SubstanceKey> filterSubstances, String customName)
            implements CustomPacketPayload {
        public static final Type<Configure> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "matter_cable_relay_configure"));

        public Configure(int containerId, GateMode gateMode, ChannelMode channelMode, int energyLimit, int matterLimit) {
            this(containerId, gateMode, channelMode, DirectionMode.BIDIRECTIONAL, energyLimit, matterLimit,
                    MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), "");
        }

        public Configure(int containerId, GateMode gateMode, ChannelMode channelMode, int energyLimit, int matterLimit, String customName) {
            this(containerId, gateMode, channelMode, DirectionMode.BIDIRECTIONAL, energyLimit, matterLimit,
                    MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), customName);
        }

        public Configure {
            if (containerId < 0 || gateMode == null || channelMode == null || directionMode == null
                    || energyLimit < 0 || energyLimit > MatterCableRelayBlockEntity.MAX_ENERGY_LIMIT
                    || matterLimit < 0 || matterLimit > MatterCableRelayBlockEntity.MAX_MATTER_LIMIT) {
                throw new IllegalArgumentException("Invalid Matter Cable Relay configuration");
            }
            stateMask &= MatterCableRelayBlockEntity.STATE_ALL;
            filterSubstances = MatterCableRelayBlockEntity.sanitizeFilter(filterSubstances);
            customName = CustomNames.sanitize(customName);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC = new StreamCodec<>() {
            @Override
            public Configure decode(RegistryFriendlyByteBuf buf) {
                return new Configure(
                        buf.readVarInt(),
                        GateMode.byIndex(buf.readVarInt()),
                        ChannelMode.byIndex(buf.readVarInt()),
                        DirectionMode.byIndex(buf.readVarInt()),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readBoolean(),
                        readFilter(buf),
                        buf.readUtf(CustomNames.MAX_LENGTH));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Configure payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeVarInt(payload.gateMode.ordinal());
                buf.writeVarInt(payload.channelMode.ordinal());
                buf.writeVarInt(payload.directionMode.ordinal());
                buf.writeVarInt(payload.energyLimit);
                buf.writeVarInt(payload.matterLimit);
                buf.writeVarInt(payload.stateMask);
                buf.writeBoolean(payload.filterWhitelist);
                writeFilter(buf, payload.filterSubstances);
                buf.writeUtf(payload.customName, CustomNames.MAX_LENGTH);
            }
        };

        @Override public Type<Configure> type() { return TYPE; }
    }

    public record State(int containerId, GateMode gateMode, ChannelMode channelMode, DirectionMode directionMode,
                        Direction facing, int energyLimit, int matterLimit, int stateMask, boolean filterWhitelist,
                        List<SubstanceKey> filterSubstances, boolean open, boolean powered, String customName)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "matter_cable_relay_state"));

        public State {
            if (containerId < 0 || gateMode == null || channelMode == null || directionMode == null || facing == null
                    || energyLimit < 0 || energyLimit > MatterCableRelayBlockEntity.MAX_ENERGY_LIMIT
                    || matterLimit < 0 || matterLimit > MatterCableRelayBlockEntity.MAX_MATTER_LIMIT) {
                throw new IllegalArgumentException("Invalid Matter Cable Relay state");
            }
            stateMask &= MatterCableRelayBlockEntity.STATE_ALL;
            filterSubstances = MatterCableRelayBlockEntity.sanitizeFilter(filterSubstances);
            customName = CustomNames.sanitize(customName);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            @Override
            public State decode(RegistryFriendlyByteBuf buf) {
                int cid = buf.readVarInt();
                GateMode g = GateMode.byIndex(buf.readVarInt());
                ChannelMode c = ChannelMode.byIndex(buf.readVarInt());
                DirectionMode d = DirectionMode.byIndex(buf.readVarInt());
                int fOrd = buf.readVarInt();
                Direction[] dirs = Direction.values();
                Direction f = fOrd >= 0 && fOrd < dirs.length ? dirs[fOrd] : Direction.NORTH;
                int eLim = buf.readVarInt();
                int mLim = buf.readVarInt();
                int sMask = buf.readVarInt();
                boolean wl = buf.readBoolean();
                List<SubstanceKey> filter = readFilter(buf);
                boolean open = buf.readBoolean();
                boolean powered = buf.readBoolean();
                String name = buf.readUtf(CustomNames.MAX_LENGTH);
                return new State(cid, g, c, d, f, eLim, mLim, sMask, wl, filter, open, powered, name);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, State payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeVarInt(payload.gateMode.ordinal());
                buf.writeVarInt(payload.channelMode.ordinal());
                buf.writeVarInt(payload.directionMode.ordinal());
                buf.writeVarInt(payload.facing.ordinal());
                buf.writeVarInt(payload.energyLimit);
                buf.writeVarInt(payload.matterLimit);
                buf.writeVarInt(payload.stateMask);
                buf.writeBoolean(payload.filterWhitelist);
                writeFilter(buf, payload.filterSubstances);
                buf.writeBoolean(payload.open);
                buf.writeBoolean(payload.powered);
                buf.writeUtf(payload.customName, CustomNames.MAX_LENGTH);
            }
        };

        @Override public Type<State> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Configure.TYPE, Configure.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MatterCableRelayMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.configureFromClient(player, payload);
            }
        });
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof MatterCableRelayMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.acceptState(payload);
            }
        });
    }
}
