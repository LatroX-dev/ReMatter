package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BlockQuadsTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyVertexSuppliesTheTransformedOutwardNormal(boolean transform) {
        var stack = new PoseStack();
        if (transform) {
            stack.translate(3, -2, 1);
            stack.mulPose(Axis.YP.rotationDegrees(68));
            stack.scale(2, 1, 0.75F);
            stack.mulPose(Axis.XP.rotationDegrees(27));
        }
        var pose = stack.last();
        var buffer = new RecordedQuads();
        BlockQuads.north(pose, buffer, 0, 0, 16, 16, 0, 0, 0, 1, 1, -1, 0);
        BlockQuads.south(pose, buffer, 0, 0, 16, 16, 16, 0, 0, 1, 1, -1, 0);
        BlockQuads.west(pose, buffer, 0, 0, 0, 16, 16, 0, 0, 1, 1, -1, 0);
        BlockQuads.east(pose, buffer, 16, 0, 0, 16, 16, 0, 0, 1, 1, -1, 0);
        BlockQuads.up(pose, buffer, 0, 0, 16, 16, 16, 0, 0, 1, 1, -1, 0);
        BlockQuads.down(pose, buffer, 0, 0, 16, 16, 0, 0, 0, 1, 1, -1, 0);
        var quads = buffer.quads();
        assertEquals(6, quads.size());
        for (var quad : quads) {
            var geometricNormal = quad.facing();
            for (var vertex : quad.vertices()) {
                assertNotNull(vertex.normal(), "Shader vertex formats require a normal on every vertex");
                assertTrue(geometricNormal.equals(vertex.normal(), 1e-5F),
                        () -> "Expected " + geometricNormal + " from face winding, got " + vertex.normal());
            }
        }
    }
}
