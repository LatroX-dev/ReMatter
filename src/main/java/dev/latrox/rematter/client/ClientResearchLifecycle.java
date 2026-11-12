package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientResearchLifecycle {
    private ClientResearchLifecycle() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientResearchState.clear(); ClientResearchToasts.clear(); }
}
