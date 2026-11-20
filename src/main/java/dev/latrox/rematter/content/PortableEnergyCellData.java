package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components for Portable Energy Cell FE storage and inventory auto-charge state. */
public final class PortableEnergyCellData {
    public static final Supplier<DataComponentType<Integer>> ENERGY = () -> Registration.ENERGY.get();
    public static final Supplier<DataComponentType<Boolean>> AUTO_CHARGE = () -> Registration.AUTO_CHARGE.get();

    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final Supplier<DataComponentType<Integer>> ENERGY =
                COMPONENTS.registerComponentType("portable_energy",
                        builder -> builder.persistent(Codec.intRange(0, PortableEnergyCellItem.CAPACITY))
                                .networkSynchronized(ByteBufCodecs.VAR_INT));
        private static final Supplier<DataComponentType<Boolean>> AUTO_CHARGE =
                COMPONENTS.registerComponentType("portable_auto_charge",
                        builder -> builder.persistent(Codec.BOOL)
                                .networkSynchronized(ByteBufCodecs.BOOL));
    }

    private PortableEnergyCellData() {}

    public static void register(IEventBus bus) {
        Registration.COMPONENTS.register(bus);
    }
}
