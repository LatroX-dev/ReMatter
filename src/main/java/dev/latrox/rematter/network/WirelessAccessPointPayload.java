package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.WirelessAccessPointMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Payloads for Wireless Access Point custom name and Anchor Upgrade status sync. */
public final class WirelessAccessPointPayload {
    private WirelessAccessPointPayload() {}

    public record Rename(int containerId, String customName) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "wireless_ap_rename"));

        public Rename {
            if (containerId < 0) throw new IllegalArgumentException("Invalid Wireless Access Point rename");
            customName = CustomNames.sanitize(customName);
        }

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

    public record State(int containerId, String customName, int anchorForced, int anchorTotal,
                        int anchorMax, String anchorReason) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "wireless_ap_state"));

        public State {
            if (containerId < 0) throw new IllegalArgumentException("Invalid Wireless Access Point state");
            customName = CustomNames.sanitize(customName);
            anchorReason = anchorReason == null ? "idle" : anchorReason;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            @Override public State decode(RegistryFriendlyByteBuf buf) {
                return new State(buf.readVarInt(), buf.readUtf(CustomNames.MAX_LENGTH),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32));
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, State payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeUtf(payload.customName, CustomNames.MAX_LENGTH);
                buf.writeVarInt(payload.anchorForced);
                buf.writeVarInt(payload.anchorTotal);
                buf.writeVarInt(payload.anchorMax);
                buf.writeUtf(payload.anchorReason, 32);
            }
        };

        @Override public Type<State> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Rename.TYPE, Rename.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof WirelessAccessPointMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.renameFromClient(player, payload.customName());
            }
        });
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof WirelessAccessPointMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.acceptState(payload);
            }
        });
    }
}
