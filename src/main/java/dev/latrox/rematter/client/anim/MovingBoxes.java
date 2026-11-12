package dev.latrox.rematter.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.latrox.rematter.client.BlockQuads;
import dev.latrox.rematter.client.SpriteUv;
import dev.latrox.rematter.client.UiPalette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.joml.Quaternionf;

/** Model-pixel boxes rendered from the block atlas, including the face shades of the surrounding casing. */
public final class MovingBoxes implements SubmitNodeCollector.CustomGeometryRenderer {
    public static final int ALL = 63, NORTH = 1, SOUTH = 2, WEST = 4, EAST = 8, UP = 16, DOWN = 32;
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int sprite, int faces,
                      int tint, int light, float px, float py, float pz, Quaternionf rotation,int uvMode,float emission) {}
    private final List<Box> boxes = new ArrayList<>();
    public SpriteUv sprites;
    public CardinalLighting cardinal = CardinalLighting.DEFAULT;
    public Direction facing = Direction.NORTH;
    public void clear() { boxes.clear(); }
    public List<Box> boxes() { return List.copyOf(boxes); }
    public void box(float x0,float y0,float z0,float x1,float y1,float z1,int sprite,int color,int light) {
        box(x0,y0,z0,x1,y1,z1,sprite,ALL,color,light,0,0,0,null);
    }
    public void box(float x0,float y0,float z0,float x1,float y1,float z1,int sprite,int faces,int color,int light,
                    float px,float py,float pz,Quaternionf rotation) {
        if(x1-x0<.001F||y1-y0<.001F||z1-z0<.001F)return;
        boxes.add(new Box(x0,y0,z0,x1,y1,z1,sprite,faces,color,light,px,py,pz,rotation,0,sprite==1?1:0));
    }
    /** Packed sight-glass tiles, cropped in model position as the meniscus moves. */
    public void reactor(float x0,float y0,float z0,float x1,float y1,float z1,int sprite,int color,int light,float emission) {
        if(y1-y0<.001F)return;
        boxes.add(new Box(x0,y0,z0,x1,y1,z1,sprite,NORTH|SOUTH|WEST|EAST,color,light,0,0,0,null,1,emission));
    }
    public void beam(float x,float y,float z,float tx,float ty,float tz,float width,int sprite,int color,int light) {
        float dx=tx-x,dy=ty-y,dz=tz-z,length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(!Float.isFinite(length)||length<.001F)return;
        box(x-width/2,y,z-width/2,x+width/2,y+length,z+width/2,sprite,ALL,color,light,x,y,z,
                new Quaternionf().rotationTo(0,1,0,dx/length,dy/length,dz/length));
    }
    public static int light(int world,float blend) {
        int block=Math.round(LightCoordsUtil.block(world)+(15-LightCoordsUtil.block(world))*blend);
        int sky=Math.round(LightCoordsUtil.sky(world)+(15-LightCoordsUtil.sky(world))*blend);
        return LightCoordsUtil.pack(block,sky);
    }
    @Override public void render(PoseStack.Pose pose,VertexConsumer buffer) {
        if(sprites==null)return;
        for(var box:boxes){
            PoseStack.Pose transformed=pose;
            if(box.rotation()!=null){
                var stack=new PoseStack();stack.last().pose().set(pose.pose());stack.last().normal().set(pose.normal());
                stack.translate(box.px()/16,box.py()/16,box.pz()/16);stack.mulPose(box.rotation());
                stack.translate(-box.px()/16,-box.py()/16,-box.pz()/16);transformed=stack.last();
            }
            emit(transformed,buffer,box);
        }
    }
    private int shade(Box b,Direction face) {
        Direction world=face.getAxis().isVertical()?face:Direction.fromYRot(face.toYRot()+180-facing.toYRot());
        float shade=cardinal.byFace(world);
        if(b.tint()==UiPalette.MOTION_STEEL) return UiPalette.multiply(b.tint(),shade);
        return UiPalette.multiply(b.tint(),shade+(1-shade)*b.emission());
    }
    private void emit(PoseStack.Pose p,VertexConsumer b,Box a){
        int s=a.sprite(),l=a.light();float x=a.x0(),y=a.y0(),z=a.z0(),X=a.x1(),Y=a.y1(),Z=a.z1();
        // Position-mapped UVs: a 16-model-pixel edge always spans one 32-texel sprite.
        float tile=sprites.modelPixels(s);
        float frontU0=a.uvMode()==1?0:(tile-X)%tile,frontU1=frontU0+(X-x);
        float sideU0=a.uvMode()==1?3:z%tile,sideU1=sideU0+(Z-z);
        float v0=a.uvMode()==1?10-Y:(tile-Y)%tile,v1=v0+(Y-y);
        // Negative relative coordinates of chamber rings wrap to the same repeatable 16px material tile.
        if(frontU0<0){frontU0+=tile;frontU1+=tile;}if(sideU0<0){sideU0+=tile;sideU1+=tile;}if(v0<0){v0+=tile;v1+=tile;}
        if(frontU1>tile){frontU0=0;frontU1=X-x;}if(sideU1>tile){sideU0=0;sideU1=Z-z;}if(v1>tile){v0=0;v1=Y-y;}
        float fu=sprites.modelU(s,frontU0),fU=sprites.modelU(s,frontU1),su=sprites.modelU(s,sideU0),sU=sprites.modelU(s,sideU1),v=sprites.modelV(s,v0),V=sprites.modelV(s,v1);
        // Sprite one is the existing flat white technical ink mask. Sample its centre for arbitrary beam lengths.
        if(s==1){fu=fU=su=sU=sprites.u(s,8);v=V=sprites.v(s,8);}
        if((a.faces()&NORTH)!=0)BlockQuads.north(p,b,x,y,X,Y,z,fu,v,fU,V,shade(a,Direction.NORTH),l);
        if((a.faces()&SOUTH)!=0)BlockQuads.south(p,b,x,y,X,Y,Z,fu,v,fU,V,shade(a,Direction.SOUTH),l);
        if((a.faces()&WEST)!=0)BlockQuads.west(p,b,x,y,z,Y,Z,su,v,sU,V,shade(a,Direction.WEST),l);
        if((a.faces()&EAST)!=0)BlockQuads.east(p,b,X,y,z,Y,Z,su,v,sU,V,shade(a,Direction.EAST),l);
        if((a.faces()&UP)!=0)BlockQuads.up(p,b,x,z,X,Z,Y,sprites.u(s, s==1?8:0),sprites.v(s,s==1?8:0),sprites.modelU(s,s==1?4:X-x),sprites.modelV(s,s==1?4:Z-z),shade(a,Direction.UP),l);
        if((a.faces()&DOWN)!=0)BlockQuads.down(p,b,x,z,X,Z,y,sprites.u(s, s==1?8:0),sprites.v(s,s==1?8:0),sprites.modelU(s,s==1?4:X-x),sprites.modelV(s,s==1?4:Z-z),shade(a,Direction.DOWN),l);
    }
}
