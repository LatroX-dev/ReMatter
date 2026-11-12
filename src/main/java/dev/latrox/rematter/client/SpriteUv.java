package dev.latrox.rematter.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * Atlas rectangles of block sprites that a renderer samples directly. They are looked up once per resource reload;
 * an animated sprite keeps its rectangle while the atlas replaces its texels.
 */
public final class SpriteUv {
    private final float[] bounds;
    private final float[] density;

    /** Four values per sprite: u0, v0, u1, v1. */
    SpriteUv(float... bounds) {
        if (bounds.length % 4 != 0) throw new IllegalArgumentException("Four atlas coordinates per sprite");
        this.bounds = bounds.clone();
        this.density = new float[bounds.length/4];
        java.util.Arrays.fill(this.density,1);
    }

    public static SpriteUv resolve(SpriteGetter sprites, String... blockSprites) {
        float[] bounds = new float[blockSprites.length * 4];
        float[] density=new float[blockSprites.length];
        for (int index = 0; index < blockSprites.length; index++) {
            var sprite = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,
                    Identifier.fromNamespaceAndPath("rematter", blockSprites[index].contains("/") ? blockSprites[index] : "block/" + blockSprites[index])));
            density[index]=32F/sprite.contents().width();
            bounds[index * 4] = sprite.getU0(); bounds[index * 4 + 1] = sprite.getV0();
            bounds[index * 4 + 2] = sprite.getU1(); bounds[index * 4 + 3] = sprite.getV1();
        }
        var result=new SpriteUv(bounds);
        System.arraycopy(density,0,result.density,0,density.length);
        return result;
    }

    /** Two physical texels per model pixel, including a small 16-texel sprite. */
    public float modelPixels(int sprite){return 16/density[sprite];}
    public float modelU(int sprite,float pixels){return u(sprite,pixels*density[sprite]);}
    public float modelV(int sprite,float pixels){return v(sprite,pixels*density[sprite]);}

    /** Model texture units: 0 to 16 spans the sprite, whatever its resolution. */
    public float u(int sprite, float units) {
        return bounds[sprite * 4] + (bounds[sprite * 4 + 2] - bounds[sprite * 4]) * units / 16;
    }
    public float v(int sprite, float units) {
        return bounds[sprite * 4 + 1] + (bounds[sprite * 4 + 3] - bounds[sprite * 4 + 1]) * units / 16;
    }
}
