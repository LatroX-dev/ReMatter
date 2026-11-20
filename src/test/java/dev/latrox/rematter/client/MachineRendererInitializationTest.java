package dev.latrox.rematter.client;

import java.util.Map;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MachineRendererInitializationTest {
    @Test void constructsBeforeItemRegistryAndComponentsAreReady() {
        // Resource reload constructs renderers before item components are bound.
        // Use the real layer and context, without bootstrapping game registries or a client.
        var models = new EntityModelSet(Map.of(MachineRenderer.LAYER, MachineRenderer.createLayer()));
        var context = new BlockEntityRendererProvider.Context(
                null, null, null, null, models, null, null, null);

        assertDoesNotThrow(() -> new MachineRenderer(context));
    }
}
