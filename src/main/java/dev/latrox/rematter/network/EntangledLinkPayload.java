package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.primal.EntangledLinkMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client-to-server rename & unpair action for regular Entangled Link. */
public final class EntangledLinkPayload {
    public static final String PROTOCOL = "1";

    private EntangledLinkPayload() {}

    public record Update(int containerId, String customName, boolean clearLink) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "entangled_link_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = new StreamCodec<>() {
            @Override public Update decode(RegistryFriendlyByteBuf buf) {
                return new Update(buf.readVarInt(), buf.readUtf(CustomNames.MAX_LENGTH), buf.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, Update payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeUtf(CustomNames.sanitize(payload.customName), CustomNames.MAX_LENGTH);
                buf.writeBoolean(payload.clearLink);
            }
        };
        @Override public Type<Update> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToServer(Update.TYPE, Update.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof EntangledLinkMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.update(sp, payload.customName(), payload.clearLink());
            }
        });
    }
}
