package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.client.anim.AnimClock;
import dev.latrox.rematter.client.anim.CrankMotion;
import dev.latrox.rematter.client.anim.MovingBoxes;
import dev.latrox.rematter.client.anim.RendererParts;
import dev.latrox.rematter.client.anim.WorkEnvelope;
import dev.latrox.rematter.content.*;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/** Block-atlas mechanisms driven by one monotonic client clock, independent of recipe batch boundaries. */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity,MachineRenderState> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter","mechanisms"),"main");
    private static final String[] SPRITES={"bench_metal","entity/effect_light","chemical_reactor_columns_liquid","chemical_reactor_columns_liquid_active","chemical_reactor_columns_glass","chemical_reactor_columns_shuttle"};
    private static final int METAL=0,MASK=1,LIQUID=2,LIQUID_ACTIVE=3,GLASS=4,SHUTTLE=5;
    public enum WaitingLight { BLUE_BREATH, OFF }
    public static final WaitingLight WAITING_LIGHT=WaitingLight.BLUE_BREATH;
    public enum IndustrialField { GIMBAL, PLATES }
    public static final IndustrialField INDUSTRIAL_FIELD=IndustrialField.GIMBAL;
    public enum ScanHead { RING, SHEET }
    public static final ScanHead SCAN_HEAD=ScanHead.RING;
    private final Map<MachineBlockEntity,Motion> motions=new WeakHashMap<>();
    private final ItemModelResolver resolver;
    private final SpriteGetter spriteGetter;
    private @Nullable SpriteUv atlas,driveAtlas;
    private final BlockPos.MutableBlockPos front=new BlockPos.MutableBlockPos();
    public MachineRenderer(BlockEntityRendererProvider.Context context){resolver=context.itemModelResolver();spriteGetter=context.sprites();}
    public static LayerDefinition createLayer(){return LayerDefinition.create(new MeshDefinition(),16,16);}
    enum Detail {
        NONE, ITEMS, CELLS, MECHANISM;
        static Detail of(MachineKind kind){return switch(kind){
            case TERMINAL,NETWORK_INTERFACE,PRIMAL_CONVERTER,CRYSTAL_GENERATOR,INDUSTRIAL_CRYSTAL_GENERATOR -> NONE;
            case REACTOR -> MECHANISM;
            case DRIVE -> CELLS;
            default -> ITEMS;
        };}
    }
    @Override public MachineRenderState createRenderState(){return new MachineRenderState();}
    @Override public int getViewDistance(){return 64;}
    @Override public boolean shouldRender(MachineBlockEntity machine,Vec3 camera){
        var detail=Detail.of(machine.kind());
        if(detail==Detail.NONE||IndustrialStructure.isIndustrial(machine.kind())&&!machine.industrialFormed())return false;
        if(detail==Detail.CELLS&&machine.visibleCellMask()==0)return false;
        return BlockEntityRenderer.super.shouldRender(machine,camera);
    }
    @Override public AABB getRenderBoundingBox(MachineBlockEntity machine){
        if(!IndustrialStructure.isIndustrial(machine.kind())||!machine.industrialFormed())return new AABB(machine.getBlockPos());
        var facing=machine.getBlockState().getValue(MachineBlock.FACING);
        var first=machine.getBlockPos().relative(facing.getClockWise(),-machine.industrialControllerColumn()).below();
        var last=first.relative(facing.getClockWise(),machine.industrialWidth()-1).relative(facing.getOpposite(),machine.industrialDepth()-1).above(machine.industrialHeight()-1);
        return new AABB(Math.min(first.getX(),last.getX()),Math.min(first.getY(),last.getY()),Math.min(first.getZ(),last.getZ()),Math.max(first.getX(),last.getX())+1,Math.max(first.getY(),last.getY())+1,Math.max(first.getZ(),last.getZ())+1);
    }
    @Override public void extractRenderState(MachineBlockEntity machine,MachineRenderState state,float partialTick,Vec3 cameraPosition,ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress){
        BlockEntityRenderer.super.extractRenderState(machine,state,partialTick,cameraPosition,breakProgress);
        state.kind=machine.kind();state.facing=machine.getBlockState().getValue(MachineBlock.FACING);state.active=machine.getBlockState().getValue(MachineBlock.ACTIVE);
        if(machine.getLevel() instanceof BlockAndTintGetter view)state.cardinal=view.cardinalLighting();
        state.sprites=null;state.moving.clear();
        var detail=Detail.of(state.kind);
        if(detail==Detail.CELLS){
            state.cellMask=machine.visibleCellMask();int tiers=0,statuses=0;
            for(int bay=0;bay<MachineCellAccess.DRIVE_BAYS;bay++){tiers|=Math.clamp(machine.visibleCellTier(bay),0,7)<<bay*3;statuses|=machine.visibleCellStatus(bay).ordinal()<<bay*3;}
            state.cellTiers=tiers;state.cellStatuses=statuses;
            state.frontLight=machine.getLevel()==null?state.lightCoords:LevelRenderer.getLightCoords(machine.getLevel(),front.setWithOffset(machine.getBlockPos(),state.facing));
            if(driveAtlas==null)driveAtlas=SpriteUv.resolve(spriteGetter,DriveBays.SPRITES);state.sprites=driveAtlas;return;
        }
        if(detail==Detail.NONE)return;
        state.clock=AnimClock.now(partialTick);state.hasProcess=machine.visualDuration()>0;
        var motion=motions.computeIfAbsent(machine,key->new Motion());
        double base=state.kind==MachineKind.INDUSTRIAL_REACTOR?2:state.kind==MachineKind.INDUSTRIAL_PREPARATOR?4:1;
        motion.work.sample(state.clock,state.active,state.hasProcess,machine.visualProgress(),machine.visualDuration(),base);
        state.phase=motion.work.phase(period(state.kind));state.turns=motion.work.turns();state.drive=motion.work.drive();state.engage=motion.work.engage();state.waiting=motion.work.waiting();state.progress=motion.work.progress();
        state.preparationStage=machine.preparationStage();state.preparationMixing=machine.preparationMixing();
        state.industrialWidth=machine.industrialWidth();state.industrialHeight=machine.industrialHeight();state.industrialDepth=machine.industrialDepth();state.industrialColumn=machine.industrialControllerColumn();
        if(IndustrialStructure.isIndustrial(state.kind)&&state.industrialWidth>0&&machine.getLevel()!=null){
            // Chamber hardware and its product are lit where they stand, not inside the opaque controller.
            var right=state.facing.getClockWise();var back=state.facing.getOpposite();var position=machine.getBlockPos();
            double x=state.industrialWidth/2.0-state.industrialColumn-.5,z=state.industrialDepth/2.0-.5;
            var focus=BlockPos.containing(position.getX()+.5+right.getStepX()*x+back.getStepX()*z,
                    position.getY()+state.industrialHeight/2.0-1,
                    position.getZ()+.5+right.getStepZ()*x+back.getStepZ()*z);
            state.lightCoords=LevelRenderer.getLightCoords(machine.getLevel(),focus);
        }
        if(atlas==null)atlas=SpriteUv.resolve(spriteGetter,SPRITES);
        state.moving.sprites=atlas;state.moving.cardinal=state.cardinal;state.moving.facing=state.facing;
        if(state.kind==MachineKind.HAND_PREPARATOR){
            state.handAngle=motion.crank.sample(state.clock,machine.visualCrankSequence());
        }
        geometry(state);
        if(detail!=Detail.ITEMS)return;
        int seed=(int)machine.getBlockPos().asLong();
        ItemStack[] items={machine.visibleInputItem(),machine.visibleSecondaryItem(),machine.visibleTertiaryItem(),machine.visibleOutputItem(),machine.visibleProcessInputItem(),machine.visibleProcessOutputItem()};
        ItemStackRenderState[] rendered={state.inputItem,state.secondaryItem,state.tertiaryItem,state.outputItem,state.processInputItem,state.processOutputItem};
        for(int i=0;i<items.length;i++){
            var fade=motion.items[i];fade.sample(state.clock,items[i]);state.itemScales[i]=fade.scale;
            resolver.updateForTopItem(rendered[i],fade.shown,ItemDisplayContext.FIXED,machine.getLevel(),null,seed+i);
        }
    }
    private static double period(MachineKind kind){return switch(kind){case REACTOR -> 80;case ANALYZER -> 48;case PREPARATOR -> 24;case DECOMPOSER -> 32;case SYNTHESIZER -> 40;default -> 64;};}
    private static void geometry(MachineRenderState s){
        var m=s.moving;float e=s.engage,p=s.phase,d=s.drive;
        int white=UiPalette.WHITE,lit=MovingBoxes.light(s.lightCoords,d),accent=UiPalette.machineAccent(s.kind);
        switch(s.kind){
            case REACTOR -> {
                if(!RendererParts.REACTOR_LIQUID_RENDERER)break;
                float flow=(float)Math.sin(p*Math.PI*2)*e;
                for(int side=0;side<2;side++){
                    var liquid=side==0?RendererParts.LIQUID_RIGHT:RendererParts.LIQUID_LEFT;
                    float top=9+(side==0?flow:-flow);
                    m.reactor(liquid.x0(),7,liquid.z0(),liquid.x1(),top,liquid.z1(),d>.001F?LIQUID_ACTIVE:LIQUID,UiPalette.brightness(d>.001F?.62F+.38F*d:1),lit,d);
                    m.reactor(liquid.x0(),top,liquid.z0(),liquid.x1(),10,liquid.z1(),GLASS,white,s.lightCoords,0);
                }
                add(m,RendererParts.SHUTTLE,-3*flow,0,0,SHUTTLE,UiPalette.brightness(.62F+.38F*d),lit);
            }
            case DECOMPOSER -> {
                float bite=bite(p)*e;
                var r=RendererParts.RAM_RIGHT;var l=RendererParts.RAM_LEFT;
                m.box(r.x0(),r.y0(),r.z0(),r.x1()+bite,r.y1(),r.z1(),METAL,MovingBoxes.ALL^MovingBoxes.WEST,white,s.lightCoords,0,0,0,null);
                m.box(l.x0()-bite,l.y0(),l.z0(),l.x1(),l.y1(),l.z1(),METAL,MovingBoxes.ALL^MovingBoxes.EAST,white,s.lightCoords,0,0,0,null);
            }
            case ANALYZER -> {
                float y=-4*(.5F-.5F*(float)Math.cos(p*Math.PI*2))*e;
                int tint=UiPalette.tint(accent,.35F);
                if(SCAN_HEAD==ScanHead.SHEET)m.box(5,10.5F+y,3.5F,11,11.5F+y,7.5F,MASK,tint,lit);
                else for(var a:new RendererParts.Part[]{RendererParts.SCAN_FRONT,RendererParts.SCAN_BACK,RendererParts.SCAN_RIGHT,RendererParts.SCAN_LEFT})add(m,a,0,y,0,MASK,tint,lit);
            }
            case PREPARATOR -> {
                var a=RendererParts.PESTLE;
                float down=s.preparationMixing?0:1.5F*press(p)*e;
                m.box(a.x0(),a.y0()-down,a.z0(),a.x1(),a.y1(),a.z1(),METAL,MovingBoxes.ALL^MovingBoxes.UP,white,s.lightCoords,0,0,0,null);
                if(s.preparationMixing&&e>.001F){var b=RendererParts.MIX_CHARGE;m.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y0()+1.5F*e,b.z1(),MASK,UiPalette.multiply(accent,.62F+.38F*d),lit);}
            }
            case SYNTHESIZER -> {
                float h=3*(.15F+.85F*s.progress)*e;
                beam(s,8,10.5F,7.5F,8,6.3F+h,5.5F,e,accent,20);
            }
            case HAND_PREPARATOR -> {
                var q=new Quaternionf().rotationZ((float)Math.toRadians(s.handAngle));
                for(var a:new RendererParts.Part[]{RendererParts.HAND_ARM,RendererParts.HAND_GRIP})m.box(a.x0(),a.y0(),a.z0(),a.x1(),a.y1(),a.z1(),METAL,MovingBoxes.ALL,white,s.lightCoords,10.5F,11.5F,2.5F,q);
            }
            case INDUSTRIAL_REACTOR,INDUSTRIAL_PREPARATOR -> field(s);
            default -> { }
        }
        if(s.kind!=MachineKind.INDUSTRIAL_REACTOR&&s.kind!=MachineKind.INDUSTRIAL_PREPARATOR){
            var lamp=RendererParts.STATUS;float breath=.8F+.2F*(float)Math.sin(s.clock*Math.PI*2/60);
            int color=UiPalette.mix(UiPalette.multiply(UiPalette.WAITING_LIGHT,s.waiting*breath*(WAITING_LIGHT==WaitingLight.BLUE_BREATH?1:0)),accent,d);
            m.box(lamp.x0(),lamp.y0(),lamp.z0(),lamp.x1(),lamp.y1(),lamp.z1(),MASK,MovingBoxes.ALL^MovingBoxes.SOUTH,color,MovingBoxes.light(s.lightCoords,Math.max(d,s.waiting)),0,0,0,null);
        }
    }
    private static void field(MachineRenderState s){
        int interior=s.industrialHeight-2;if(interior<2)return;
        boolean odd=(s.industrialWidth&1)==1&&(s.industrialDepth&1)==1;
        float x=(s.industrialWidth/2F-s.industrialColumn)*16,y=(s.industrialHeight/2F-1)*16,z=s.industrialDepth/2F*16;
        float scale=interior>=3?Math.clamp(Math.min(Math.min(s.industrialWidth-2,s.industrialDepth-2),s.industrialHeight-4),1,2.5F):1;
        boolean reactor=s.kind==MachineKind.INDUSTRIAL_REACTOR;
        if(INDUSTRIAL_FIELD==IndustrialField.PLATES){
            if(interior<3)return;
            for(int i=0;i<4;i++){float p=(float)((s.turns/64+i*.25)%1),a=WorkEnvelope.ease(p/.15)*WorkEnvelope.ease((1-p)/.15)*s.engage;
                if(a>.001F)s.moving.box(x-2.5F*a,y-5+10*p,z-2.5F*a,x+2.5F*a,y-4+10*p,z+2.5F*a,MASK,UiPalette.machineAccent(s.kind),MovingBoxes.light(s.lightCoords,s.drive));}
            return;
        }
        if(interior>=3||odd)ring(s,x,y,z,scale,false,reactor?(interior>=3?25:15):(interior>=3?10:0),(float)((s.turns*1.8)%360));
        ring(s,x,y,z,scale,true,reactor&&interior>=3?50:0,(float)((s.turns*-3)%360));
        if(reactor&&interior>=3){var a=RendererParts.FIELD_CORE;
            s.moving.box(x+a.x0()*scale,y+a.y0()*scale,z+a.z0()*scale,x+a.x1()*scale,y+a.y1()*scale,z+a.z1()*scale,MASK,MovingBoxes.ALL,UiPalette.multiply(UiPalette.tint(UiPalette.VIOLET,.45F),.5F+.5F*s.drive),MovingBoxes.light(s.lightCoords,s.drive),x,y,z,new Quaternionf().rotationZYX((float)Math.PI/4,(float)Math.PI/4,0));}
    }
    private static void ring(MachineRenderState s,float x,float y,float z,float scale,boolean inner,float tilt,float yaw){
        var parts=inner?new RendererParts.Part[]{RendererParts.FIELD_INNER_FRONT,RendererParts.FIELD_INNER_BACK,RendererParts.FIELD_INNER_RIGHT,RendererParts.FIELD_INNER_LEFT}:new RendererParts.Part[]{RendererParts.FIELD_OUTER_FRONT,RendererParts.FIELD_OUTER_BACK,RendererParts.FIELD_OUTER_RIGHT,RendererParts.FIELD_OUTER_LEFT};
        var q=new Quaternionf();if(inner)q.rotateX((float)Math.toRadians(tilt));else q.rotateZ((float)Math.toRadians(tilt));q.rotateY((float)Math.toRadians(yaw));
        for(var a:parts)s.moving.box(x+a.x0()*scale,y+a.y0()*scale,z+a.z0()*scale,x+a.x1()*scale,y+a.y1()*scale,z+a.z1()*scale,MASK,MovingBoxes.ALL,inner?UiPalette.multiply(UiPalette.machineAccent(s.kind),.5F+.5F*s.drive):UiPalette.MOTION_STEEL,inner?MovingBoxes.light(s.lightCoords,s.drive):s.lightCoords,x,y,z,q);
    }
    private static void beam(MachineRenderState s,float x,float y,float z,float X,float Y,float Z,float extent,int color,double period){
        if(extent<.001F)return;float d=s.drive;
        X=x+(X-x)*extent;Y=y+(Y-y)*extent;Z=z+(Z-z)*extent;
        s.moving.beam(x,y,z,X,Y,Z,1,MASK,UiPalette.multiply(color,.5F+.5F*d),MovingBoxes.light(s.lightCoords,d));
        for(int i=0;i<2;i++){
            float t=(float)((s.turns/period+i*.5)%1),fade=WorkEnvelope.ease(t/.125)*WorkEnvelope.ease((1-t)/.125);
            float px=x+(X-x)*t,py=y+(Y-y)*t,pz=z+(Z-z)*t,w=.75F*fade;
            s.moving.box(px-w,py-w,pz-w,px+w,py+w,pz+w,MASK,UiPalette.tint(color,.45F),MovingBoxes.light(s.lightCoords,d));
        }
    }
    private static void add(MovingBoxes m,RendererParts.Part p,float dx,float dy,float dz,int sprite,int color,int light){m.box(p.x0()+dx,p.y0()+dy,p.z0()+dz,p.x1()+dx,p.y1()+dy,p.z1()+dz,sprite,color,light);}
    public static float bite(float p){return p<.3F?WorkEnvelope.ease(p/.3):p<.4F?1:p<.75F?1-WorkEnvelope.ease((p-.4)/.35):0;}
    public static float press(float p){return p<.3F?(p/.3F)*(p/.3F):p<.4F?1:p<.8F?1-WorkEnvelope.ease((p-.4)/.4):0;}
    @Override public void submit(MachineRenderState s,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5F,0,.5F);pose.mulPose(Axis.YP.rotationDegrees(180-s.facing.toYRot()));pose.translate(-.5F,0,-.5F);
        float p=s.progress,e=s.engage;
        var source=s.processInputItem.isEmpty()?s.inputItem:s.processInputItem;
        switch(s.kind){
            case HAND_PREPARATOR -> {floatingItem(source,6,6.4F,5,2.5F*s.itemScales[s.processInputItem.isEmpty()?0:4],0,s,pose,collector);item(s.outputItem,6,8.15F,2.6F,4*s.itemScales[3],1.5F,2.5F,s,pose,collector);}
            case PREPARATOR -> {
                if(s.preparationMixing){floatingItem(source,11,10.2F-1.2F*p,5,2.4F*(1-.5F*p)*s.itemScales[4],0,s,pose,collector);floatingItem(s.secondaryItem,10,10.7F,5,1.3F*s.itemScales[1],0,s,pose,collector);}
                else item(source,5,6.05F,5.5F,2.4F*s.itemScales[s.processInputItem.isEmpty()?0:4],Math.min(2.4F,2.45F-1.5F*press(s.phase)*e),1.8F,s,pose,collector);
                item(s.outputItem,8,6.15F,2.5F,3.5F*s.itemScales[3],1.5F,2.3F,s,pose,collector);
            }
            case ANALYZER -> fitted(s.inputItem,8,8.5F,5.5F,4.2F,4.2F,1.8F,s.itemScales[0],s,pose,collector);
            case DECOMPOSER -> {
                float size=3*(1-.6F*p),gap=3-2*bite(s.phase)*e;
                fitted(source,8,9.5F,5.5F,Math.min(size,gap),size,2.5F,s.itemScales[s.processInputItem.isEmpty()?0:4],s,pose,collector);
            }
            case SYNTHESIZER -> {
                float h=3*(.15F+.85F*p)*e;
                fitted(s.processOutputItem,8,6+h/2,5.5F,3,h,2.5F,s.itemScales[5],s,pose,collector);
                item(s.outputItem,8,6.15F,2.5F,5*s.itemScales[3],2,2.7F,s,pose,collector);
            }
            case SINTER_FURNACE -> {
                float size=3*(1-WorkEnvelope.ease((p-.85F)/.15F));
                floatingItem(source,8,8.6F+.25F*(float)Math.sin(s.turns*Math.PI*2/64)*e,5.5F,size*s.itemScales[s.processInputItem.isEmpty()?0:4],0,s,pose,collector);
                item(s.outputItem,8,6.15F,2.5F,5*s.itemScales[3],1.8F,2.5F,s,pose,collector);
            }
            case INDUSTRIAL_REACTOR,INDUSTRIAL_PREPARATOR -> {
                if(s.industrialHeight>=5){float x=(s.industrialWidth/2F-s.industrialColumn)*16,y=(s.industrialHeight/2F-1)*16,z=s.industrialDepth/2F*16;
                    float productSize=4,productHeight=3;
                    if(s.kind==MachineKind.INDUSTRIAL_REACTOR){
                        float fieldScale=Math.clamp(Math.min(Math.min(s.industrialWidth-2,s.industrialDepth-2),s.industrialHeight-4),1,2.5F);
                        productSize=Math.min(4,2*fieldScale);
                        // A tilted core fits inside this sphere; reserve the full bob and half a pixel of air.
                        productHeight=(float)Math.sqrt(3)*fieldScale+productSize/2+1;
                    }
                    floatingItem(s.processOutputItem.isEmpty()?source:s.processOutputItem,x,y+productHeight+.5F*(float)Math.sin(s.turns*Math.PI*2/64)*e,z,productSize*e,0,s,pose,collector);}
            }
            default -> { }
        }
        if(s.sprites!=null)collector.submitCustomGeometry(pose,RenderTypes.solidMovingBlock(),s);
        if(!s.moving.boxes().isEmpty())collector.submitCustomGeometry(pose,RenderTypes.solidMovingBlock(),s.moving);
        pose.popPose();
    }
    private static void fitted(ItemStackRenderState item,float x,float y,float z,float width,float height,float depth,float amount,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector){
        if(item.isEmpty()||!Float.isFinite(amount)||!Float.isFinite(width)||!Float.isFinite(height)||!Float.isFinite(depth)||amount<.001F||width<.001F||height<.001F||depth<.001F)return;var b=item.getModelBoundingBox();
        if(!Double.isFinite(b.minX)||!Double.isFinite(b.minY)||!Double.isFinite(b.minZ)||!Double.isFinite(b.maxX)||!Double.isFinite(b.maxY)||!Double.isFinite(b.maxZ)||b.getXsize()<=0||b.getYsize()<=0||b.getZsize()<=0)return;
        double scale=Math.min(width/Math.max(.00001,b.getXsize()),Math.min(height/Math.max(.00001,b.getYsize()),depth/Math.max(.00001,b.getZsize())))*amount/16;
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.scale((float)scale,(float)scale,(float)scale);pose.translate(-(b.minX+b.maxX)/2,-(b.minY+b.maxY)/2,-(b.minZ+b.maxZ)/2);
        item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    private static void floatingItem(ItemStackRenderState item,float x,float y,float z,float size,float yaw,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector){fitted(item,x,y,z,size,size,size,1,state,pose,collector);}
    private static final class Motion {
        final WorkEnvelope work=new WorkEnvelope();final ShownItem[] items={new ShownItem(),new ShownItem(),new ShownItem(),new ShownItem(),new ShownItem(),new ShownItem()};
        final CrankMotion crank=new CrankMotion();
    }

    private static final class ShownItem {
        ItemStack shown=ItemStack.EMPTY,pending=ItemStack.EMPTY;float scale;double last=Double.NaN;
        void sample(double time,ItemStack item){
            double dt=Double.isNaN(last)?0:Math.clamp(time-last,0,5);last=time;
            pending=item;
            if(!ItemStack.isSameItemSameComponents(shown,pending)){
                scale=Math.max(0,scale-(float)dt/4);
                if(scale==0)shown=pending.copy();
            }else scale=Math.min(1,scale+(float)dt/6);
        }
    }
    /** Fit the resolved model, including its native fixed transform, onto a work surface. */
    private static void item(ItemStackRenderState item, float x, float surfaceY, float z,
            float width, float height, float depth, MachineRenderState state,
            PoseStack pose, SubmitNodeCollector collector) {
        if (item.isEmpty() || !Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(depth) || width < .001F || height < .001F || depth < .001F) return;
        var bounds = item.getModelBoundingBox();
        // Laying the native fixed model flat turns local Z into vertical height.
        double sizeX = bounds.getXsize(), sizeY = bounds.getZsize(), sizeZ = bounds.getYsize();
        double extent = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (!Double.isFinite(extent) || extent <= 0 || !Double.isFinite(bounds.minX)
                || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.maxZ)) return;
        double scale = Math.min(width / Math.max(sizeX, 0.00001),
                Math.min(height / Math.max(sizeY, 0.00001), depth / Math.max(sizeZ, 0.00001))) / 16;
        pose.pushPose();
        pose.translate(x / 16, surfaceY / 16, z / 16);
        pose.scale((float) scale, (float) scale, (float) scale);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.translate(-(bounds.minX + sizeX / 2), -(bounds.minY + sizeZ / 2), -bounds.maxZ);
        item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

}
