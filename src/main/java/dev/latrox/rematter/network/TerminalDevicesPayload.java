package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineRunState;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Changed rows of the open terminal's device list, display data only. A reset replaces the client's list;
 * otherwise only changed and removed devices travel. {@code total} counts every manageable device.
 */
public record TerminalDevicesPayload(int containerId, boolean reset, int total, List<Device> changed, List<BlockPos> removed)
        implements CustomPacketPayload {
    public static final String PROTOCOL = "3";
    public static final int MAX_DEVICES = 512;
    public static final Type<TerminalDevicesPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "terminal_devices"));

    public TerminalDevicesPayload {
        changed = List.copyOf(changed);
        removed = removed.stream().map(BlockPos::immutable).toList();
        if (total < 0 || changed.size() > MAX_DEVICES || removed.size() > MAX_DEVICES) throw new IllegalArgumentException("Too many terminal devices");
    }

    /** The client names the selected recipe from its ID. */
    public record Device(BlockPos position, MachineKind kind, String status, String recipeId,
                         int progress, int duration, int queuedBatches, boolean canQueue,
                         long target,long targetStock,boolean networkTarget,int workingBatches,int batchCapacity) {
        public Device(BlockPos position,MachineKind kind,String status,String recipeId,int progress,int duration,int queuedBatches,boolean canQueue) {
            this(position,kind,status,recipeId,progress,duration,queuedBatches,canQueue,0,0,false,0,1);
        }
        public Device {
            position = position.immutable();
            if (status.isEmpty() || status.length() > 64 || recipeId.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH || canQueue && recipeId.isEmpty()
                    || progress < 0 || duration < 0 || queuedBatches < 0 || queuedBatches > MachineRunState.MAX_QUEUED_BATCHES
                    ||target<0||target>dev.latrox.rematter.content.MachineSettings.MAX_TARGET||targetStock< -1
                    ||workingBatches<0||workingBatches>batchCapacity||batchCapacity<1||batchCapacity>4) {
                throw new IllegalArgumentException("Invalid terminal device");
            }
        }
    }

    /** The client's current device list, replaced as a whole whenever a change arrives. */
    public record Listing(int containerId, int total, List<Device> devices) {
        public static final Listing EMPTY = new Listing(-1, 0, List.of());
        public Listing { devices = List.copyOf(devices); }
        public boolean truncated() { return devices.size() < total; }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalDevicesPayload> CODEC = new StreamCodec<>() {
        @Override public TerminalDevicesPayload decode(RegistryFriendlyByteBuf buffer) {
            int container = buffer.readVarInt();
            boolean reset = buffer.readBoolean();
            int total = buffer.readVarInt();
            int count = MachineSnapshot.count(buffer, MAX_DEVICES);
            var changed = new ArrayList<Device>(count);
            for (int i = 0; i < count; i++) {
                changed.add(new Device(buffer.readBlockPos(), buffer.readEnum(MachineKind.class), buffer.readUtf(64),
                        buffer.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readBoolean(),buffer.readVarLong(),buffer.readVarLong(),buffer.readBoolean(),buffer.readVarInt(),buffer.readVarInt()));
            }
            int removedCount = MachineSnapshot.count(buffer, MAX_DEVICES);
            var removed = new ArrayList<BlockPos>(removedCount);
            for (int i = 0; i < removedCount; i++) removed.add(buffer.readBlockPos());
            return new TerminalDevicesPayload(container, reset, total, changed, removed);
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, TerminalDevicesPayload payload) {
            buffer.writeVarInt(payload.containerId);
            buffer.writeBoolean(payload.reset);
            buffer.writeVarInt(payload.total);
            buffer.writeVarInt(payload.changed.size());
            for (var device : payload.changed) {
                buffer.writeBlockPos(device.position());
                buffer.writeEnum(device.kind());
                buffer.writeUtf(device.status(), 64);
                buffer.writeUtf(device.recipeId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
                buffer.writeVarInt(device.progress());
                buffer.writeVarInt(device.duration());
                buffer.writeVarInt(device.queuedBatches());
                buffer.writeBoolean(device.canQueue());
                buffer.writeVarLong(device.target());buffer.writeVarLong(device.targetStock());buffer.writeBoolean(device.networkTarget());
                buffer.writeVarInt(device.workingBatches());buffer.writeVarInt(device.batchCapacity());
            }
            buffer.writeVarInt(payload.removed.size());
            for (var position : payload.removed) buffer.writeBlockPos(position);
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToClient(TYPE, CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == payload.containerId()) {
                menu.acceptTerminalDevices(payload);
            }
        });
    }

    @Override public Type<TerminalDevicesPayload> type() { return TYPE; }
}
