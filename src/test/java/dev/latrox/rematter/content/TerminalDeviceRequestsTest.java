package dev.latrox.rematter.content;

import dev.latrox.rematter.network.TerminalDeviceActionPayload;
import dev.latrox.rematter.network.TerminalDeviceActionPayload.Action;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalDeviceRequestsTest {
    private static final BlockPos POSITION = new BlockPos(3, 64, -9);
    private static final String RECIPE = "rematter:water_electrolysis";

    private static TerminalDevicesPayload.Device shown(String recipe, boolean canQueue) {
        return new TerminalDevicesPayload.Device(POSITION, MachineKind.ELECTROLYZER, "paused", recipe, 0, 0, 0, canQueue);
    }

    @Test void onlyDevicesInTheShownListCanBeOpenedOrQueued() {
        var open = new TerminalDeviceActionPayload(1, POSITION, Action.OPEN, "");
        assertTrue(TerminalDeviceRequests.permits(shown(RECIPE, true), open, 20, 19));
        assertFalse(TerminalDeviceRequests.permits(null, open, 20, 19));
        assertFalse(TerminalDeviceRequests.permits(null, new TerminalDeviceActionPayload(1, POSITION, Action.QUEUE_BATCH, RECIPE), 20, 19));
    }

    @Test void queueingRequiresTheShownRecipeAndAQueueableDevice() {
        var queue = new TerminalDeviceActionPayload(1, POSITION, Action.QUEUE_BATCH, RECIPE);
        assertTrue(TerminalDeviceRequests.permits(shown(RECIPE, true), queue, 20, 19));
        assertFalse(TerminalDeviceRequests.permits(shown(RECIPE, false), queue, 20, 19));
        assertFalse(TerminalDeviceRequests.permits(shown("rematter:other_process", true), queue, 20, 19));
    }

    @Test void atMostOneRequestIsAcceptedPerTick() {
        var open = new TerminalDeviceActionPayload(1, POSITION, Action.OPEN, "");
        assertFalse(TerminalDeviceRequests.permits(shown(RECIPE, true), open, 20, 20));
        assertTrue(TerminalDeviceRequests.permits(shown(RECIPE, true), open, 0, Long.MIN_VALUE));
    }
}
