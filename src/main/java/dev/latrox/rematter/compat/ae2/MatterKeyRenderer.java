package dev.latrox.rematter.compat.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.client.api.AEKeyRenderer;
import appeng.client.api.AEKeyRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.latrox.rematter.client.SubstanceIcon;
import dev.latrox.rematter.compat.ChemistryViewer;
import dev.latrox.rematter.content.ModContent;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** A native terminal symbol; no physical item is created when displaying a substance. */
final class MatterKeyRenderer implements AEKeyRenderer<MatterKey, MatterKeyRenderer.RenderState> {
    @Override public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor graphics, int x, int y, MatterKey key) {
        SubstanceIcon.draw(graphics, x, y, key.substance());
    }
    @Override public List<Component> getTooltip(MatterKey key) {
        var lines = ChemistryViewer.matterLines(key.substance());
        lines.add(Component.translatable("integration.rematter.stored_substance"));
        return lines;
    }

    @Override public Class<RenderState> stateClass() { return RenderState.class; }
    @Override public RenderState createState() { return new RenderState(); }
    @Override public void extract(RenderState state, MatterKey key, Level level, int seed) {
        // World-mounted AE2 displays use the registered native item renderer as their visual container.
        // Its state stays opaque; no dependency on AE2's internal renderer implementation is needed.
        var icon = AEItemKey.of(ModContent.MATTER_CELL_64K.get());
        state.capture(AEKeyRendering.getOrThrow(icon), icon, level, seed);
    }
    @Override public void submit(PoseStack pose, RenderState state, SubmitNodeCollector collector, int light) {
        if (state.renderer != null) state.renderer.submit(pose, state.value, collector, light);
    }

    static final class RenderState {
        private AEKeyRenderer<? super AEItemKey, Object> renderer;
        private Object value;
        @SuppressWarnings("unchecked")
        private <S> void capture(AEKeyRenderer<? super AEItemKey, S> renderer, AEItemKey key, Level level, int seed) {
            S value = renderer.createState();
            renderer.extract(value, key, level, seed);
            this.renderer = (AEKeyRenderer<? super AEItemKey, Object>) (AEKeyRenderer<?, ?>) renderer;
            this.value = value;
        }
    }
}
