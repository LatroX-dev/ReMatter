package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** Collects what a renderer writes, four vertices to a quad, without a graphics context. */
final class RecordedQuads implements VertexConsumer {
    record Vertex(float x, float y, float z, int color, float u, float v, int light) {
        Vector3f position() { return new Vector3f(x, y, z); }
    }
    record Quad(List<Vertex> vertices) {
        /** Direction a viewer must stand in to see the quad: counter-clockwise vertices face the viewer. */
        Vector3f facing() {
            var first = vertices.get(0).position();
            var normal = vertices.get(1).position().sub(first).cross(vertices.get(2).position().sub(first));
            return normal.normalize();
        }
        /** The facing of an axis-aligned quad as an exact unit vector, without negative zeros. */
        Vector3f side() {
            var normal = facing();
            return new Vector3f(Math.round(normal.x) + 0F, Math.round(normal.y) + 0F, Math.round(normal.z) + 0F);
        }
        float min(int axis) { return (float) vertices.stream().mapToDouble(vertex -> vertex.position().get(axis)).min().orElseThrow(); }
        float max(int axis) { return (float) vertices.stream().mapToDouble(vertex -> vertex.position().get(axis)).max().orElseThrow(); }
        boolean planar() {
            var normal = facing();
            float offset = normal.dot(vertices.get(0).position());
            return vertices.stream().allMatch(vertex -> Math.abs(normal.dot(vertex.position()) - offset) < 1e-5F);
        }
    }

    private final List<Vertex> vertices = new ArrayList<>();
    private float x, y, z, u, v;
    private int color, light;
    private boolean open;

    List<Quad> quads() {
        flush();
        if (vertices.size() % 4 != 0) throw new AssertionError("A quad was left unfinished");
        var quads = new ArrayList<Quad>();
        for (int index = 0; index < vertices.size(); index += 4) quads.add(new Quad(List.copyOf(vertices.subList(index, index + 4))));
        return quads;
    }
    private void flush() {
        if (open) vertices.add(new Vertex(x, y, z, color, u, v, light));
        open = false;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        flush();
        this.x = x; this.y = y; this.z = z; open = true;
        return this;
    }
    @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        color = alpha << 24 | red << 16 | green << 8 | blue;
        return this;
    }
    @Override public VertexConsumer setColor(int color) { this.color = color; return this; }
    @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { light = u & 0xFFFF | (v & 0xFFFF) << 16; return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    @Override public VertexConsumer setLineWidth(float width) { return this; }
}
