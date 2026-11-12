package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.MachinePortMode;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurablePortsTest {
    private static SpriteUv sprites() {
        float[] bounds = new float[ConfigurablePorts.SPRITES.length * 4];
        for (int i = 0; i < bounds.length / 4; i++) {
            bounds[4*i] = i; bounds[4*i+2] = i+1; bounds[4*i+3] = 1;
        }
        return new SpriteUv(bounds);
    }

    private static List<RecordedQuads.Quad> draw(Direction front, int modes) {
        var pose = new PoseStack();
        pose.translate(.5F, 0, .5F);
        pose.mulPose(Axis.YP.rotationDegrees(180-front.toYRot()));
        pose.translate(-.5F, 0, -.5F);
        var buffer = new RecordedQuads();
        ConfigurablePorts.emit(pose.last(), buffer, modes, sprites(), CardinalLighting.DEFAULT, front,
                new int[]{11,22,33,44});
        return buffer.quads();
    }

    @Test void allFourSeatsFollowTheFacingAndUseTheirOwnModeAndNeighbourLight() {
        for (var front : Direction.Plane.HORIZONTAL) {
            int packed = 0;
            for (int i=0; i<4; i++) packed |= (i+1) << (4*ConfigurablePorts.SIDES[i].ordinal());
            var faces = draw(front, packed);
            assertEquals(48, faces.size());
            for (int i=0; i<4; i++) {
                var direction = ConfigurablePorts.SIDES[i].world(front);
                var expected = new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                for (var quad : faces.subList(12*i, 12*i+4)) {
                    assertTrue(quad.planar());
                    assertEquals(expected, quad.side());
                    for (var vertex : quad.vertices()) {
                        assertEquals(i+1, (int)Math.floor(vertex.u()));
                        assertEquals((i+1)*11, vertex.light());
                    }
                    int axis = direction.getStepX()!=0 ? 0 : direction.getStepY()!=0 ? 1 : 2;
                    float plane = direction.getStepX()+direction.getStepY()+direction.getStepZ()>0 ? 16.5F/16 : -.5F/16;
                    assertEquals(plane, quad.min(axis), 1e-5F, "Ring must stay clear of the casing plane");
                    assertEquals(plane, quad.max(axis), 1e-5F);
                }
            }
        }
    }

    @Test void everyModeUsesTheShippedMachinePortSpriteAndUnknownModeIsDisabled() {
        for (var mode : MachinePortMode.values()) {
            assertNotNull(getClass().getResource("/assets/rematter/textures/block/"+
                    ConfigurablePorts.SPRITES[mode.ordinal()]+".png"));
            int packed=0;
            for (var side:ConfigurablePorts.SIDES) packed|=mode.ordinal()<<(4*side.ordinal());
            for (var quad:draw(Direction.NORTH,packed)) for(var vertex:quad.vertices())
                assertEquals(mode.ordinal(), (int)Math.floor(vertex.u()));
        }
        for(var quad:draw(Direction.NORTH,0xFFFFFF)) for(var vertex:quad.vertices())
            assertEquals(MachinePortMode.DISABLED.ordinal(),(int)Math.floor(vertex.u()));
    }
}
