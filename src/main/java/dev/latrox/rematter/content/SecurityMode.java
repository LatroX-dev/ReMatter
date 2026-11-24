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

/**
 * Mekanism-style device access control. Defaults to {@link #PUBLIC} so coop players can share devices immediately;
 * only the owner (or a server operator) may change the mode.
 */
public enum SecurityMode implements StringRepresentable {
    PUBLIC,
    TRUSTED,
    PRIVATE;

    private static final SecurityMode[] VALUES = values();

    public static final Codec<SecurityMode> CODEC = StringRepresentable.fromEnum(SecurityMode::values);
    public static final StreamCodec<ByteBuf, SecurityMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(SecurityMode::byIndex, SecurityMode::ordinal);
    public static final Supplier<DataComponentType<SecurityMode>> TYPE = () -> Registration.TYPE.get();

    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final Supplier<DataComponentType<SecurityMode>> TYPE =
                COMPONENTS.registerComponentType("security_card_mode",
                        builder -> builder.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    }

    public static void register(IEventBus bus) {
        Registration.COMPONENTS.register(bus);
    }

    public static SecurityMode byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : PUBLIC;
    }

    public static SecurityMode load(String raw) {
        for (var value : VALUES) {
            if (value.getSerializedName().equals(raw)) return value;
        }
        throw new IllegalArgumentException("Unknown security mode: " + raw);
    }

    public static boolean validName(String raw) {
        for (var value : VALUES) {
            if (value.getSerializedName().equals(raw)) return true;
        }
        return false;
    }

    public SecurityMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public SecurityMode previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public ChatFormatting color() {
        return switch (this) {
            case PUBLIC -> ChatFormatting.GREEN;
            case TRUSTED -> ChatFormatting.AQUA;
            case PRIVATE -> ChatFormatting.RED;
        };
    }

    public Component label() {
        return Component.translatable("screen.rematter.security." + getSerializedName());
    }

    public Component coloredLabel() {
        return label().copy().withStyle(color());
    }

    public Component meaning() {
        return Component.translatable("screen.rematter.security_hint." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
