package dev.latrox.rematter.content;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomNamesAndChunkAnchorTest {

    @Test
    void customNamesSanitizeClampsAndStripsControlAndFormattingChars() {
        assertEquals("", CustomNames.sanitize(null));
        assertEquals("", CustomNames.sanitize("   \t\n  "));
        assertEquals("Nether Pump", CustomNames.sanitize("  §bNether\u0007 Pump  "));
        String longInput = "A".repeat(80);
        String sanitized = CustomNames.sanitize(longInput);
        assertEquals(CustomNames.MAX_LENGTH, sanitized.length());
        assertEquals("A".repeat(CustomNames.MAX_LENGTH), sanitized);
    }

    @Test
    void shortDimensionLabelsFormatVanillaAndModdedDimensions() {
        assertEquals("", CustomNames.shortDimensionLabel(""));
        assertEquals("", CustomNames.shortDimensionLabel("minecraft:overworld"));
        assertEquals("Nether", CustomNames.shortDimensionLabel("minecraft:the_nether"));
        assertEquals("End", CustomNames.shortDimensionLabel("minecraft:the_end"));
        assertEquals("Twilight forest", CustomNames.shortDimensionLabel("twilightforest:twilight_forest"));
    }

    @Test
    void entangledLinkBaseRangeIs64Blocks() {
        BlockPos origin = new BlockPos(10, 64, -5);
        assertTrue(dev.latrox.rematter.primal.EntangledLinks.withinRange(origin, origin.offset(64, 0, 0)));
        assertFalse(dev.latrox.rematter.primal.EntangledLinks.withinRange(origin, origin.offset(65, 0, 0)));
        assertFalse(dev.latrox.rematter.primal.EntangledLinks.withinRange(origin, origin));
    }

    @Test
    void chunkLoadingConfigDefaultsAreSafeBeforeWorldLoad() {
        assertTrue(ChunkLoadingConfig.enabled());
        assertEquals(32, ChunkLoadingConfig.maxChunksPerPlayer());
        assertFalse(ChunkLoadingConfig.onlyWhileOwnerOnline());
    }
}
