package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineCellAccess;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.IndustrialStructure;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Moving parts, shown items and mounted cells. Every working pose comes from server-reported process progress,
 * every indicator from the coarse state the server publishes.
 */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity, MachineRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter", "mechanisms"), "main");
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/effect_light.png");
    private static final Identifier CRYSTAL = Identifier.fromNamespaceAndPath("rematter", "textures/block/effect_violet.png");
    private static final Map<MachineKind,Identifier> CRYSTALS = familyTextures("block");
    private static final Map<MachineKind,Identifier> SIGNALS = familyTextures("block");
    private static final Identifier BENCH_METAL = Identifier.fromNamespaceAndPath("rematter", "textures/block/bench_metal.png");
    private static final Identifier BENCH_SCAN = Identifier.fromNamespaceAndPath("rematter", "textures/block/bench_analyzer_work_active.png");
    private static final Identifier CONNECTOR_BODY = Identifier.fromNamespaceAndPath("rematter", "textures/block/casing_plain.png");
    private static final Identifier CONNECTOR_METAL = Identifier.fromNamespaceAndPath("rematter", "textures/block/bench_metal.png");
    private static final int CELL_VIEW_DISTANCE = 64;
    private final Map<MachineBlockEntity,ProcessVisualMotion> motion=new WeakHashMap<>();
    private final ItemModelResolver itemModelResolver;
    private final SpriteGetter sprites;
    private final ModelPart core, halo, trace, beam, mote, handCrank, handGrip;
    private final ModelPart scanStripe;
    private final BlockPos.MutableBlockPos front = new BlockPos.MutableBlockPos();
    private @Nullable SpriteUv driveSprites, portSprites;

    public MachineRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
        // The atlas is stitched after renderers may be constructed; sprites are looked up on first use.
        sprites = context.sprites();
        ModelPart root = context.bakeLayer(LAYER);
        core=root.getChild("core"); halo=root.getChild("halo"); trace=root.getChild("trace");
        beam=root.getChild("beam"); mote=root.getChild("mote");
        scanStripe=root.getChild("bench_scan");
        handCrank=root.getChild("hand_crank"); handGrip=root.getChild("hand_grip");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var crystal=root.addOrReplaceChild("core",CubeListBuilder.create().texOffs(0,0)
                .addBox(-1.3F,-1.5F,-1.3F,2.6F,3,2.6F)
                .addBox(-.52F,-2.5F,-.52F,1.04F,5,1.04F),PartPose.ZERO);
        crystal.addOrReplaceChild("cut_facets",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.936F,-1.9F,-.936F,1.872F,3.8F,1.872F),PartPose.rotation(0,Mth.PI/4,0));
        var ring=root.addOrReplaceChild("halo",CubeListBuilder.create(),PartPose.ZERO);
        var glow=root.addOrReplaceChild("trace",CubeListBuilder.create(),PartPose.ZERO);
        for(int i=0;i<8;i++) {
            float angle=i*Mth.PI/4;
            ring.addOrReplaceChild("segment_"+i,CubeListBuilder.create().texOffs(0,0)
                    .addBox(-1.45F,-.2F,3.5F,2.9F,.4F,.55F),PartPose.rotation(0,angle,0));
            glow.addOrReplaceChild("segment_"+i,CubeListBuilder.create().texOffs(0,0)
                    .addBox(-.75F,-.06F,3.39F,1.5F,.12F,.15F),PartPose.rotation(0,angle,0));
        }
        root.addOrReplaceChild("beam",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.095F,0,-.095F,.19F,1,.19F),PartPose.ZERO);
        root.addOrReplaceChild("mote",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.22F,-.22F,-.22F,.44F,.44F,.44F),PartPose.ZERO);
        // The only renderer-owned bench hardware: one scan bar and one bounded physical crank.
        root.addOrReplaceChild("bench_scan",CubeListBuilder.create().texOffs(0,0)
                .addBox(-4,-.25F,-.5F,8,.5F,1),PartPose.ZERO);
        root.addOrReplaceChild("hand_crank",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.5F,-.5F,-.5F,1,2.5F,1),PartPose.ZERO);
        root.addOrReplaceChild("hand_grip",CubeListBuilder.create().texOffs(0,0)
                .addBox(-1,2,-1,2,1,1),PartPose.ZERO);
        return LayerDefinition.create(mesh,32,32);
    }

    /** The extracted state that a kind's visuals read. Everything else is never extracted. */
    enum Detail {
        NONE, ITEMS, CELLS;

        static Detail of(MachineKind kind) {
            return switch (kind) {
                case TERMINAL, NETWORK_INTERFACE, REACTOR, PRIMAL_CONVERTER, CRYSTAL_GENERATOR, INDUSTRIAL_CRYSTAL_GENERATOR -> NONE;
                case DRIVE -> CELLS;
                case ANALYZER, DECOMPOSER, SYNTHESIZER, PREPARATOR, HAND_PREPARATOR, SINTER_FURNACE,
                        INDUSTRIAL_PREPARATOR, INDUSTRIAL_REACTOR -> ITEMS;
            };
        }
    }

    @Override public MachineRenderState createRenderState() { return new MachineRenderState(); }
    @Override public boolean shouldRender(MachineBlockEntity machine,Vec3 camera) {
        if(ConfigurablePorts.supports(machine.kind()))
            return Vec3.atCenterOf(machine.getBlockPos()).closerThan(camera,CELL_VIEW_DISTANCE);
        var detail=Detail.of(machine.kind());
        if(detail==Detail.NONE||IndustrialStructure.isIndustrial(machine.kind())&&!machine.industrialFormed())return false;
        if(detail!=Detail.CELLS)return BlockEntityRenderer.super.shouldRender(machine,camera);
        // Cell-only renderers can skip empty bays; configurable machines also draw their port rings above.
        return machine.visibleCellMask()!=0&&Vec3.atCenterOf(machine.getBlockPos()).closerThan(camera,CELL_VIEW_DISTANCE);
    }
    @Override public AABB getRenderBoundingBox(MachineBlockEntity machine) {
        if(ConfigurablePorts.supports(machine.kind()))return new AABB(machine.getBlockPos()).inflate(.5/16);
        if(!IndustrialStructure.isIndustrial(machine.kind()) || !machine.industrialFormed()) return new AABB(machine.getBlockPos());
        var facing=machine.getBlockState().getValue(MachineBlock.FACING);
        var first=machine.getBlockPos().relative(facing.getClockWise(),-machine.industrialControllerColumn()).below();
        var last=first.relative(facing.getClockWise(),machine.industrialWidth()-1)
                .relative(facing.getOpposite(),machine.industrialDepth()-1).above(machine.industrialHeight()-1);
        return new AABB(Math.min(first.getX(),last.getX()),Math.min(first.getY(),last.getY()),Math.min(first.getZ(),last.getZ()),
                Math.max(first.getX(),last.getX())+1,Math.max(first.getY(),last.getY())+1,Math.max(first.getZ(),last.getZ())+1);
    }
    @Override public int getViewDistance() { return 48; }
    @Override public void extractRenderState(MachineBlockEntity machine,MachineRenderState state,float partialTick,
            Vec3 cameraPosition,ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine,state,partialTick,cameraPosition,breakProgress);
        state.kind=machine.kind(); state.facing=machine.getBlockState().getValue(MachineBlock.FACING);
        state.active=machine.getBlockState().getValue(MachineBlock.ACTIVE);
        var detail=Detail.of(state.kind);
        if(machine.getLevel() instanceof BlockAndTintGetter view)state.cardinal=view.cardinalLighting();
        state.sprites=null;state.portSprites=null;state.portModes=0;
        if(ConfigurablePorts.supports(state.kind)) {
            if(portSprites==null)portSprites=SpriteUv.resolve(sprites,ConfigurablePorts.SPRITES);
            state.portSprites=portSprites;
            for(int index=0;index<ConfigurablePorts.SIDES.length;index++) {
                var side=ConfigurablePorts.SIDES[index];
                state.portModes|=machine.portMode(side).ordinal()<<(4*side.ordinal());
                state.portLights[index]=machine.getLevel()==null?state.lightCoords:LevelRenderer.getLightCoords(
                        machine.getLevel(),front.setWithOffset(machine.getBlockPos(),side.world(state.facing)));
            }
        }
        if(detail==Detail.CELLS) {
            state.cellMask=machine.visibleCellMask();
            // Assigned whole, never accumulated: a reused render state must not keep bits of an earlier cell.
            int tiers=0,statuses=0;
            for(int bay=0;bay<MachineCellAccess.DRIVE_BAYS;bay++) {
                tiers|=Math.clamp(machine.visibleCellTier(bay),0,7)<<bay*3;
                statuses|=machine.visibleCellStatus(bay).ordinal()<<bay*3;
            }
            state.cellTiers=tiers;state.cellStatuses=statuses;
            // The bays are recessed into a closed cube; like the faces around them they are lit from in front.
            state.frontLight=machine.getLevel()==null?state.lightCoords
                    :LevelRenderer.getLightCoords(machine.getLevel(),front.setWithOffset(machine.getBlockPos(),state.facing));
            if(driveSprites==null)driveSprites=SpriteUv.resolve(sprites,DriveBays.SPRITES);
            state.sprites=driveSprites;
        }
        if(detail!=Detail.ITEMS)return;
        state.preparationStage=machine.preparationStage();state.preparationMixing=machine.preparationMixing();
        double time=machine.getLevel()==null?0:machine.getLevel().getGameTime()+(double)partialTick;
        state.phase=motion.computeIfAbsent(machine,key->new ProcessVisualMotion()).sample(time,machine.visualProgress(),machine.visualDuration(),state.active);
        state.hasProcess=machine.visualDuration()>0;
        state.drive=state.active?1:state.hasProcess?.25F:0;
        state.industrialWidth=machine.industrialWidth();state.industrialHeight=machine.industrialHeight();
        state.industrialDepth=machine.industrialDepth();state.industrialColumn=machine.industrialControllerColumn();
        if(detail!=Detail.ITEMS)return;
        int seed=(int)machine.getBlockPos().asLong();
        itemModelResolver.updateForTopItem(state.inputItem,machine.visibleInputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed);
        itemModelResolver.updateForTopItem(state.secondaryItem,machine.visibleSecondaryItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+3);
        itemModelResolver.updateForTopItem(state.tertiaryItem,machine.visibleTertiaryItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+4);
        itemModelResolver.updateForTopItem(state.outputItem,machine.visibleOutputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+1);
        itemModelResolver.updateForTopItem(state.processInputItem,machine.visibleProcessInputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+5);
        itemModelResolver.updateForTopItem(state.processOutputItem,machine.visibleProcessOutputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+6);
    }

    @Override public void submit(MachineRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose();pose.translate(.5F,0,.5F);pose.mulPose(Axis.YP.rotationDegrees(180-state.facing.toYRot()));pose.translate(-.5F,0,-.5F);
        float p=state.phase;
        switch(state.kind) {
            case HAND_PREPARATOR -> {
                pose.pushPose();pose.translate(10.5F/16,11.5F/16,2.5F/16);pose.mulPose(Axis.ZP.rotationDegrees(p*720));
                part(handCrank,BENCH_METAL,false,1,state,pose,collector);part(handGrip,BENCH_METAL,false,1,state,pose,collector);pose.popPose();
                benchItems(6,9.5F,5,3,state,pose,collector);
                item(state.outputItem,6,8.15F,2.6F,4,1.5F,2.5F,state,pose,collector);
            }
            case PREPARATOR -> {
                if(state.preparationMixing)benchItems(10.5F,10.5F,5,2.7F,state,pose,collector);
                else benchItems(5,7.5F,5.5F,2.8F,state,pose,collector);
                item(state.outputItem,8,6.15F,2.5F,3.5F,1.5F,2.3F,state,pose,collector);
            }
            case ANALYZER -> {
                floatingItem(state.inputItem,8,8.5F,5.5F,4.2F,0,state,pose,collector);
                if(state.hasProcess){
                    pose.pushPose();pose.translate(.5F,(6.75F+4.5F*ease(p))/16,2F/16);
                    part(scanStripe,BENCH_SCAN,true,.8F,state,pose,collector);pose.popPose();
                }
            }
            case DECOMPOSER -> benchItems(8,8.8F,5.2F,3.5F,state,pose,collector);
            case SYNTHESIZER -> {
                benchItems(8,9.5F,5.5F,3.7F,state,pose,collector);
                item(state.outputItem,8,6.15F,2.5F,5,2,2.7F,state,pose,collector);
            }
            case SINTER_FURNACE -> {
                benchItems(8,9,5.5F,3.5F,state,pose,collector);
                item(state.outputItem,8,6.15F,2.5F,5,1.8F,2.5F,state,pose,collector);
            }
            // Faceplates and status lights sample the block atlas like the housing around them.
            case DRIVE -> { }
            case INDUSTRIAL_PREPARATOR,INDUSTRIAL_REACTOR -> {
                if(state.industrialWidth>0) {
                    float x=(state.industrialWidth/2F-state.industrialColumn)*16;
                    float y=(state.industrialHeight/2F-1)*16;
                    float z=state.industrialDepth/2F*16;
                    boolean reactor=state.kind==MachineKind.INDUSTRIAL_REACTOR;
                    float size=Math.min(2.8F,Math.min(state.industrialWidth-2,state.industrialDepth-2)*1.35F);
                    halo(x,y,z,p*(reactor?180:-180),reactor?55:0,size,state,pose,collector);
                    if(reactor)halo(x,y,z,-p*90,-55,size,state,pose,collector);
                    else halo(x,y+3,z,p*90,0,size*.85F,state,pose,collector);
                    transformItems(x,y,z,Math.min(12,size*4.5F),state,pose,collector);
                    if(reactor)crystal(x,y,z,p*180,Math.min(2,size),state.hasProcess,state,pose,collector);
                }
            }
            default -> { }
        }
        if(state.sprites!=null||state.portSprites!=null)
            collector.submitCustomGeometry(pose,RenderTypes.solidMovingBlock(),state);
        pose.popPose();
    }

    /** Public process items stay inside the alcove; no old crystal heads or hoops enter the new hardware. */
    private static void benchItems(float x,float y,float z,float size,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector){
        var source=state.processInputItem.isEmpty()?state.inputItem:state.processInputItem;
        float progress=state.phase;
        float input=state.hasProcess?1-ease((progress-.35F)/.2F):1;
        float output=state.hasProcess?ease((progress-.6F)/.25F):0;
        floatingItem(source,x,y,z,size*input,0,state,pose,collector);
        floatingItem(state.secondaryItem,x-2.2F,y-.3F,z-1,size*.4F*input,0,state,pose,collector);
        floatingItem(state.tertiaryItem,x+2.2F,y-.3F,z-1,size*.4F*input,0,state,pose,collector);
        floatingItem(state.processOutputItem,x,y,z,size*output,0,state,pose,collector);
    }

    private static float ease(float x) { x=Math.clamp(x,0,1);return x*x*(3-2*x); }

    /** The change of item model is inside an opaque, small light core, never in open view. */
    private void transformItems(float x,float y,float z,float size,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        float p=state.phase;
        var source=state.processInputItem.isEmpty()?state.inputItem:state.processInputItem;
        if(!state.hasProcess) {
            floatingItem(source,x,y,z,size,0,state,pose,collector);
            floatingItem(state.secondaryItem,x-3,y-.4F,z-1,size*.5F,0,state,pose,collector);
            floatingItem(state.tertiaryItem,x+3,y-.4F,z-1,size*.5F,0,state,pose,collector);
            return;
        }
        float inputScale=1-ease((p-.32F)/.16F);
        float outputScale=ease((p-.62F)/.2F);
        float lift=ease(p/.3F)*.8F;
        if(inputScale>.001F) {
            floatingItem(source,x,y+lift,z,size*inputScale,p*45,state,pose,collector);
            float spread=3*(1-ease(p/.5F));
            floatingItem(state.secondaryItem,x-spread,y+lift-.3F,z-1,size*.5F*inputScale,-p*45,state,pose,collector);
            floatingItem(state.tertiaryItem,x+spread,y+lift-.3F,z-1,size*.5F*inputScale,p*45,state,pose,collector);
        }
        if(outputScale>.001F)floatingItem(state.processOutputItem,x,y+(1-outputScale)*.8F,z,size*outputScale,0,state,pose,collector);
        float mask=ease((p-.3F)/.16F)*(1-ease((p-.65F)/.2F));
        if(mask>.001F)crystal(x,y+.6F,z,p*180,size*.31F*mask,true,state,pose,collector);
    }

    private void halo(float x,float y,float z,float yaw,float tilt,float scale,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.mulPose(Axis.ZP.rotationDegrees(tilt));pose.scale(scale,scale,scale);
        part(halo,CONNECTOR_BODY,false,1,state,pose,collector);
        part(trace,CRYSTAL,state.hasProcess,Math.max(.25F,state.drive),state,pose,collector);pose.popPose();
    }
    private void crystal(float x,float y,float z,float rotation,float scale,boolean glow,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.YP.rotationDegrees(rotation));pose.scale(scale,scale,scale);
        part(core,CRYSTAL,glow,glow?Math.max(.3F,state.drive):1,state,pose,collector);pose.popPose();
    }
    private void beam(float x1,float y1,float z1,float x2,float y2,float z2,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length<.01F)return;
        pose.pushPose();pose.translate(x1/16,y1/16,z1/16);pose.mulPose(new Quaternionf().rotationTo(0,1,0,dx/length,dy/length,dz/length));pose.scale(1,length,1);
        part(beam,LIGHT,true,Math.max(.18F,state.drive*.8F),state,pose,collector);pose.popPose();
    }
    private void fragments(float x,float y,float z,float radius,float p,boolean inward,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        float t=Math.clamp((p-.24F)/.55F,0,1);if(t==0||t==1)return;
        float distance=radius*(inward?1-ease(t):ease(t));
        float scale=Mth.sin(t*Mth.PI);
        for(int i=0;i<6;i++) {
            float angle=i*Mth.TWO_PI/6+p*Mth.PI;
            pose.pushPose();pose.translate((x+Mth.cos(angle)*distance)/16,(y+.7F+Mth.sin(angle*2)*.6F)/16,(z+Mth.sin(angle)*distance)/16);
            pose.scale(scale,scale,scale);part(mote,LIGHT,true,Math.max(.2F,state.drive),state,pose,collector);pose.popPose();
        }
    }
    private static void floatingItem(ItemStackRenderState item,float x,float y,float z,float size,float yaw,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        if(item.isEmpty()||size<=.001F)return;
        var bounds=item.getModelBoundingBox();double extent=Math.max(bounds.getXsize(),Math.max(bounds.getYsize(),bounds.getZsize()));
        if(!Double.isFinite(extent)||extent<=0)return;
        double scale=size/extent/16;
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.scale((float)scale,(float)scale,(float)scale);
        pose.translate(-(bounds.minX+bounds.maxX)/2,-(bounds.minY+bounds.maxY)/2,-(bounds.minZ+bounds.maxZ)/2);
        item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    private static void part(ModelPart part,Identifier texture,boolean glow,float brightness,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        if(texture.equals(CRYSTAL))texture=CRYSTALS.getOrDefault(state.kind,texture);
        else if(texture.equals(LIGHT))texture=SIGNALS.getOrDefault(state.kind,texture);
        int tint=glow?UiPalette.brightness(brightness):UiPalette.WHITE;
        collector.submitModelPart(part,pose,RenderTypes.entitySolid(texture),glow?15_728_880:state.lightCoords,OverlayTexture.NO_OVERLAY,null,tint,state.breakProgress);
    }
    private static Map<MachineKind,Identifier> familyTextures(String folder) {
        var textures=new java.util.EnumMap<MachineKind,Identifier>(MachineKind.class);
        for(var kind:MachineKind.values()) {
            String family=switch(kind) {
                case ANALYZER -> "ice";
                case DECOMPOSER -> "copper";
                case SYNTHESIZER -> "leaf";
                case INDUSTRIAL_REACTOR -> "violet";
                case PREPARATOR,INDUSTRIAL_PREPARATOR -> "amber";
                case SINTER_FURNACE -> "ember";
                default -> null;
            };
            if(family!=null)textures.put(kind,Identifier.fromNamespaceAndPath("rematter","textures/"+folder+"/effect_"+family+".png"));
        }
        return Map.copyOf(textures);
    }

    /** Fit the resolved model, including its native fixed transform, onto a work surface. */
    private static void item(ItemStackRenderState item, float x, float surfaceY, float z,
            float width, float height, float depth, MachineRenderState state,
            PoseStack pose, SubmitNodeCollector collector) {
        if (item.isEmpty()) return;
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
