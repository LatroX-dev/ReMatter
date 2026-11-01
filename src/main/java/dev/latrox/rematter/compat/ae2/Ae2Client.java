package dev.latrox.rematter.compat.ae2;

import appeng.client.api.AEKeyRendering;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registration is kept out of the common AE2 adapter's class initialization. */
final class Ae2Client {
    private Ae2Client() {}
    static void register(IEventBus bus) { bus.addListener(Ae2Client::setup); }
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AEKeyRendering.register(MatterKey.TYPE, MatterKey.class, new MatterKeyRenderer()));
    }
}
