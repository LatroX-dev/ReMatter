package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Versioned contents for {@link PressurizedCanisterItem}.
 * Stores either a single non-reserved liquid/gas substance or a single plain fluid up to 16,000 units/mB,
 * preserving unknown schemas and unknown substance/fluid IDs untouched.
 */
public final class PressurizedCanisterData {
    public static final int SCHEMA = 1;
    public static final int CAPACITY = 16_000;
    private static final Identifier EMPTY_FLUID_ID = Identifier.withDefaultNamespace("empty");

    public static final Codec<PressurizedCanisterData> CODEC =
            CompoundTag.CODEC.xmap(PressurizedCanisterData::new, PressurizedCanisterData::copyTag);
    public static final StreamCodec<ByteBuf, PressurizedCanisterData> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(PressurizedCanisterData::new, PressurizedCanisterData::copyTag);
    public static final Supplier<DataComponentType<PressurizedCanisterData>> TYPE =
            () -> Registration.TYPE.get();

    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final Supplier<DataComponentType<PressurizedCanisterData>> TYPE =
                COMPONENTS.registerComponentType("pressurized_canister",
                        builder -> builder.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    }

    private final CompoundTag raw;

    public PressurizedCanisterData(CompoundTag raw) {
        this.raw = Objects.requireNonNull(raw).copy();
    }

    public CompoundTag copyTag() {
        return raw.copy();
    }

    public static void register(IEventBus bus) {
        Registration.COMPONENTS.register(bus);
    }

    public static boolean validMatter(SubstanceKey key) {
        return key != null
                && (key.phase() == Phase.LIQUID || key.phase() == Phase.GAS)
                && !ReservedSubstances.reserved(key);
    }

    public sealed interface State permits State.Empty, State.Matter, State.Fluid, State.Locked {
        record Empty() implements State {}
        record Matter(SubstanceKey key, int amount) implements State {
            public Matter {
                if (!validMatter(key) || amount <= 0 || amount > CAPACITY) {
                    throw new IllegalArgumentException("Invalid canister matter state");
                }
            }
        }
        record Fluid(Identifier fluidId, int amount) implements State {
            public Fluid {
                Objects.requireNonNull(fluidId);
                if (fluidId.equals(EMPTY_FLUID_ID) || amount <= 0 || amount > CAPACITY) {
                    throw new IllegalArgumentException("Invalid canister fluid state");
                }
            }
        }
        record Locked() implements State {}
    }

    public static State read(ItemStack stack) {
        var data = stack.get(TYPE.get());
        return data == null ? new State.Empty() : data.decode();
    }

    public static State read(ItemResource resource) {
        var data = resource.get(TYPE.get());
        return data == null ? new State.Empty() : data.decode();
    }

    public State decode() {
        try {
            if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA) {
                return new State.Locked();
            }
            if (!(raw.get("amount") instanceof IntTag)) return new State.Locked();
            int amount = raw.getIntOr("amount", -1);
            if (amount <= 0 || amount > CAPACITY) return new State.Locked();
            String mode = raw.getString("mode").orElse("");
            return switch (mode) {
                case "matter" -> {
                    if (!raw.keySet().equals(Set.of("schema", "mode", "substance", "phase", "amount"))) {
                        yield new State.Locked();
                    }
                    SubstanceId id = SubstanceId.parse(raw.getString("substance").orElseThrow());
                    Phase phase = Phase.valueOf(raw.getString("phase").orElseThrow());
                    SubstanceKey key = new SubstanceKey(id, phase);
                    if (!validMatter(key)) yield new State.Locked();
                    yield new State.Matter(key, amount);
                }
                case "fluid" -> {
                    if (!raw.keySet().equals(Set.of("schema", "mode", "fluid", "amount"))) {
                        yield new State.Locked();
                    }
                    Identifier fluidId = Identifier.parse(raw.getString("fluid").orElseThrow());
                    if (fluidId.equals(EMPTY_FLUID_ID)) yield new State.Locked();
                    yield new State.Fluid(fluidId, amount);
                }
                default -> new State.Locked();
            };
        } catch (RuntimeException invalid) {
            return new State.Locked();
        }
    }

    public static PressurizedCanisterData ofMatter(SubstanceKey key, int amount) {
        var state = new State.Matter(key, amount);
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("mode", "matter");
        tag.putString("substance", state.key().id().toString());
        tag.putString("phase", state.key().phase().name());
        tag.putInt("amount", state.amount());
        return new PressurizedCanisterData(tag);
    }

    public static PressurizedCanisterData ofFluid(Identifier fluidId, int amount) {
        var state = new State.Fluid(fluidId, amount);
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("mode", "fluid");
        tag.putString("fluid", state.fluidId().toString());
        tag.putInt("amount", state.amount());
        return new PressurizedCanisterData(tag);
    }

    public static void writeEmpty(ItemStack stack) {
        stack.remove(TYPE.get());
    }

    public static void writeMatter(ItemStack stack, SubstanceKey key, int amount) {
        if (amount <= 0) stack.remove(TYPE.get());
        else stack.set(TYPE.get(), ofMatter(key, amount));
    }

    public static void writeFluid(ItemStack stack, Identifier fluidId, int amount) {
        if (amount <= 0) stack.remove(TYPE.get());
        else stack.set(TYPE.get(), ofFluid(fluidId, amount));
    }

    public static ItemResource writeEmpty(ItemResource resource) {
        return resource.without(TYPE.get());
    }

    public static ItemResource writeMatter(ItemResource resource, SubstanceKey key, int amount) {
        return amount <= 0 ? resource.without(TYPE.get()) : resource.with(TYPE.get(), ofMatter(key, amount));
    }

    public static ItemResource writeFluid(ItemResource resource, Identifier fluidId, int amount) {
        return amount <= 0 ? resource.without(TYPE.get()) : resource.with(TYPE.get(), ofFluid(fluidId, amount));
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof PressurizedCanisterData other && raw.equals(other.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }
}
