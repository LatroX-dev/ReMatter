package dev.latrox.rematter.compat.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.client.api.AEKeyRenderer;
import appeng.client.api.AEKeyRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** A native terminal symbol; no physical item is created when displaying a substance. */
final class MatterKeyRenderer implements AEKeyRenderer<MatterKey, MatterKeyRenderer.RenderState> {
    @Override public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor graphics, int x, int y, MatterKey key) {
        int color = switch (key.substance().phase()) {
            case SOLID -> 0xFF83D9CF;
            case LIQUID -> 0xFF6FAFE7;
            case GAS -> 0xFFDBB3EF;
        };
        graphics.fill(x, y, x + 16, y + 16, 0xFF19343C);
        graphics.fill(x, y + 14, x + 16, y + 16, color);
        var definition = ClientChemistry.current().definition(key.substance().id()).orElse(null);
        String symbol = definition == null ? "?" : definition.symbol();
        if (symbol.isBlank()) symbol = key.substance().id().path().substring(0, 1);
        String label = minecraft.font.plainSubstrByWidth(symbol, 15);
        graphics.text(minecraft.font, label, x + (16 - minecraft.font.width(label)) / 2, y + 3, color, false);
    }
    @Override public List<Component> getTooltip(MatterKey key) {
        var name = ClientChemistry.current().definition(key.substance().id())
                .map(definition -> Component.translatableWithFallback(definition.translationKey(), key.substance().id().toString()))
                .orElseGet(() -> Component.literal(key.substance().id().toString()));
        return List.of(name, Component.literal(key.substance().phase().name().toLowerCase(Locale.ROOT)),
                Component.translatableWithFallback("integration.rematter.stored_substance", "Stored substance; not an item"));
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
