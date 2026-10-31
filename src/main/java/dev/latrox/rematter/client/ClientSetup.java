package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MACHINE_MENU.get(), MachineScreen::new);
    }
}
