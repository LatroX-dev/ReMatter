package dev.latrox.rematter.content;

import java.util.Optional;
import java.util.Set;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** A storage's persisted owner mode; unrecognized data is retained verbatim and never made usable. */
final class MatterStorageState {
    static final Codec<Tag> RAW_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue(), tag -> new Dynamic<>(NbtOps.INSTANCE, tag));
    static final Codec<MatterStorageState> CODEC = RAW_CODEC.xmap(MatterStorageState::restore, MatterStorageState::save);
    enum Mode { CELL, LEGACY_INTERNAL }
    private final Tag raw;

    private MatterStorageState(Tag raw) { this.raw = raw.copy(); }
    static MatterStorageState cell() { return create(Mode.CELL); }
    static MatterStorageState legacy() { return create(Mode.LEGACY_INTERNAL); }
    static MatterStorageState restore(Tag raw) { return new MatterStorageState(raw); }
    private static MatterStorageState create(Mode mode) {
        var tag = new CompoundTag(); tag.putInt("schema", 1);
        tag.putString("mode", mode == Mode.CELL ? "cell" : "legacy_internal");
        return new MatterStorageState(tag);
    }
    Optional<Mode> mode() {
        if (!(raw instanceof CompoundTag compound) || !compound.keySet().equals(Set.of("schema", "mode"))
                || !(compound.get("schema") instanceof IntTag) || compound.getIntOr("schema", -1) != 1) return Optional.empty();
        return switch (compound.getStringOr("mode", "")) {
            case "cell" -> Optional.of(Mode.CELL);
            case "legacy_internal" -> Optional.of(Mode.LEGACY_INTERNAL);
            default -> Optional.empty();
        };
    }
    Tag save() { return raw.copy(); }
}
