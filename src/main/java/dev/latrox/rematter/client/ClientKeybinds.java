package dev.latrox.rematter.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.network.OpenWirelessTerminalPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Configurable client hotkeys for ReMatter handheld tools. */
@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientKeybinds {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "general"));
    public static final KeyMapping OPEN_WIRELESS_TERMINAL = new KeyMapping(
            "key.rematter.open_wireless_terminal",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            CATEGORY);
    public static final KeyMapping TOGGLE_DISCOVERY_LENS = new KeyMapping(
            "key.rematter.toggle_discovery_lens",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            CATEGORY);

    private ClientKeybinds() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_WIRELESS_TERMINAL);
        event.register(TOGGLE_DISCOVERY_LENS);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        while (OPEN_WIRELESS_TERMINAL.consumeClick()) {
            ClientPacketDistributor.sendToServer(OpenWirelessTerminalPayload.INSTANCE);
        }
        while (TOGGLE_DISCOVERY_LENS.consumeClick()) {
            if (!DiscoveryLensState.carriedOrEquipped(minecraft.player)) {
                minecraft.gui.setOverlayMessage(
                        Component.translatable("message.rematter.discovery_lens.none_carried"), false);
            } else {
                boolean active = DiscoveryLensState.toggle();
                minecraft.gui.setOverlayMessage(
                        Component.translatable(active
                                ? "message.rematter.discovery_lens.enabled"
                                : "message.rematter.discovery_lens.disabled"),
                        false);
            }
        }
    }
}
