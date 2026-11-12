package dev.latrox.rematter.client;

import dev.latrox.rematter.network.ResearchLearnedPayload;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** A distinct native toast for each genuinely learned family, never reconstructed from login snapshots. */
public final class ClientResearchToasts {
    private static final Set<String> SHOWN = new HashSet<>();
    private ClientResearchToasts() {}
    public static void clear() { SHOWN.clear(); }
    public static void show(ResearchLearnedPayload notice) {
        if (Minecraft.getInstance().player == null || !SHOWN.add(notice.family())) return;
        if (SHOWN.size() > 32768) SHOWN.clear();
        Component body = notice.decompositionOnly() ? Component.translatable("gui.rematter.research.toast_decomposition", notice.name()) : Component.literal(notice.name());
        SystemToast.add(Minecraft.getInstance().getToastManager(), new SystemToast.SystemToastId(),
                Component.translatable("gui.rematter.research.toast_title"), body);
    }
}
