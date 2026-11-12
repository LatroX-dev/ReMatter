package dev.latrox.rematter.primal;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class PrimalCellDataTest {
    @Test void actualCodecsKeepIdentityBalanceAndReceiptsExactly() {
        var original = new PrimalCellData.State(UUID.randomUUID(), 64000, 2, Map.of(UUID.randomUUID(), -4000L, UUID.randomUUID(), 4000L));
        var data = PrimalCellData.from(original);
        assertEquals(original, data.open(64000).orElseThrow());
        assertTrue(data.open(16000).isEmpty());
        var decoded = PrimalCellData.CODEC.parse(NbtOps.INSTANCE, PrimalCellData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertEquals(data, decoded);
        var buffer = Unpooled.buffer();
        try { PrimalCellData.STREAM_CODEC.encode(buffer, data); assertEquals(data, PrimalCellData.STREAM_CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes()); }
        finally { buffer.release(); }
    }
    @Test void unknownVersionMalformedResourceAndExtraFieldsRemainRawAndLocked() {
        var raw = new CompoundTag(); raw.putInt("schema", 42); raw.putString("future", "never erase"); raw.putLong("amount", Long.MAX_VALUE);
        var data = new PrimalCellData(raw); raw.putInt("schema", 1);
        assertTrue(data.open(64000).isEmpty()); assertEquals(42, data.copyTag().getIntOr("schema", 0));
        assertEquals(data, PrimalCellData.CODEC.parse(NbtOps.INSTANCE, PrimalCellData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow());
        var valid = PrimalCellData.from(new PrimalCellData.State(UUID.randomUUID(), 500, 0, Map.of())).copyTag();
        valid.putString("resource", "rematter:copper"); assertTrue(new PrimalCellData(valid).open(64000).isEmpty());
        valid.putString("resource", "rematter:primal_matter"); valid.putLong("capacity", Long.MAX_VALUE);
        assertTrue(new PrimalCellData(valid).open(64000).isEmpty());
    }
    @Test void paymentsCannotUnderflowRepeatOrOverflow() {
        UUID operation = UUID.randomUUID();
        var initial = new PrimalCellData.State(UUID.randomUUID(), 4000, 0, Map.of());
        var paid = initial.withReceipt(operation, -4000);
        assertEquals(0, paid.amount()); assertEquals(-4000, paid.receipts().get(operation)); assertEquals(4000, initial.amount());
        assertThrows(IllegalArgumentException.class, () -> paid.withReceipt(operation, -4000));
        assertThrows(IllegalArgumentException.class, () -> paid.withReceipt(UUID.randomUUID(), -1));
        var maximum = new PrimalCellData.State(UUID.randomUUID(), Long.MAX_VALUE, 0, Map.of());
        assertThrows(ArithmeticException.class, () -> maximum.withReceipt(UUID.randomUUID(), 1));
    }
    @Test void compactWatermarksSupportLongHistoriesWithoutCopyingPastReceiptsIntoTheItem() {
        var settled = new PrimalCellData.State(UUID.randomUUID(), 16000, 100_000, Map.of());
        var data = PrimalCellData.from(settled);
        var buffer = Unpooled.buffer();
        try {
            PrimalCellData.STREAM_CODEC.encode(buffer, data);
            assertTrue(buffer.readableBytes() < 256, "Historical payment count must not grow the item component");
            assertEquals(settled, PrimalCellData.STREAM_CODEC.decode(buffer).open(16000).orElseThrow());
        } finally { buffer.release(); }
        var pending = settled.withReceipt(UUID.randomUUID(), -4000);
        assertEquals(100_001, pending.authorityRevision()); assertEquals(1, pending.receipts().size());
        var old = PrimalCellData.from(settled).copyTag(); old.putInt("schema", 1);
        var locked = new PrimalCellData(old); assertTrue(locked.open(64000).isEmpty()); assertEquals(old, locked.copyTag());
        var receipts = new java.util.HashMap<UUID, Long>();
        for (int i = 0; i < 9; i++) receipts.put(UUID.randomUUID(), 1L);
        assertThrows(IllegalArgumentException.class, () -> new PrimalCellData.State(UUID.randomUUID(), 1000, 1000, receipts));
    }
}
