package dev.latrox.rematter.client;

import java.util.Optional;
import net.minecraft.client.renderer.Rect2i;

/** Substance hit testing shared by the screen and optional native recipe viewers. Coordinates are screen pixels. */
public interface SubstanceHoverProvider {
    Optional<SubstanceDetails.Hover> substanceAt(double x, double y);
    Rect2i substancePanelBounds();
    default int substanceAccent() { return UiPalette.machineAccent(dev.latrox.rematter.content.MachineKind.DRIVE); }
}
