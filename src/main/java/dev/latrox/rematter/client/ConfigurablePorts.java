package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachinePortMode;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;

/** Configuration rings around the four cable seats of the compact machine family. */
final class ConfigurablePorts {
    static final String[] SPRITES = java.util.Arrays.stream(MachinePortMode.values())
            .map(mode -> "machine_port_" + mode.getSerializedName()).toArray(String[]::new);
    static final MachinePortMode.Side[] SIDES = {MachinePortMode.Side.RIGHT, MachinePortMode.Side.BACK,
            MachinePortMode.Side.LEFT, MachinePortMode.Side.BOTTOM};
    // A real half-pixel step above the casing; no coplanar overlay or tiny depth offset.
    private static final float OUTER = 2, INNER = 3, FAR = 14, INNER_FAR = 13, RISE = .5F;

    private ConfigurablePorts() {}

    static boolean supports(MachineKind kind) {
        return dev.latrox.rematter.content.MachinePortMode.configurable(kind);
    }

    static void emit(PoseStack.Pose pose, VertexConsumer buffer, int modes, SpriteUv uv,
            CardinalLighting cardinal, Direction front, int[] lights) {
        for (int index = 0; index < SIDES.length; index++) {
            var side = SIDES[index];
            int sprite = MachinePortMode.byIndex(modes >>> (4 * side.ordinal()) & 15).ordinal();
            var direction = side.world(front);
            int color = ARGB.gray(cardinal.byFace(direction));
            // Strips do not overlap at the corners; UVs sample the same ring as the reactor Machine Port.
            strip(pose, buffer, side, OUTER, OUTER, FAR, INNER, uv, sprite, color, lights[index]);
            strip(pose, buffer, side, OUTER, INNER_FAR, FAR, FAR, uv, sprite, color, lights[index]);
            strip(pose, buffer, side, OUTER, INNER, INNER, INNER_FAR, uv, sprite, color, lights[index]);
            strip(pose, buffer, side, INNER_FAR, INNER, FAR, INNER_FAR, uv, sprite, color, lights[index]);
            // Close both perimeters down to the casing: the raised ring has thickness even at a grazing angle.
            edge(pose, buffer, side, OUTER, OUTER, OUTER, FAR, uv, sprite, color, lights[index]);
            edge(pose, buffer, side, OUTER, FAR, FAR, FAR, uv, sprite, color, lights[index]);
            edge(pose, buffer, side, FAR, FAR, FAR, OUTER, uv, sprite, color, lights[index]);
            edge(pose, buffer, side, FAR, OUTER, OUTER, OUTER, uv, sprite, color, lights[index]);
            edge(pose, buffer, side, INNER, INNER, INNER_FAR, INNER, uv, sprite, color, lights[index]);
            edge(pose, buffer, side, INNER_FAR, INNER, INNER_FAR, INNER_FAR, uv, sprite, color, lights[index]);
            edge(pose, buffer, side, INNER_FAR, INNER_FAR, INNER, INNER_FAR, uv, sprite, color, lights[index]);
            edge(pose, buffer, side, INNER, INNER_FAR, INNER, INNER, uv, sprite, color, lights[index]);
        }
    }

    private static void edge(PoseStack.Pose pose, VertexConsumer buffer, MachinePortMode.Side side,
            float u0, float v0, float u1, float v1, SpriteUv uv, int sprite, int color, int light) {
        // A texel inside the coloured bevel supplies the edge material without stretching its markings.
        float u=uv.u(sprite,2.25F),v=uv.v(sprite,2.25F);
        vertex(pose,buffer,side,u0,v0,RISE,u,v,color,light);
        vertex(pose,buffer,side,u0,v0,0,u,v,color,light);
        vertex(pose,buffer,side,u1,v1,0,u,v,color,light);
        vertex(pose,buffer,side,u1,v1,RISE,u,v,color,light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, MachinePortMode.Side side,
            float u, float v, float depth, float su, float sv, int color, int light) {
        float x,y,z;
        switch(side) {
            case RIGHT -> {x=-depth;y=16-v;z=u;}
            case BACK -> {x=u;y=16-v;z=16+depth;}
            case LEFT -> {x=16+depth;y=16-v;z=16-u;}
            case BOTTOM -> {x=u;y=-depth;z=16-v;}
            default -> throw new IllegalArgumentException("No cable seat on "+side);
        }
        var matrix=pose.pose();x/=16;y/=16;z/=16;
        buffer.addVertex(matrix.m00()*x+matrix.m10()*y+matrix.m20()*z+matrix.m30(),
                matrix.m01()*x+matrix.m11()*y+matrix.m21()*z+matrix.m31(),
                matrix.m02()*x+matrix.m12()*y+matrix.m22()*z+matrix.m32())
                .setColor(color).setUv(su,sv).setLight(light);
    }

    private static void strip(PoseStack.Pose pose, VertexConsumer buffer, MachinePortMode.Side side,
            float u0, float v0, float u1, float v1, SpriteUv uv, int sprite, int color, int light) {
        float su0 = uv.u(sprite, u0), sv0 = uv.v(sprite, v0), su1 = uv.u(sprite, u1), sv1 = uv.v(sprite, v1);
        switch (side) {
            case RIGHT -> BlockQuads.west(pose, buffer, -RISE, 16-v1, u0, 16-v0, u1,
                    su0, sv0, su1, sv1, color, light);
            case BACK -> BlockQuads.south(pose, buffer, u0, 16-v1, u1, 16-v0, 16+RISE,
                    su0, sv0, su1, sv1, color, light);
            case LEFT -> BlockQuads.east(pose, buffer, 16+RISE, 16-v1, 16-u1, 16-v0, 16-u0,
                    su0, sv0, su1, sv1, color, light);
            case BOTTOM -> BlockQuads.down(pose, buffer, u0, 16-v1, u1, 16-v0, -RISE,
                    su0, sv0, su1, sv1, color, light);
            default -> throw new IllegalArgumentException("No cable seat on " + side);
        }
    }
}
