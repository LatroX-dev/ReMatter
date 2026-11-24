package dev.latrox.rematter.client;

import java.util.List;
import net.minecraft.network.chat.Component;

/** The viewer explains knowledge without changing recipe membership or manufacturing permission. */
public final class ResearchDisplay {
    private ResearchDisplay() {}
    public static Component note(String itemOrFluidId) {
        return note(List.of(itemOrFluidId));
    }
    public static Component note(List<String> itemOrFluidIds) {
        String key = !ClientResearchState.ready() ? "syncing" : !ClientResearchState.available() ? "unavailable"
                : !ClientResearchState.enabled() ? "disabled"
                : !itemOrFluidIds.isEmpty() && itemOrFluidIds.stream().allMatch(ClientResearchState::known) ? "known" : "unknown";
        return Component.translatable("gui.rematter.research." + key);
    }
}
