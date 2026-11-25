package dev.latrox.rematter.content;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Lightweight top-down 5x5 chunk coverage map captured strictly from already-loaded chunks (never forces chunk loads).
 * Samples 4x4 surface map colors per chunk (20x20 = 400 colors) plus per-chunk coverage flags.
 */
public record ChunkMapSnapshot(
        int centerChunkX,
        int centerChunkZ,
        String dimensionId,
        boolean playerSameDim,
        int playerChunkX,
        int playerChunkZ,
        byte[] chunkFlags,
        int[] colors) {

    public static final int GRID = 5;
    public static final int HALF = GRID / 2;
    public static final int CHUNK_COUNT = GRID * GRID;
    public static final int SUB = 4;
    public static final int COLOR_COUNT = CHUNK_COUNT * SUB * SUB;

    public static final byte FLAG_LOADED = 1;
    public static final byte FLAG_COVERED_THIS = 2;
    public static final byte FLAG_COVERED_OTHER = 4;

    public ChunkMapSnapshot {
        if (chunkFlags.length != CHUNK_COUNT) throw new IllegalArgumentException("Expected " + CHUNK_COUNT + " chunk flags");
        if (colors.length != COLOR_COUNT) throw new IllegalArgumentException("Expected " + COLOR_COUNT + " map colors");
        chunkFlags = chunkFlags.clone();
        colors = colors.clone();
        dimensionId = dimensionId == null ? "minecraft:overworld" : dimensionId;
    }

    public byte flagAt(int gx, int gz) {
        return chunkFlags[gz * GRID + gx];
    }

    public int colorAt(int gx, int gz, int sx, int sz) {
        return colors[((gz * GRID + gx) * SUB + sz) * SUB + sx];
    }

    public static ChunkMapSnapshot capture(ServerLevel level, BlockPos origin, @Nullable UUID owner,
                                           Set<ChunkPos> coveredByThis, @Nullable Player viewer) {
        ChunkPos center = ChunkPos.containing(origin);
        Set<ChunkPos> others = ChunkAnchorManager.ownerChunksInDimension(level.getServer(), owner, level.dimension(), origin);
        byte[] flags = new byte[CHUNK_COUNT];
        int[] colors = new int[COLOR_COUNT];
        var mutable = new BlockPos.MutableBlockPos();

        for (int gz = 0; gz < GRID; gz++) {
            int cz = center.z() + (gz - HALF);
            for (int gx = 0; gx < GRID; gx++) {
                int cx = center.x() + (gx - HALF);
                ChunkPos cp = new ChunkPos(cx, cz);
                byte flag = 0;
                if (coveredByThis.contains(cp)) flag |= FLAG_COVERED_THIS;
                if (others.contains(cp)) flag |= FLAG_COVERED_OTHER;

                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                int baseIdx = (gz * GRID + gx) * SUB * SUB;
                if (chunk != null) {
                    flag |= FLAG_LOADED;
                    int minY = level.getMinY();
                    for (int sz = 0; sz < SUB; sz++) {
                        int lz = sz * 4 + 1;
                        int wz = (cz << 4) + lz;
                        for (int sx = 0; sx < SUB; sx++) {
                            int lx = sx * 4 + 1;
                            int wx = (cx << 4) + lx;
                            int topY = Math.max(minY, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) - 1);
                            mutable.set(wx, topY, wz);
                            MapColor mapColor = chunk.getBlockState(mutable).getMapColor(level, mutable);
                            int rgb = mapColor == MapColor.NONE ? 0x262B33 : mapColor.col;
                            // Subtle elevation shading for crisp readable terrain relief.
                            int shadeIdx = topY > 85 ? 2 : topY < 58 ? 0 : 1;
                            int mul = shadeIdx == 2 ? 245 : shadeIdx == 0 ? 185 : 218;
                            int r = (((rgb >> 16) & 0xFF) * mul) / 255;
                            int g = (((rgb >> 8) & 0xFF) * mul) / 255;
                            int b = ((rgb & 0xFF) * mul) / 255;
                            colors[baseIdx + sz * SUB + sx] = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                    }
                } else {
                    for (int i = 0; i < SUB * SUB; i++) colors[baseIdx + i] = 0;
                }
                flags[gz * GRID + gx] = flag;
            }
        }

        boolean sameDim = viewer != null && viewer.level() == level;
        ChunkPos playerChunk = sameDim ? ChunkPos.containing(viewer.blockPosition()) : center;
        return new ChunkMapSnapshot(
                center.x(), center.z(),
                level.dimension().identifier().toString(),
                sameDim, playerChunk.x(), playerChunk.z(),
                flags, colors);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(centerChunkX);
        buf.writeVarInt(centerChunkZ);
        buf.writeUtf(dimensionId, 128);
        buf.writeBoolean(playerSameDim);
        buf.writeVarInt(playerChunkX);
        buf.writeVarInt(playerChunkZ);
        buf.writeBytes(chunkFlags);
        for (int c : colors) buf.writeInt(c);
    }

    public static ChunkMapSnapshot read(RegistryFriendlyByteBuf buf) {
        int cx = buf.readVarInt();
        int cz = buf.readVarInt();
        String dim = buf.readUtf(128);
        boolean sameDim = buf.readBoolean();
        int px = buf.readVarInt();
        int pz = buf.readVarInt();
        byte[] flags = new byte[CHUNK_COUNT];
        buf.readBytes(flags);
        int[] colors = new int[COLOR_COUNT];
        for (int i = 0; i < COLOR_COUNT; i++) colors[i] = buf.readInt();
        return new ChunkMapSnapshot(cx, cz, dim, sameDim, px, pz, flags, colors);
    }
}
