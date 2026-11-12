package dev.latrox.rematter.client;

import net.minecraft.network.chat.Component;

/** The viewer explains knowledge without changing recipe membership or manufacturing permission. */
public final class ResearchDisplay {
    private ResearchDisplay() {}
    public static Component note(String itemOrFluidId) {
        String key = !ClientResearchState.ready() ? "syncing" : !ClientResearchState.available() ? "unavailable"
                : !ClientResearchState.enabled() ? "disabled" : ClientResearchState.known(itemOrFluidId) ? "known" : "unknown";
        return Component.translatable("gui.rematter.research." + key);
    }
}
