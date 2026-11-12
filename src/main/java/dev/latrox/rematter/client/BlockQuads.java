package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Faces of axis-aligned boxes drawn the way baked block faces are: coordinates in model pixels, the vertex order
 * and texture orientation of block models, and the face shade carried in the vertex colour. The texture rectangle
 * (u0, v0)-(u1, v1) is in atlas coordinates; (u0, v0) is the top left corner as seen from outside the face.
 */
final class BlockQuads {
    private BlockQuads() {}

    static void north(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z,
            float u0, float v0, float u1, float v1, int color, int light) {
        vertex(pose, buffer, x1, y1, z, u0, v0, color, light);
        vertex(pose, buffer, x1, y0, z, u0, v1, color, light);
        vertex(pose, buffer, x0, y0, z, u1, v1, color, light);
        vertex(pose, buffer, x0, y1, z, u1, v0, color, light);
    }
    static void south(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z,
            float u0, float v0, float u1, float v1, int color, int light) {
        vertex(pose, buffer, x0, y1, z, u0, v0, color, light);
        vertex(pose, buffer, x0, y0, z, u0, v1, color, light);
        vertex(pose, buffer, x1, y0, z, u1, v1, color, light);
        vertex(pose, buffer, x1, y1, z, u1, v0, color, light);
    }
    static void west(PoseStack.Pose pose, VertexConsumer buffer, float x, float y0, float z0, float y1, float z1,
            float u0, float v0, float u1, float v1, int color, int light) {
        vertex(pose, buffer, x, y1, z0, u0, v0, color, light);
        vertex(pose, buffer, x, y0, z0, u0, v1, color, light);
        vertex(pose, buffer, x, y0, z1, u1, v1, color, light);
        vertex(pose, buffer, x, y1, z1, u1, v0, color, light);
    }
    static void east(PoseStack.Pose pose, VertexConsumer buffer, float x, float y0, float z0, float y1, float z1,
            float u0, float v0, float u1, float v1, int color, int light) {
        vertex(pose, buffer, x, y1, z1, u0, v0, color, light);
        vertex(pose, buffer, x, y0, z1, u0, v1, color, light);
        vertex(pose, buffer, x, y0, z0, u1, v1, color, light);
        vertex(pose, buffer, x, y1, z0, u1, v0, color, light);
    }
    static void up(PoseStack.Pose pose, VertexConsumer buffer, float x0, float z0, float x1, float z1, float y,
            float u0, float v0, float u1, float v1, int color, int light) {
        vertex(pose, buffer, x0, y, z0, u0, v0, color, light);
        vertex(pose, buffer, x0, y, z1, u0, v1, color, light);
        vertex(pose, buffer, x1, y, z1, u1, v1, color, light);
        vertex(pose, buffer, x1, y, z0, u1, v0, color, light);
    }
    static void down(PoseStack.Pose pose, VertexConsumer buffer, float x0, float z0, float x1, float z1, float y,
            float u0, float v0, float u1, float v1, int color, int light) {
        vertex(pose, buffer, x0, y, z1, u0, v0, color, light);
        vertex(pose, buffer, x0, y, z0, u0, v1, color, light);
        vertex(pose, buffer, x1, y, z0, u1, v1, color, light);
        vertex(pose, buffer, x1, y, z1, u1, v0, color, light);
    }

    /** Transforms by hand: the matrix helper of the vertex consumer creates a vector per vertex. */
    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
            int color, int light) {
        Matrix4f matrix = pose.pose();
        float px = x / 16, py = y / 16, pz = z / 16;
        buffer.addVertex(matrix.m00() * px + matrix.m10() * py + matrix.m20() * pz + matrix.m30(),
                        matrix.m01() * px + matrix.m11() * py + matrix.m21() * pz + matrix.m31(),
                        matrix.m02() * px + matrix.m12() * py + matrix.m22() * pz + matrix.m32())
                .setColor(color).setUv(u, v).setLight(light);
    }
}
