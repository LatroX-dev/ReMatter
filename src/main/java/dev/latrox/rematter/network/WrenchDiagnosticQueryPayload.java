package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ConfigurationCardItem;
import dev.latrox.rematter.content.MatterWrenchItem;
import dev.latrox.rematter.content.WrenchDiagnostics;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Throttled look-target query sent while holding a Matter Wrench or Configuration Card. */
public record WrenchDiagnosticQueryPayload(BlockPos pos, Direction face) implements CustomPacketPayload {
    public static final Type<WrenchDiagnosticQueryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "wrench_diagnostic_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchDiagnosticQueryPayload> CODEC = new StreamCodec<>() {
        @Override
        public WrenchDiagnosticQueryPayload decode(RegistryFriendlyByteBuf buf) {
            return new WrenchDiagnosticQueryPayload(
                    buf.readBlockPos(),
                    Direction.from3DDataValue(buf.readUnsignedByte()));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, WrenchDiagnosticQueryPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeByte(payload.face().get3DDataValue());
        }
    };

    public WrenchDiagnosticQueryPayload {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(face);
    }

    private static boolean holdsDiagnosticTool(Player player) {
        return isTool(player.getMainHandItem()) || isTool(player.getOffhandItem());
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof MatterWrenchItem || stack.getItem() instanceof ConfigurationCardItem;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || player.isSpectator()
                    || !(player.level() instanceof ServerLevel server)
                    || !holdsDiagnosticTool(player)
                    || player.distanceToSqr(
                            payload.pos().getX() + 0.5,
                            payload.pos().getY() + 0.5,
                            payload.pos().getZ() + 0.5) > 64.0) {
                return;
            }
            var reply = WrenchDiagnostics.inspect(server, payload.pos(), payload.face(), player);
            if (reply != null) {
                PacketDistributor.sendToPlayer(player, reply);
            }
        });
    }

    @Override
    public Type<WrenchDiagnosticQueryPayload> type() {
        return TYPE;
    }
}
