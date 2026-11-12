package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineRendererDetailTest {
    @Test void bakedDevicesNeedNoProcessMotionOrShownItems() {
        assertEquals(MachineRenderer.Detail.NONE, MachineRenderer.Detail.of(MachineKind.TERMINAL));
        assertEquals(MachineRenderer.Detail.NONE, MachineRenderer.Detail.of(MachineKind.NETWORK_INTERFACE));
        assertEquals(MachineRenderer.Detail.NONE, MachineRenderer.Detail.of(MachineKind.REACTOR));
        assertTrue(ConfigurablePorts.supports(MachineKind.REACTOR), "The baked reactor still draws its configured port rings");
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
