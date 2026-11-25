package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ModContent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        MachineSoundManager.register();
        event.register(ModContent.MACHINE_MENU.get(), MachineScreen::new);
        event.register(ModContent.ENERGY_CUBE_MENU.get(), EnergyCubeScreen::new);
        event.register(ModContent.CELL_TRANSFER_MENU.get(), CellTransferScreen::new);
        event.register(ModContent.WIRELESS_ACCESS_POINT_MENU.get(), WirelessAccessPointScreen::new);
        event.register(ModContent.MATTER_LEVEL_EMITTER_MENU.get(), MatterLevelEmitterScreen::new);
        event.register(ModContent.MATTER_CABLE_RELAY_MENU.get(), MatterCableRelayScreen::new);
        event.register(ModContent.SPATIAL_ANCHOR_MENU.get(), SpatialAnchorScreen::new);
        event.register(ModContent.DIMENSIONAL_LINK_MENU.get(), DimensionalEntangledLinkScreen::new);
        event.register(ModContent.ENTANGLED_LINK_MENU.get(), EntangledLinkScreen::new);
    }
    @SubscribeEvent public static void registerReloadListeners(AddClientReloadListenersEvent event) {
        // Runs with the first resource load at startup and with every later reload.
        event.addListener(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "machine_audio"),
                (ResourceManagerReloadListener) MachineSoundManager::reload);
    }
    @SubscribeEvent public static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(PortPlatesModel.ID, PortPlatesModel.Unbaked.MAP_CODEC);
        event.registerModel(ConnectedGlassModel.ID, ConnectedGlassModel.Unbaked.MAP_CODEC);
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.MACHINE_ENTITY.get(), MachineRenderer::new);
        event.registerBlockEntityRenderer(ModContent.CELL_TRANSFER_STATION_ENTITY.get(),CellTransferStationRenderer::new);
        event.registerBlockEntityRenderer(ModContent.INDUSTRIAL_MODULE_ENTITY.get(), IndustrialModuleRenderer::new);
    }
    @SubscribeEvent public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MachineRenderer.LAYER, MachineRenderer::createLayer);
        event.registerLayerDefinition(IndustrialModuleRenderer.LAYER, IndustrialModuleRenderer::createLayer);
    }
    @SubscribeEvent public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DiscoveryLensTooltip.Data.class, DiscoveryLensTooltip.Client::new);
    }
    @SubscribeEvent public static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        var wrenchLayer = net.minecraft.resources.Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "wrench_hud");
        event.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR,
                wrenchLayer,
                WrenchHudOverlay::render);
        event.registerAbove(
                wrenchLayer,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "discovery_lens_hud"),
                DiscoveryLensHudOverlay::render);
    }
}
