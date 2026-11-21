package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.WirelessMatterTerminalItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client hotkey request to open a carried or Curios-equipped Wireless Matter Terminal. */
public record OpenWirelessTerminalPayload() implements CustomPacketPayload {
    public static final OpenWirelessTerminalPayload INSTANCE = new OpenWirelessTerminalPayload();
    public static final Type<OpenWirelessTerminalPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "open_wireless_terminal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWirelessTerminalPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override public Type<OpenWirelessTerminalPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
                WirelessMatterTerminalItem.openFromHotkey(player);
            }
        });
    }
}
