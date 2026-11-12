package dev.latrox.rematter.research;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Physical readback after a flush. Never creates a region, uses a live chunk cache or falls back to older player data. */
public final class IdentityCheckpointReader {
    private static final int SECTOR = 4096, MAX_EXTERNAL = 16 * 1024 * 1024;
    private IdentityCheckpointReader() {}
    public static Optional<CompoundTag> blockEntity(ServerLevel level, BlockPos position) throws IOException {
        var chunk = readChunk(level, position);
        if (chunk.isEmpty()) return Optional.empty();
        var entities = chunk.get().getList("block_entities"); if (entities.isEmpty()) return Optional.empty();
        for (int i = 0; i < entities.get().size(); i++) {
            var entity = entities.get().getCompound(i);
            if (entity.isPresent() && entity.get().getIntOr("x", Integer.MIN_VALUE) == position.getX()
                    && entity.get().getIntOr("y", Integer.MIN_VALUE) == position.getY() && entity.get().getIntOr("z", Integer.MIN_VALUE) == position.getZ()) return entity;
        }
        return Optional.empty();
    }
    public static Optional<CompoundTag> readChunk(ServerLevel level, BlockPos position) throws IOException {
        var world = level.getServer().getWorldPath(LevelResource.ROOT);
        var region = DimensionType.getStorageFolder(level.dimension(), world).resolve("region");
        return chunk(region, position.getX() >> 4, position.getZ() >> 4);
    }
    public static Optional<CompoundTag> readPlayer(MinecraftServer server, UUID owner) throws IOException {
        var path = server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(owner + ".dat");
        if (!Files.isRegularFile(path)) return Optional.empty();
        return Optional.of(NbtIo.readCompressed(path, NbtAccounter.create(64L * 1024 * 1024)));
    }
    public static Optional<CompoundTag> chunk(Path regionDirectory, int chunkX, int chunkZ) throws IOException {
        var path = regionDirectory.resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
        if (!Files.isRegularFile(path)) return Optional.empty();
        try (var file = FileChannel.open(path, StandardOpenOption.READ)) {
            if (file.size() < 8192) throw new EOFException("Truncated region header");
            var offset = ByteBuffer.allocate(4); readFully(file, offset, 4L * ((chunkX & 31) + (chunkZ & 31) * 32));
            int packed = offset.getInt(); if (packed == 0) return Optional.empty();
            int sector = packed >>> 8, sectors = packed & 255;
            // Native RegionFile pads its final allocated sector on close, not after every live write.
            // A complete declared payload is valid even while the final sector's unused padding is absent.
            long sectorStart = Math.multiplyExact((long)sector, SECTOR);
            if (sector < 2 || sectors == 0 || sectorStart + 5 > file.size()) throw new IOException("Invalid region allocation");
            var header = ByteBuffer.allocate(5); readFully(file, header, (long)sector * SECTOR);
            int length = header.getInt(), versionId = Byte.toUnsignedInt(header.get());
            var version = RegionFileVersion.fromId(versionId & 127);
            if (length < 1 || length > sectors * SECTOR - 4 || sectorStart + 4L + length > file.size()
                    || version == null || version == RegionFileVersion.VERSION_CUSTOM) throw new IOException("Invalid, truncated or unsupported chunk stream");
            if ((versionId & 128) != 0) {
                if (length != 1) throw new IOException("Ambiguous external chunk stream");
                var external = regionDirectory.resolve("c." + chunkX + "." + chunkZ + ".mcc");
                if (!Files.isRegularFile(external) || Files.size(external) > MAX_EXTERNAL) throw new IOException("Missing or oversized external chunk stream");
                try (var raw = Files.newInputStream(external)) { return Optional.of(readNbt(version, raw)); }
            }
            var bytes = ByteBuffer.allocate(length - 1); readFully(file, bytes, (long)sector * SECTOR + 5);
            return Optional.of(readNbt(version, new ByteArrayInputStream(bytes.array())));
        }
    }
    private static CompoundTag readNbt(RegionFileVersion version, InputStream raw) throws IOException {
        try (var input = new DataInputStream(version.wrap(raw))) { return NbtIo.read(input, NbtAccounter.create(64L * 1024 * 1024)); }
    }
    private static void readFully(FileChannel file, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) { int read = file.read(buffer, position); if (read < 0) throw new EOFException("Truncated region stream"); position += read; }
        buffer.flip();
    }
}
