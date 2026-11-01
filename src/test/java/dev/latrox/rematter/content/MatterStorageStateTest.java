package dev.latrox.rematter.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterStorageStateTest {
    @Test void explicitOwnerModeSurvivesReloadWithoutInferringItFromCellContents() {
        for (var state : new MatterStorageState[]{MatterStorageState.cell(), MatterStorageState.legacy()}) {
            var restored = MatterStorageState.restore(state.save());
            assertEquals(state.mode(), restored.mode());
            assertEquals(state.save(), restored.save());
        }
        assertNotEquals(MatterStorageState.cell().mode(), MatterStorageState.legacy().mode());
    }
    @Test void futureModesAndSchemasStayLockedAndRawWithoutAliasingCallerTags() {
        var raw = new CompoundTag(); raw.putInt("schema", 42); raw.putString("mode", "future");
        raw.putLong("resources", Long.MAX_VALUE);
        var state = MatterStorageState.restore(raw); var original = raw.copy(); raw.putLong("resources", 0);
        assertTrue(state.mode().isEmpty()); assertEquals(original, state.save());
        ((CompoundTag) state.save()).putInt("schema", 1);
        assertEquals(original, MatterStorageState.restore(state.save()).save());
        var unknownMode = (CompoundTag) MatterStorageState.cell().save(); unknownMode.putString("mode", "unknown");
        assertTrue(MatterStorageState.restore(unknownMode).mode().isEmpty());
    }
    @Test void extraFieldsOrNonintegralSchemaNeverActivateLegacyCapacity() {
        var raw = (CompoundTag) MatterStorageState.legacy().save(); raw.putString("extension", "preserve");
        assertTrue(MatterStorageState.restore(raw).mode().isEmpty());
        var fractional = (CompoundTag) MatterStorageState.legacy().save(); fractional.putDouble("schema", 1.5);
        assertTrue(MatterStorageState.restore(fractional).mode().isEmpty());
    }
    @Test void presentNoncompoundStateDecodesAsOpaqueInsteadOfMissingLegacy() {
        var raw = StringTag.valueOf("future storage state");
        var decoded = MatterStorageState.CODEC.parse(NbtOps.INSTANCE, raw).getOrThrow();
        assertTrue(decoded.mode().isEmpty());
        assertEquals(raw, MatterStorageState.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow());
    }
}
