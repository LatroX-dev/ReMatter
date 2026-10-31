package dev.latrox.rematter.guide;

import guideme.GuidesCommon;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** Loaded only behind the GuideME mod-presence check. */
public final class GuideMeIntegration {
    private GuideMeIntegration() {}

    public static void open(Player player) {
        GuidesCommon.openGuide(player, Identifier.fromNamespaceAndPath("rematter", "guide"));
    }
}
