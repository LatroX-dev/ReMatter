package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineRendererDetailTest {
    @Test void bakedDevicesNeedNoProcessMotionOrShownItems() {
        assertEquals(MachineRenderer.Detail.NONE, MachineRenderer.Detail.of(MachineKind.TERMINAL));
        assertEquals(MachineRenderer.Detail.NONE, MachineRenderer.Detail.of(MachineKind.NETWORK_INTERFACE));
        assertEquals(MachineRenderer.Detail.MECHANISM, MachineRenderer.Detail.of(MachineKind.REACTOR));
        // Port plates are block models now; the renderer draws nothing for a device without moving parts.
        assertEquals(MachineRenderer.Detail.NONE, MachineRenderer.Detail.of(MachineKind.PRIMAL_CONVERTER));
    }

    @Test void onlyTheDriveExtractsCellBaysAndOnlyItemShowingKindsExtractItems() {
        for (var kind : MachineKind.values()) {
            var detail = MachineRenderer.Detail.of(kind);
            assertEquals(kind == MachineKind.DRIVE, detail == MachineRenderer.Detail.CELLS, kind.name());
        }
        assertEquals(MachineRenderer.Detail.ITEMS, MachineRenderer.Detail.of(MachineKind.HAND_PREPARATOR));
        assertEquals(MachineRenderer.Detail.ITEMS, MachineRenderer.Detail.of(MachineKind.INDUSTRIAL_REACTOR));
    }
}
