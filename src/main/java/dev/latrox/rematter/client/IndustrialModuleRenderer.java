package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.client.anim.AnimClock;
import dev.latrox.rematter.client.anim.MovingBoxes;
import dev.latrox.rematter.client.anim.RendererParts;
import dev.latrox.rematter.client.anim.WorkEnvelope;
import dev.latrox.rematter.content.IndustrialModuleBlock;
import dev.latrox.rematter.content.IndustrialModuleBlockEntity;
import dev.latrox.rematter.content.IndustrialModuleKind;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/** Real mechanisms and smoothly growing field connections; preparation stages never switch the picture. */
public final class IndustrialModuleRenderer implements BlockEntityRenderer<IndustrialModuleBlockEntity,IndustrialModuleRenderState> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter","industrial_modules"),"main");
    private static final String[] SPRITES={"bench_metal","entity/effect_light"};
    private final Map<Level,Map<Focus,WorkEnvelope>> motions=new WeakHashMap<>();
    private record Focus(long x2,long y2,long z2) {}
    private final Map<IndustrialModuleBlockEntity,Focus> lastFormedFocus=new WeakHashMap<>();
    private final SpriteGetter spriteGetter;
    private @Nullable SpriteUv atlas;
    public IndustrialModuleRenderer(BlockEntityRendererProvider.Context context){spriteGetter=context.sprites();}
    public static LayerDefinition createLayer(){return LayerDefinition.create(new MeshDefinition(),16,16);}
    @Override public IndustrialModuleRenderState createRenderState(){return new IndustrialModuleRenderState();}
    @Override public int getViewDistance(){return 64;}
    @Override public AABB getRenderBoundingBox(IndustrialModuleBlockEntity module){return module.visuallyFormed()?new AABB(module.getBlockPos()).inflate(8):new AABB(module.getBlockPos());}
    @Override public void extractRenderState(IndustrialModuleBlockEntity module,IndustrialModuleRenderState s,float partialTick,Vec3 cameraPosition,ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress){
        BlockEntityRenderer.super.extractRenderState(module,s,partialTick,cameraPosition,breakProgress);
        s.kind=module.kind();s.facing=module.getBlockState().getValue(IndustrialModuleBlock.FACING);s.suspended=module.getBlockState().getValue(IndustrialModuleBlock.SUSPENDED);
        s.active=module.getBlockState().getValue(IndustrialModuleBlock.ACTIVE);s.formed=module.visuallyFormed();s.emitting=s.active&&s.formed;
        s.targetX=module.beamTargetX();s.targetY=module.beamTargetY();s.targetZ=module.beamTargetZ();
        s.clock=AnimClock.now(partialTick);
        // Modules aimed at the same chamber share one phase, even if a different one comes into view later.
        float dx=s.targetX-.5F,dz=s.targetZ-.5F;
        float rx=switch(s.facing){case EAST -> -dz;case SOUTH -> -dx;case WEST -> dz;default -> dx;};
        float rz=switch(s.facing){case EAST -> dx;case SOUTH -> -dz;case WEST -> -dx;default -> dz;};
        var pos=module.getBlockPos();
        var focus=new Focus(Math.round((pos.getX()+.5+rx)*2),Math.round((pos.getY()+(double)s.targetY)*2),Math.round((pos.getZ()+.5+rz)*2));
        if(s.formed)lastFormedFocus.put(module,focus);
        else focus=lastFormedFocus.getOrDefault(module,focus);
        var worldMotions=motions.computeIfAbsent(module.getLevel(),key->new LinkedHashMap<>(16,.75F,true){
            @Override protected boolean removeEldestEntry(Map.Entry<Focus,WorkEnvelope> eldest){return size()>4096;}
        });
        var motion=worldMotions.computeIfAbsent(focus,key->new WorkEnvelope());
        motion.sample(s.clock,s.emitting,module.visualWorkDuration()>0,module.visualWorkProgress(),module.visualWorkDuration(),module.visualBaseRate());
        s.phase=motion.phase(48);s.drive=motion.drive();s.engage=motion.engage();s.turns=motion.turns();s.progress=motion.progress();
        if(atlas==null)atlas=SpriteUv.resolve(spriteGetter,SPRITES);
        s.moving.clear();s.moving.sprites=atlas;s.moving.facing=s.facing;
        if(module.getLevel() instanceof BlockAndTintGetter view)s.moving.cardinal=view.cardinalLighting();
        geometry(s);
    }
    private static void geometry(IndustrialModuleRenderState s){
        var m=s.moving;int light=MovingBoxes.light(s.lightCoords,s.drive),accent=UiPalette.moduleAccent(s.kind);
        // Unformed modules are still physical hardware; only their working amplitude returns to rest.
        if(s.kind==IndustrialModuleKind.GRINDING_UNIT){
            float p=(float)((s.turns/24)%1),stroke=(p<.25F?(p/.25F)*(p/.25F):p<.35F?1:p<.85F?1-WorkEnvelope.ease((p-.35)/.5):0)*s.engage;
            var blade=RendererParts.MODULE_BLADE;var edge=RendererParts.MODULE_EDGE;
            localBox(s,blade.x0(),blade.y0()-stroke,blade.z0(),blade.x1(),blade.y1(),blade.z1(),0,UiPalette.WHITE,s.lightCoords,MovingBoxes.ALL^MovingBoxes.UP,null,0,0,0);
            localBox(s,edge.x0(),edge.y0()-stroke,edge.z0(),edge.x1(),edge.y1()-stroke,edge.z1(),1,UiPalette.multiply(accent,.5F+.5F*s.drive),light,MovingBoxes.ALL^MovingBoxes.UP,null,0,0,0);
        }else if(s.kind==IndustrialModuleKind.MIXING_BASIN){
            var a=RendererParts.MODULE_PADDLE;
            var q=new Quaternionf().rotationY((float)Math.toRadians((s.turns*7.5)%360)*(s.suspended?-1:1));
            localBox(s,a.x0(),a.y0(),a.z0(),a.x1(),a.y1(),a.z1(),0,UiPalette.WHITE,s.lightCoords,MovingBoxes.ALL^MovingBoxes.DOWN,q,8,0,8);
        }
        if(!s.formed||s.drive<.0001F)return;
        float source=switch(s.kind){case FIELD_COIL -> 15;case REACTION_CHAMBER -> 13;case MIXING_BASIN -> 14;default -> 16;};
        if(s.suspended)source=16-source;
        float x=8,y=source,z=8,X=s.targetX*16,Y=s.targetY*16,Z=s.targetZ*16;
        X=x+(X-x)*s.drive;Y=y+(Y-y)*s.drive;Z=z+(Z-z)*s.drive;
        float length=(float)Math.sqrt((X-x)*(X-x)+(Y-y)*(Y-y)+(Z-z)*(Z-z));
        if(!Float.isFinite(length)||length>192)return;
        float shortBreath=length<6?.85F+.15F*(float)Math.sin(s.clock*Math.PI*2/60):1;
        m.beam(x,y,z,X,Y,Z,1,1,UiPalette.multiply(accent,(.45F+.55F*s.drive)*shortBreath),light);
        if(length<6)return;
        float shift=switch(s.kind){case REACTION_CHAMBER -> .5F;case SEPARATION_UNIT -> 1F/3;case MIXING_BASIN -> 2F/3;default -> 0;};
        for(int i=0;i<2;i++){
            float t=(float)((s.turns/32+shift+i*.5)%1),fade=WorkEnvelope.ease(t/.125)*WorkEnvelope.ease((1-t)/.125);
            if(s.kind==IndustrialModuleKind.SEPARATION_UNIT)t=1-t;
            float px=x+(X-x)*t,py=y+(Y-y)*t,pz=z+(Z-z)*t,w=.75F*fade;
            m.box(px-w,py-w,pz-w,px+w,py+w,pz+w,1,UiPalette.modulePulse(s.kind),light);
        }
    }
    private static void localBox(IndustrialModuleRenderState s,float x,float y,float z,float X,float Y,float Z,int sprite,int color,int light,int faces,Quaternionf rotation,float px,float py,float pz){
        if(s.suspended){float low=16-Y;Y=16-y;y=low;py=16-py;
            int up=faces&MovingBoxes.UP,down=faces&MovingBoxes.DOWN;faces&=~(MovingBoxes.UP|MovingBoxes.DOWN);if(up!=0)faces|=MovingBoxes.DOWN;if(down!=0)faces|=MovingBoxes.UP;}
        s.moving.box(x,y,z,X,Y,Z,sprite,faces,color,light,px,py,pz,rotation);
    }
    @Override public void submit(IndustrialModuleRenderState s,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5F,0,.5F);pose.mulPose(Axis.YP.rotationDegrees(180-s.facing.toYRot()));pose.translate(-.5F,0,-.5F);
        collector.submitCustomGeometry(pose,RenderTypes.solidMovingBlock(),s.moving);pose.popPose();
    }
}
