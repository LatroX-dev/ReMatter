package dev.latrox.rematter.compat.rs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;

/** Optional client bootstrap, invoked only after both RS and the Minecraft client have loaded. */
public final class RsClient {
    private RsClient() {}
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("refinedstorage")) event.enqueueWork(RsClient::register);
    }
    public static void register() {
        RefinedStorageClientApi.INSTANCE.registerResourceRendering(RsMatterKey.class, new ResourceRendering() {
            @Override public String formatAmount(long amount, boolean units) { return amount + (units ? " u" : ""); }
            @Override public Component getDisplayName(ResourceKey resource) {
                return RsMatterGrid.displayName(((RsMatterKey) resource).substance());
            }
            @Override public List<Component> getTooltip(ResourceKey resource) {
                return List.of(getDisplayName(resource), Component.literal("Exact ReMatter substance quantity"));
            }
            @Override public void render(ResourceKey resource, GuiGraphicsExtractor graphics, int x, int y) {
                new RsMatterGrid((RsMatterKey) resource).render(graphics, x, y);
            }
            @Override public void render(ResourceKey resource, PoseStack poseStack, SubmitNodeCollector nodes, int light, long seed) {
                var icon=new com.refinedmods.refinedstorage.common.support.resource.ItemResource(dev.latrox.rematter.content.ModContent.MATTER_CELL_64K.get());
                RefinedStorageClientApi.INSTANCE.getResourceRendering(com.refinedmods.refinedstorage.common.support.resource.ItemResource.class)
                        .render(icon,poseStack,nodes,light,seed);
            }
        });
    }
}
