package dev.latrox.rematter.compat.rs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceRendering;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.SubstanceIcon;
import dev.latrox.rematter.client.SubstanceLabels;
import dev.latrox.rematter.compat.ChemistryViewer;
import java.util.ArrayList;
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
        RsMatterGrid.install(new RsMatterGrid.View() {
            @Override public Component name(SubstanceKey substance) { return ChemistryViewer.matterName(substance); }
            @Override public List<Component> lines(SubstanceKey substance) { return ChemistryViewer.matterLines(substance); }
            @Override public String symbol(SubstanceKey substance) { return SubstanceLabels.iconLabel(substance.id()); }
            @Override public void render(GuiGraphicsExtractor graphics, int x, int y, SubstanceKey substance) {
                SubstanceIcon.draw(graphics, x, y, substance);
            }
        });
        RefinedStorageClientApi.INSTANCE.registerResourceRendering(RsMatterKey.class, new ResourceRendering() {
            @Override public String formatAmount(long amount, boolean units) { return amount + (units ? " u" : ""); }
            @Override public Component getDisplayName(ResourceKey resource) {
                return RsMatterGrid.displayName(((RsMatterKey) resource).substance());
            }
            @Override public List<Component> getTooltip(ResourceKey resource) {
                var lines = new ArrayList<>(RsMatterGrid.lines(((RsMatterKey) resource).substance()));
                lines.add(Component.translatable("integration.rematter.stored_substance"));
                return lines;
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
