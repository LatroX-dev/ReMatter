package dev.latrox.rematter.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A native GUI layer whose existing controls survive a temporary recipe-viewer round trip. */
public abstract class SubstanceScreenLayer extends Screen implements SubstanceHoverProvider {
    private int layoutWidth = -1, layoutHeight = -1;
    protected SubstanceScreenLayer(Component title) { super(title); }
    protected final void rememberLayout() { layoutWidth = width; layoutHeight = height; }
    /** The container supplies the backdrop; each layer draws its own opaque graphite panel. */
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // A second Screen blur in the same frame crashes Minecraft when picker and details are stacked.
    }
    @Override protected void repositionElements() {
        if (layoutWidth != width || layoutHeight != height) super.repositionElements();
    }
}
