package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MACHINE_MENU.get(), MachineScreen::new);
        event.register(ModContent.ENERGY_CUBE_MENU.get(),EnergyCubeScreen::new);
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.MACHINE_ENTITY.get(), MachineRenderer::new);
        event.registerBlockEntityRenderer(ModContent.INDUSTRIAL_MODULE_ENTITY.get(), IndustrialModuleRenderer::new);
    }
    @SubscribeEvent public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MachineRenderer.LAYER, MachineRenderer::createLayer);
        event.registerLayerDefinition(IndustrialModuleRenderer.LAYER, IndustrialModuleRenderer::createLayer);
    }
}
