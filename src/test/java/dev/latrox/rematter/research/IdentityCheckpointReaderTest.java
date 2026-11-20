package dev.latrox.rematter.research;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IdentityCheckpointReaderTest {
    @TempDir Path directory;
    @Test void missingCheckpointReadDoesNotCreateARegion() throws Exception {
        assertTrue(IdentityCheckpointReader.chunk(directory, 0, 0).isEmpty());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
    @Test void physicalHeaderAndCurrentCompressedNbtAreReadAtNegativeCoordinates() throws Exception {
        var tag = new CompoundTag(); var marker = UUID.randomUUID().toString(); tag.putString("checkpoint", marker);
        byte[] data = compressed(tag); var region = ByteBuffer.allocate(3 * 4096);
        region.putInt(4 * (31 + 31 * 32), (2 << 8) | 1); region.position(8192); region.putInt(data.length + 1); region.put((byte)2); region.put(data);
        var file = directory.resolve("r.-1.-1.mca"); Files.write(file, region.array());
        assertEquals(marker, IdentityCheckpointReader.chunk(directory, -1, -1).orElseThrow().getString("checkpoint").orElseThrow());
        byte[] before = Files.readAllBytes(file); IdentityCheckpointReader.chunk(directory, -1, -1); assertArrayEquals(before, Files.readAllBytes(file));
        assertTrue(IdentityCheckpointReader.chunk(directory, -2, -1).isEmpty());
    }
    @Test void completeUnpaddedLiveFinalSectorIsValidButAnActuallyTruncatedPayloadIsRejected() throws Exception {
        var tag = new CompoundTag(); tag.putString("checkpoint", "live-unpadded"); byte[] data = compressed(tag);
        var region = ByteBuffer.allocate(8192 + 5 + data.length);
        region.putInt(0, (2 << 8) | 1); region.position(8192); region.putInt(data.length + 1); region.put((byte)2); region.put(data);
        var path = directory.resolve("r.0.0.mca"); Files.write(path, region.array());
        assertEquals("live-unpadded", IdentityCheckpointReader.chunk(directory, 0, 0).orElseThrow().getString("checkpoint").orElseThrow());
        Files.write(path, java.util.Arrays.copyOf(region.array(), region.capacity() - 1));
        assertThrows(java.io.IOException.class, () -> IdentityCheckpointReader.chunk(directory, 0, 0));
    }
    @Test void truncatedOrUnrecognizedPhysicalCheckpointFailsClosed() throws Exception {
        var path = directory.resolve("r.0.0.mca"); Files.write(path, new byte[12]);
        assertThrows(java.io.IOException.class, () -> IdentityCheckpointReader.chunk(directory, 0, 0));
        var region = ByteBuffer.allocate(3 * 4096); region.putInt(0, (2 << 8) | 1); region.position(8192); region.putInt(1); region.put((byte)99); Files.write(path, region.array());
        assertThrows(java.io.IOException.class, () -> IdentityCheckpointReader.chunk(directory, 0, 0));
    }
    private byte[] compressed(CompoundTag tag) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(RegionFileVersion.VERSION_DEFLATE.wrap(bytes))) { NbtIo.write(tag, output); }
        return bytes.toByteArray();
    }
}
