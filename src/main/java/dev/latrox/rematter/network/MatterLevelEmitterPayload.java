package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.CellPartitionData;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MatterLevelEmitterBlockEntity;
import dev.latrox.rematter.content.MatterLevelEmitterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/** Network payloads for Matter Level Emitter C2S configuration and S2C live stock/signal telemetry. */
public final class MatterLevelEmitterPayload {
    private MatterLevelEmitterPayload() {}

    public record Configure(int containerId, @Nullable SubstanceKey target, long threshold, boolean emitWhenBelow)
            implements CustomPacketPayload {
        public static final Type<Configure> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "matter_level_emitter_configure"));

        public Configure {
            if (containerId < 0 || threshold < 0 || threshold > MatterLevelEmitterBlockEntity.MAX_THRESHOLD
                    || target != null && target.id().toString().length() > CellPartitionData.MAX_ID_LENGTH) {
                throw new IllegalArgumentException("Invalid Matter Level Emitter configuration");
            }
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC = new StreamCodec<>() {
            @Override
            public Configure decode(RegistryFriendlyByteBuf buf) {
                int id = buf.readVarInt();
                var target = buf.readBoolean() ? CellTransferPayload.readKey(buf) : null;
                long threshold = buf.readVarLong();
                boolean below = buf.readBoolean();
                return new Configure(id, target, threshold, below);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Configure payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeBoolean(payload.target != null);
                if (payload.target != null) CellTransferPayload.writeKey(buf, payload.target);
                buf.writeVarLong(payload.threshold);
                buf.writeBoolean(payload.emitWhenBelow);
            }
        };

        @Override public Type<Configure> type() { return TYPE; }
    }

    public record Rename(int containerId, String customName) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "matter_level_emitter_rename"));
        public Rename { customName = CustomNames.sanitize(customName); }
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> CODEC = new StreamCodec<>() {
            @Override public Rename decode(RegistryFriendlyByteBuf buf) {
                return new Rename(buf.readVarInt(), buf.readUtf(CustomNames.MAX_LENGTH));
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, Rename payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeUtf(payload.customName, CustomNames.MAX_LENGTH);
            }
        };
        @Override public Type<Rename> type() { return TYPE; }
    }

    public record State(int containerId, @Nullable SubstanceKey target, long threshold, boolean emitWhenBelow,
                        long observedStock, boolean networkReady, boolean emitting, String customName)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "matter_level_emitter_state"));

        public State(int containerId, @Nullable SubstanceKey target, long threshold, boolean emitWhenBelow,
                     long observedStock, boolean networkReady, boolean emitting) {
            this(containerId, target, threshold, emitWhenBelow, observedStock, networkReady, emitting, "");
        }

        public State {
            if (containerId < 0 || threshold < 0 || threshold > MatterLevelEmitterBlockEntity.MAX_THRESHOLD
                    || observedStock < 0 || target != null && target.id().toString().length() > CellPartitionData.MAX_ID_LENGTH) {
                throw new IllegalArgumentException("Invalid Matter Level Emitter state");
            }
            customName = CustomNames.sanitize(customName);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            @Override
            public State decode(RegistryFriendlyByteBuf buf) {
                int id = buf.readVarInt();
                var target = buf.readBoolean() ? CellTransferPayload.readKey(buf) : null;
                long threshold = buf.readVarLong();
                boolean below = buf.readBoolean();
                long stock = buf.readVarLong();
                boolean ready = buf.readBoolean();
                boolean emitting = buf.readBoolean();
                String name = buf.readUtf(CustomNames.MAX_LENGTH);
                return new State(id, target, threshold, below, stock, ready, emitting, name);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, State payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeBoolean(payload.target != null);
                if (payload.target != null) CellTransferPayload.writeKey(buf, payload.target);
                buf.writeVarLong(payload.threshold);
                buf.writeBoolean(payload.emitWhenBelow);
                buf.writeVarLong(payload.observedStock);
                buf.writeBoolean(payload.networkReady);
                buf.writeBoolean(payload.emitting);
                buf.writeUtf(payload.customName, CustomNames.MAX_LENGTH);
            }
        };

        @Override public Type<State> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Configure.TYPE, Configure.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MatterLevelEmitterMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.configureFromClient(player, payload);
            }
        });
        registrar.playToServer(Rename.TYPE, Rename.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MatterLevelEmitterMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.renameFromClient(player, payload.customName());
            }
        });
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof MatterLevelEmitterMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.acceptState(payload);
            }
        });
    }
}
