package dev.latrox.rematter.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A native GUI layer whose existing controls survive a temporary recipe-viewer round trip. */
public abstract class SubstanceScreenLayer extends Screen implements SubstanceHoverProvider {
    private int layoutWidth = -1, layoutHeight = -1;
    protected SubstanceScreenLayer(Component title) { super(title); }
    protected final void rememberLayout() { layoutWidth = width; layoutHeight = height; }
    @Override protected void repositionElements() {
        if (layoutWidth != width || layoutHeight != height) super.repositionElements();
    }
}
