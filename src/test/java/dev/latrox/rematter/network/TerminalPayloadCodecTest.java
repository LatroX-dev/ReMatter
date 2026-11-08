package dev.latrox.rematter.network;

import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineRunState;
import dev.latrox.rematter.content.MachineSnapshot;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalPayloadCodecTest {
    private static final BlockPos POSITION = new BlockPos(12, -40, 300_000);
    private static final String RECIPE = "rematter:water_electrolysis";

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, value);
            T decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally { buffer.release(); }
    }

    private static void assertRejected(StreamCodec<RegistryFriendlyByteBuf, ?> codec, java.util.function.Consumer<RegistryFriendlyByteBuf> writer) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            writer.accept(buffer);
            assertThrows(RuntimeException.class, () -> codec.decode(buffer));
        } finally { buffer.release(); }
    }

    private static TerminalDevicesPayload.Device device(BlockPos position) {
        return new TerminalDevicesPayload.Device(position, MachineKind.ELECTROLYZER, "working", RECIPE, 30, 40, 2, true);
    }

    @Test void clientRequestsRoundTripAndOnlyQueuesNameARecipe() {
        var queue = new TerminalDeviceActionPayload(4, POSITION, TerminalDeviceActionPayload.Action.QUEUE_BATCH, RECIPE);
        assertEquals(queue, roundTrip(TerminalDeviceActionPayload.CODEC, queue));
        var open = new TerminalDeviceActionPayload(4, POSITION, TerminalDeviceActionPayload.Action.OPEN, "");
        assertEquals(open, roundTrip(TerminalDeviceActionPayload.CODEC, open));
        assertThrows(IllegalArgumentException.class,
                () -> new TerminalDeviceActionPayload(4, POSITION, TerminalDeviceActionPayload.Action.QUEUE_BATCH, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new TerminalDeviceActionPayload(4, POSITION, TerminalDeviceActionPayload.Action.OPEN, RECIPE));
        assertThrows(IllegalArgumentException.class, () -> new TerminalDeviceActionPayload(4, POSITION,
                TerminalDeviceActionPayload.Action.QUEUE_BATCH, "rematter:" + "x".repeat(MachineSnapshot.MAX_CHOICE_ID_LENGTH)));
    }

    @Test void oversizedOrUnknownClientRequestsAreRejectedWhileDecoding() {
        assertRejected(TerminalDeviceActionPayload.CODEC, buffer -> {
            buffer.writeVarInt(4); buffer.writeBlockPos(POSITION); buffer.writeVarInt(TerminalDeviceActionPayload.Action.values().length);
            buffer.writeUtf("");
        });
        assertRejected(TerminalDeviceActionPayload.CODEC, buffer -> {
            buffer.writeVarInt(4); buffer.writeBlockPos(POSITION); buffer.writeEnum(TerminalDeviceActionPayload.Action.QUEUE_BATCH);
            buffer.writeUtf("a".repeat(MachineSnapshot.MAX_CHOICE_ID_LENGTH + 1));
        });
    }

    @Test void deviceChangesRoundTripWithTheirTotalAndRemovals() {
        var change = new TerminalDevicesPayload(9, false, 700, List.of(device(POSITION)), List.of(POSITION.above()));
        var decoded = roundTrip(TerminalDevicesPayload.CODEC, change);
        assertEquals(change, decoded);
        assertEquals(700, decoded.total());
        var listing = new TerminalDevicesPayload.Listing(9, 700, decoded.changed());
        assertTrue(listing.truncated());
        assertFalse(new TerminalDevicesPayload.Listing(9, 1, decoded.changed()).truncated());
    }

    @Test void deviceListsAndRowsStayWithinTheirBounds() {
        var row = device(POSITION);
        assertThrows(IllegalArgumentException.class, () -> new TerminalDevicesPayload(9, true, 0,
                Collections.nCopies(TerminalDevicesPayload.MAX_DEVICES + 1, row), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TerminalDevicesPayload(9, true, -1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TerminalDevicesPayload.Device(POSITION, MachineKind.REACTOR,
                "working", "", 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new TerminalDevicesPayload.Device(POSITION, MachineKind.REACTOR,
                "", RECIPE, 0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new TerminalDevicesPayload.Device(POSITION, MachineKind.REACTOR,
                "working", RECIPE, 0, 0, MachineRunState.MAX_QUEUED_BATCHES + 1, false));
        assertRejected(TerminalDevicesPayload.CODEC, buffer -> {
            buffer.writeVarInt(9); buffer.writeBoolean(true); buffer.writeVarInt(0);
            buffer.writeVarInt(TerminalDevicesPayload.MAX_DEVICES + 1);
        });
    }
}
