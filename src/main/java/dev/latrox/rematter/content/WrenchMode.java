package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Active operating mode of a Matter Wrench, switched with Sneak + Right-Click. */
public enum WrenchMode implements StringRepresentable {
    PORTS, ROTATE, DISMANTLE;

    public static final Codec<WrenchMode> CODEC = StringRepresentable.fromEnum(WrenchMode::values);
    public static final StreamCodec<ByteBuf, WrenchMode> STREAM_CODEC = ByteBufCodecs.idMapper(
            index -> values()[Math.clamp(index, 0, values().length - 1)], WrenchMode::ordinal);
    public static final Supplier<DataComponentType<WrenchMode>> TYPE = () -> Registration.TYPE.get();

    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final Supplier<DataComponentType<WrenchMode>> TYPE =
                COMPONENTS.registerComponentType("wrench_mode",
                        builder -> builder.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    }

    public static void register(IEventBus bus) { Registration.COMPONENTS.register(bus); }

    @Override
    public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

    public WrenchMode next() { return values()[(ordinal() + 1) % values().length]; }

    public ChatFormatting color() {
        return switch (this) {
            case PORTS -> ChatFormatting.AQUA;
            case ROTATE -> ChatFormatting.GOLD;
            case DISMANTLE -> ChatFormatting.RED;
        };
    }

    public Component label() {
        return Component.translatable("item.rematter.matter_wrench.mode." + getSerializedName());
    }

    public Component coloredLabel() {
        return label().copy().withStyle(color());
    }

    public Component hint() {
        return Component.translatable("item.rematter.matter_wrench.mode_hint." + getSerializedName());
    }
}
