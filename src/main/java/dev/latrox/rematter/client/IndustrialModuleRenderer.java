package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.IndustrialModuleBlock;
import dev.latrox.rematter.content.IndustrialModuleBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Crystal sockets remain legible at rest. Only authoritative processing energizes their optics. */
public final class IndustrialModuleRenderer implements BlockEntityRenderer<IndustrialModuleBlockEntity, IndustrialModuleRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter", "industrial_modules"), "main");
    private static final Identifier MATRIX = Identifier.fromNamespaceAndPath("rematter", "textures/block/machine_ice.png");
    private static final Identifier COIL = Identifier.fromNamespaceAndPath("rematter", "textures/block/machine_amber.png");
    private static final Identifier CLUSTER = Identifier.fromNamespaceAndPath("rematter", "textures/block/matrix_cluster_core.png");
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/light.png");
    private static final int BEAM_COLOR = 0xff56bcb3, PULSE_COLOR = 0xffc4fff0;
    private final Map<IndustrialModuleBlockEntity, MachineMotion> motions = new WeakHashMap<>();
    private final Map<IndustrialModuleBlockEntity, ProcessVisualMotion> progressMotions = new WeakHashMap<>();
    private final ModelPart cutter, filter, basin, basinCrown, stabilizer, focus, focusHalo, halo, beam, pulse;

    public IndustrialModuleRenderer(BlockEntityRendererProvider.Context context) {
        var root = context.bakeLayer(LAYER);
        cutter=root.getChild("cutter");filter=root.getChild("filter");
        basin=root.getChild("basin");basinCrown=root.getChild("basin_crown");
        stabilizer=root.getChild("stabilizer");focus=root.getChild("focus");
        focusHalo=root.getChild("focus_halo");halo=root.getChild("halo");
        beam=root.getChild("beam");pulse=root.getChild("pulse");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        // Distinct core silhouettes, all fitted to the same mirrored floor/crown socket.
        crystal(root, "cutter", 2.4F, 7.6F);
        crystal(root, "filter", 1.4F, 7.2F);
        crystal(root, "stabilizer", 1.35F, 4.5F);
        crystal(root, "focus", 4.2F, 8.8F);
        confluence(root,"basin",false);
        confluence(root,"basin_crown",true);
        halo(root,"focus_halo",4.1F);
        var ring = root.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2F, -.09F, -3.3F, 4F, .18F, .18F)
                .addBox(-2F, -.09F, 3.12F, 4F, .18F, .18F)
                .addBox(-3.3F, -.09F, -2F, .18F, .18F, 4F)
                .addBox(3.12F, -.09F, -2F, .18F, .18F, 4F), PartPose.ZERO);
        for (int x : new int[] {-1, 1}) for (int z : new int[] {-1, 1}) {
            ring.addOrReplaceChild("corner_"+x+"_"+z, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-.9F, -.09F, -.09F, 1.8F, .18F, .18F),
                    PartPose.offsetAndRotation(x*2.65F, 0, z*2.65F, 0, x*z*Mth.PI/4, 0));
        }
        root.addOrReplaceChild("beam", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-.14F, 0, -.14F, .28F, 16, .28F), PartPose.ZERO);
        root.addOrReplaceChild("pulse", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-.32F, -.32F, -.32F, .64F, .64F, .64F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
    private static void halo(PartDefinition root,String name,float radius){
        float reach=radius*.61F,thickness=.24F;
        var part=root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(0,0)
                .addBox(-reach,-.12F,-radius,reach*2,.24F,thickness)
                .addBox(-reach,-.12F,radius-thickness,reach*2,.24F,thickness)
                .addBox(-radius,-.12F,-reach,thickness,.24F,reach*2)
                .addBox(radius-thickness,-.12F,-reach,thickness,.24F,reach*2),PartPose.ZERO);
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})
            part.addOrReplaceChild("corner_"+x+"_"+z,CubeListBuilder.create().texOffs(0,0)
                    .addBox(-1.05F,-.12F,-.12F,2.1F,.24F,.24F),
                    PartPose.offsetAndRotation(x*radius*.80F,0,z*radius*.80F,0,x*z*Mth.PI/4,0));
    }
    private static void confluence(PartDefinition root,String name,boolean mirrorZ){
        var part=root.addOrReplaceChild(name,CubeListBuilder.create(),PartPose.ZERO);
        inclinedCrystal(part,"left",-3.1F,7.4F,0,false,-Mth.PI/4);
        inclinedCrystal(part,"right",3.1F,7.4F,0,false,Mth.PI/4);
        // The render pose flips Y and Z. Reflecting this asymmetric core first in Z
        // makes its ceiling silhouette an exact Y mirror without negative face winding.
        inclinedCrystal(part,"rear",0,7.4F,mirrorZ?-3.1F:3.1F,true,mirrorZ?Mth.PI/4:-Mth.PI/4);
    }
    private static void inclinedCrystal(PartDefinition root,String name,float x,float y,float z,boolean axisX,float angle){
        float width=2.2F,height=5.8F,half=width/2,body=height*.34F,tip=width*.32F;
        root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(0,0)
                .addBox(-half,-body,-half,width,body*2,width),
                PartPose.offsetAndRotation(x,y,z,axisX?angle:0,0,axisX?0:angle));
        for(int sign:new int[]{-1,1}){
            float offset=sign*body;
            float cx=axisX?x:x-offset*Mth.sin(angle);
            float cy=y+offset*Mth.cos(angle);
            float cz=axisX?z+offset*Mth.sin(angle):z;
            root.addOrReplaceChild(name+"_tip_"+sign,CubeListBuilder.create().texOffs(0,10)
                    .addBox(-tip,-tip,-tip,tip*2,tip*2,tip*2),PartPose.offset(cx,cy,cz));
        }
    }
    private static void crystal(PartDefinition root, String name, float width, float height) {
        float h=width/2, body=height*.34F, tip=width*.32F;
        var crystal=root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(0,0)
                .addBox(-h,-body,-h,width,body*2,width),PartPose.ZERO);
        crystal.addOrReplaceChild("facets",CubeListBuilder.create().texOffs(0,0)
                .addBox(-h,-body,-h,width,body*2,width),PartPose.rotation(0,Mth.PI/4,0));
        for(int sign:new int[]{-1,1})crystal.addOrReplaceChild("tip_"+sign,CubeListBuilder.create().texOffs(0,10)
                .addBox(-tip,-tip,-tip,tip*2,tip*2,tip*2),PartPose.offsetAndRotation(0,sign*body,0,0,0,Mth.PI/4));
    }
    @Override public IndustrialModuleRenderState createRenderState() { return new IndustrialModuleRenderState(); }
    @Override public int getViewDistance() { return 48; }
    @Override public AABB getRenderBoundingBox(IndustrialModuleBlockEntity module) {
        // The beam is always inside a validated <=9-block shell; keep frustum culling bounded.
        return module.visuallyFormed() ? new AABB(module.getBlockPos()).inflate(8) : new AABB(module.getBlockPos());
    }
    @Override public void extractRenderState(IndustrialModuleBlockEntity module, IndustrialModuleRenderState state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(module, state, partialTick, cameraPosition, breakProgress);
        state.kind = module.kind();
        state.facing = module.getBlockState().getValue(IndustrialModuleBlock.FACING);
        state.suspended = module.getBlockState().getValue(IndustrialModuleBlock.SUSPENDED);
        state.active = module.getBlockState().getValue(IndustrialModuleBlock.ACTIVE);
        state.formed = module.visuallyFormed();
        state.progress = module.visualProgress();
        state.targetX = module.beamTargetX(); state.targetY = module.beamTargetY(); state.targetZ = module.beamTargetZ();
        int stage = module.preparationStage();
        state.emitting = state.active && switch (state.kind) {
            case GRINDING_UNIT -> stage == 0;
            case SEPARATION_UNIT -> stage == 2 || stage == 1 && !module.preparationMixing();
            case MIXING_BASIN -> stage == 1 && module.preparationMixing();
            case FIELD_COIL, REACTION_CHAMBER -> true;
        };
        // One shared starting phase makes adjacent modules read as one instrument.
        var motion = motions.computeIfAbsent(module, key -> new MachineMotion(0));
        if (module.getLevel() != null) {
            double time=module.getLevel().getGameTime()+(double)partialTick;
            motion.advance(time,state.emitting);
            state.progress=progressMotions.computeIfAbsent(module,key->new ProcessVisualMotion())
                    .sample(time,Math.round(module.visualProgress()*64),64,state.active);
        }
        state.phase = motion.phase(); state.drive = motion.drive();
    }
    @Override public void submit(IndustrialModuleRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(.5F,0,.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180-state.facing.toYRot()));
        pose.translate(-.5F,0,-.5F);
        pose.pushPose();
        // A proper rotation mirrors Y; the asymmetric confluence core has a Z-reflected variant
        // to keep the floor/crown pair identical without reversing winding or stretching crystals.
        if(state.suspended){pose.translate(0,1,1);pose.mulPose(Axis.XP.rotationDegrees(180));}
        // Fixed-proportion cores never wobble, spin or stretch; only their light performs work.
        switch(state.kind){
            case GRINDING_UNIT -> {
                core(cutter,5.2F,8.1F,8,state,pose,collector);
                core(cutter,10.8F,8.1F,8,state,pose,collector);
            }
            case SEPARATION_UNIT -> {
                for(float x:new float[]{4.9F,8,11.1F})core(filter,x,8,8,state,pose,collector);
            }
            case MIXING_BASIN -> {
                pose.pushPose();pose.translate(.5F,0,.5F);
                draw(state.suspended?basinCrown:basin,MATRIX,state.emitting,-1,state,pose,collector);
                pose.popPose();
            }
            case FIELD_COIL -> {
                for(int i=0;i<8;i++){
                    float a=i*Mth.PI/4;
                    core(stabilizer,8+3.25F*Mth.cos(a),7.6F,8+3.25F*Mth.sin(a),state,pose,collector);
                }
            }
            case REACTION_CHAMBER -> {
                core(focus,8,8.5F,8,state,pose,collector);
                pose.pushPose();pose.translate(.5F,8.5F/16,.5F);
                draw(focusHalo,CLUSTER,state.emitting,-1,state,pose,collector);pose.popPose();
            }
        }
        if(state.emitting){
            float bandY=switch(state.kind){
                case SEPARATION_UNIT -> (5.8F+4.8F*state.progress)/16;
                case MIXING_BASIN -> 6.4F/16;
                case FIELD_COIL -> 7.2F/16;
                case GRINDING_UNIT,REACTION_CHAMBER -> 9.8F/16;
            };
            pose.pushPose();pose.translate(.5F,bandY,.5F);
            draw(halo,LIGHT,true,BEAM_COLOR,state,pose,collector);pose.popPose();
            switch(state.kind){
                case GRINDING_UNIT -> {
                    float y=(6.4F+3.6F*state.progress)/16;
                    line(4.9F/16,y,.5F,11.1F/16,y,.5F,state,pose,collector);
                }
                case MIXING_BASIN -> {
                    for(float[] p:new float[][]{{4.9F,8},{11.1F,8},{8,4.9F},{8,11.1F}})
                        line(p[0]/16,6.4F/16,p[1]/16,.5F,8.3F/16,.5F,state,pose,collector);
                }
                case FIELD_COIL -> {
                    pose.pushPose();pose.translate(.5F,10F/16,.5F);
                    draw(halo,LIGHT,true,BEAM_COLOR,state,pose,collector);pose.popPose();
                }
                case SEPARATION_UNIT,REACTION_CHAMBER -> { }
            }
        }
        pose.popPose();
        if(state.formed&&state.emitting&&state.drive>.01F){
            float sourceY=switch(state.kind){
                case GRINDING_UNIT -> 11.8F/16;
                case SEPARATION_UNIT -> 11.1F/16;
                case MIXING_BASIN -> 9.5F/16;
                case FIELD_COIL -> 9.6F/16;
                case REACTION_CHAMBER -> 13.4F/16;
            };
            if(state.suspended)sourceY=1-sourceY;
            line(.5F,sourceY,.5F,state.targetX,state.targetY,state.targetZ,state,pose,collector);
            // One pulse per real installed module, travelling along its own field connection.
            float t=state.progress;
            if(state.kind==dev.latrox.rematter.content.IndustrialModuleKind.SEPARATION_UNIT)t=1-t;
            pose.pushPose();pose.translate(Mth.lerp(t,.5F,state.targetX),Mth.lerp(t,sourceY,state.targetY),Mth.lerp(t,.5F,state.targetZ));
            draw(pulse,LIGHT,true,PULSE_COLOR,state,pose,collector);pose.popPose();
        }
        pose.popPose();
    }
    private void core(ModelPart part,float x,float y,float z,IndustrialModuleRenderState state,
            PoseStack pose,SubmitNodeCollector collector){
        pose.pushPose();pose.translate(x/16,y/16,z/16);
        Identifier texture = switch (state.kind) {
            case FIELD_COIL -> COIL;
            case REACTION_CHAMBER -> CLUSTER;
            default -> MATRIX;
        };
        draw(part,texture,state.emitting,-1,state,pose,collector);pose.popPose();
    }
    private void line(float x,float y,float z,float tx,float ty,float tz,IndustrialModuleRenderState state,
            PoseStack pose,SubmitNodeCollector collector){
        float dx=tx-x,dy=ty-y,dz=tz-z,length=Mth.sqrt(dx*dx+dy*dy+dz*dz);
        if(!Float.isFinite(length)||length<.015F||length>12)return;
        pose.pushPose();pose.translate(x,y,z);
        pose.mulPose(Axis.YP.rotation((float)Math.atan2(dx,dz)));
        pose.mulPose(Axis.XP.rotation((float)Math.acos(Math.clamp(dy/length,-1,1))));
        pose.scale(1,length,1);
        draw(beam,LIGHT,true,BEAM_COLOR,state,pose,collector);pose.popPose();
    }
    private static void draw(ModelPart part, Identifier texture, boolean glow, int color, IndustrialModuleRenderState state,
            PoseStack pose, SubmitNodeCollector collector) {
        collector.submitModelPart(part, pose, RenderTypes.entitySolid(texture), glow ? 15_728_880 : state.lightCoords,
                OverlayTexture.NO_OVERLAY, null, color, state.breakProgress);
    }
}
