package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The open terminal's device list contains display data only. */
public record TerminalDevicesPayload(int containerId, List<Device> devices) implements CustomPacketPayload {
    public static final int MAX_DEVICES = 512;
    public static final TerminalDevicesPayload EMPTY = new TerminalDevicesPayload(-1, List.of());
    public static final Type<TerminalDevicesPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "terminal_devices"));

    public TerminalDevicesPayload {
        if (devices.size() > MAX_DEVICES) throw new IllegalArgumentException("Too many terminal devices");
        devices = List.copyOf(devices);
    }

    public record Device(BlockPos position, MachineKind kind, String status, String recipe, String recipeId,
                         int progress, int duration, int queuedBatches, boolean canQueue) {
        public Device {
            position = position.immutable();
            status = clip(status, 64);
            recipe = clip(recipe, 256);
            if (recipeId.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH || canQueue && recipeId.isEmpty()
                    || progress < 0 || duration < 0 || queuedBatches < 0 || queuedBatches > 64) {
                throw new IllegalArgumentException("Invalid terminal device progress");
            }
        }
        private static String clip(String text, int maximum) {
            if (text.length() <= maximum) return text;
            int end = Character.isHighSurrogate(text.charAt(maximum - 1)) ? maximum - 1 : maximum;
            return text.substring(0, end);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalDevicesPayload> CODEC = new StreamCodec<>() {
        @Override public TerminalDevicesPayload decode(RegistryFriendlyByteBuf buffer) {
            int container = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_DEVICES) throw new IllegalArgumentException("Invalid terminal device count");
            var devices = new ArrayList<Device>(count);
            for (int i = 0; i < count; i++) {
                devices.add(new Device(buffer.readBlockPos(), buffer.readEnum(MachineKind.class),
                        buffer.readUtf(64), buffer.readUtf(256), buffer.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH),
                        buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readBoolean()));
            }
            return new TerminalDevicesPayload(container, devices);
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, TerminalDevicesPayload payload) {
            buffer.writeVarInt(payload.containerId());
            buffer.writeVarInt(payload.devices().size());
            for (var device : payload.devices()) {
                buffer.writeBlockPos(device.position());
                buffer.writeEnum(device.kind());
                buffer.writeUtf(device.status(), 64);
                buffer.writeUtf(device.recipe(), 256);
                buffer.writeUtf(device.recipeId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
                buffer.writeVarInt(device.progress());
                buffer.writeVarInt(device.duration());
                buffer.writeVarInt(device.queuedBatches());
                buffer.writeBoolean(device.canQueue());
            }
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == payload.containerId()) {
                menu.acceptTerminalDevices(payload);
            }
        });
    }

    @Override public Type<TerminalDevicesPayload> type() { return TYPE; }
}
