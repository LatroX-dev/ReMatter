package dev.latrox.rematter.guide;

import guideme.GuidesCommon;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** Loaded only behind the GuideME mod-presence check. */
public final class GuideMeIntegration {
    public static final Identifier GUIDE = Identifier.fromNamespaceAndPath("rematter", "guide");
    private GuideMeIntegration() {}

    public static void open(Player player) {
        GuidesCommon.openGuide(player, GUIDE);
    }
}
