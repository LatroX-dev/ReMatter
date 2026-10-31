package dev.latrox.rematter.compat.jade;

import dev.latrox.rematter.content.MachineBlockEntity;
import java.util.List;
import net.minecraft.resources.Identifier;
import snownee.jade.api.Accessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/** Jade discovers this entry point only when Jade is installed. */
@WailaPlugin
public final class ReMatterJadePlugin implements IWailaPlugin {
    static final Identifier STATUS = id("machine_status");
    static final Identifier ITEMS = id("private_items");
    static final Identifier FLUIDS = id("private_fluids");
    static final Identifier ENERGY = id("private_energy");
    static final Identifier PROGRESS = id("private_progress");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineDataProvider.INSTANCE, MachineBlockEntity.class);
        // Stop generic capability/container readers. The guarded provider below supplies this machine's HUD.
        // An empty (not null) result also prevents fallback readers from leaking a locked machine's contents.
        registration.registerItemStorage(new PrivateStorage<>(ITEMS), MachineBlockEntity.class);
        registration.registerFluidStorage(new PrivateStorage<>(FLUIDS), MachineBlockEntity.class);
        registration.registerEnergyStorage(new PrivateStorage<>(ENERGY), MachineBlockEntity.class);
        registration.registerProgress(new PrivateStorage<>(PROGRESS), MachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        JadeClient.register(registration);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("rematter", "jade/" + path);
    }

    private record PrivateStorage<T>(Identifier uid) implements IServerExtensionProvider<T> {
        @Override public Identifier getUid() { return uid; }
        @Override public int getDefaultPriority() { return -10_000; }
        @Override public List<ViewGroup<T>> getGroups(Accessor<?> accessor) { return List.of(); }
    }
}
