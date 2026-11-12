package dev.latrox.rematter.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * Atlas rectangles of block sprites that a renderer samples directly. They are looked up once per resource reload;
 * an animated sprite keeps its rectangle while the atlas replaces its texels.
 */
final class SpriteUv {
    private final float[] bounds;

    /** Four values per sprite: u0, v0, u1, v1. */
    SpriteUv(float... bounds) {
        if (bounds.length % 4 != 0) throw new IllegalArgumentException("Four atlas coordinates per sprite");
        this.bounds = bounds.clone();
    }

    static SpriteUv resolve(SpriteGetter sprites, String... blockSprites) {
        float[] bounds = new float[blockSprites.length * 4];
        for (int index = 0; index < blockSprites.length; index++) {
            var sprite = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,
                    Identifier.fromNamespaceAndPath("rematter", "block/" + blockSprites[index])));
            bounds[index * 4] = sprite.getU0(); bounds[index * 4 + 1] = sprite.getV0();
            bounds[index * 4 + 2] = sprite.getU1(); bounds[index * 4 + 3] = sprite.getV1();
        }
        return new SpriteUv(bounds);
    }

    /** Model texture units: 0 to 16 spans the sprite, whatever its resolution. */
    float u(int sprite, float units) {
        return bounds[sprite * 4] + (bounds[sprite * 4 + 2] - bounds[sprite * 4]) * units / 16;
    }
    float v(int sprite, float units) {
        return bounds[sprite * 4 + 1] + (bounds[sprite * 4 + 3] - bounds[sprite * 4 + 1]) * units / 16;
    }
}
