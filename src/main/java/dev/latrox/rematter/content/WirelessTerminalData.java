package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components for Wireless Matter Terminal binding and native FE battery storage. */
public final class WirelessTerminalData {
    public static final int SCHEMA = 1;
    public static final Codec<WirelessTerminalData> CODEC = CompoundTag.CODEC.xmap(WirelessTerminalData::new, WirelessTerminalData::copyTag);
    public static final StreamCodec<ByteBuf, WirelessTerminalData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(WirelessTerminalData::new, WirelessTerminalData::copyTag);
    public static final Supplier<DataComponentType<WirelessTerminalData>> LINK = () -> Registration.LINK.get();
    public static final Supplier<DataComponentType<Integer>> ENERGY = () -> Registration.ENERGY.get();

    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final Supplier<DataComponentType<WirelessTerminalData>> LINK =
                COMPONENTS.registerComponentType("wireless_link",
                        builder -> builder.persistent(CODEC).networkSynchronized(STREAM_CODEC));
        private static final Supplier<DataComponentType<Integer>> ENERGY =
                COMPONENTS.registerComponentType("wireless_energy",
                        builder -> builder.persistent(Codec.intRange(0, WirelessMatterTerminalItem.CAPACITY))
                                .networkSynchronized(ByteBufCodecs.VAR_INT));
    }

    public static void register(IEventBus bus) { Registration.COMPONENTS.register(bus); }

    public record Target(ResourceKey<Level> dimension, BlockPos pos) {
        public Target {
            Objects.requireNonNull(dimension);
            pos = pos.immutable();
        }
    }

    private final CompoundTag raw;

    public WirelessTerminalData(CompoundTag raw) { this.raw = Objects.requireNonNull(raw).copy(); }
    public CompoundTag copyTag() { return raw.copy(); }

    public static WirelessTerminalData of(ResourceKey<Level> dimension, BlockPos pos) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("dimension", dimension.identifier().toString());
        tag.putLong("pos", pos.asLong());
        return new WirelessTerminalData(tag);
    }

    public Optional<Target> target() {
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA) return Optional.empty();
        try {
            var dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(raw.getString("dimension").orElseThrow()));
            return Optional.of(new Target(dim, BlockPos.of(raw.getLong("pos").orElseThrow())));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    @Override public boolean equals(Object other) { return other instanceof WirelessTerminalData data && raw.equals(data.raw); }
    @Override public int hashCode() { return raw.hashCode(); }
}
