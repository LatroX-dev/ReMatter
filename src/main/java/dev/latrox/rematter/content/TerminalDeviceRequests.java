package dev.latrox.rematter.content;

import dev.latrox.rematter.network.TerminalDeviceActionPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import org.jspecify.annotations.Nullable;

/** Whether a terminal request matches the device row the player was actually shown, at most once per tick. */
final class TerminalDeviceRequests {
    private TerminalDeviceRequests() {}

    static boolean permits(TerminalDevicesPayload.@Nullable Device shown, TerminalDeviceActionPayload request, long tick, long lastAction) {
        if (shown == null || tick == lastAction) return false;
        if (request.action() == TerminalDeviceActionPayload.Action.CANCEL_JOB) {
            return shown.queuedBatches() > 0 || shown.progress() > 0 || shown.workingBatches() > 0;
        }
        return request.action() != TerminalDeviceActionPayload.Action.QUEUE_BATCH
                || shown.canQueue() && shown.recipeId().equals(request.recipeId());
    }
}
