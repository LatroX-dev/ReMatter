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
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/effect_light.png");
    private final Map<IndustrialModuleBlockEntity, MachineMotion> motions = new WeakHashMap<>();
    private final Map<IndustrialModuleBlockEntity, ProcessVisualMotion> progressMotions = new WeakHashMap<>();
    private final ModelPart beam, pulse;

    public IndustrialModuleRenderer(BlockEntityRendererProvider.Context context) {
        var root = context.bakeLayer(LAYER);
        beam=root.getChild("beam");pulse=root.getChild("pulse");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        // Static crystals are baked in the same models as their held item. Only the field is dynamic.
        root.addOrReplaceChild("beam", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-.25F, 0, -.25F, .5F, 16, .5F), PartPose.ZERO);
        root.addOrReplaceChild("pulse", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-.5F, -.5F, -.5F, 1, 1, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
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
        if(state.formed&&state.emitting&&state.drive>.01F){
            float sourceY=switch(state.kind){
                case GRINDING_UNIT -> 14F/16;
                case SEPARATION_UNIT -> 14F/16;
                case MIXING_BASIN -> 12F/16;
                case FIELD_COIL -> 12F/16;
                case REACTION_CHAMBER -> 9.5F/16;
            };
            if(state.suspended)sourceY=1-sourceY;
            line(.5F,sourceY,.5F,state.targetX,state.targetY,state.targetZ,state,pose,collector);
            // One pulse per real installed module, travelling along its own field connection.
            float t=state.progress;
            if(state.kind==dev.latrox.rematter.content.IndustrialModuleKind.SEPARATION_UNIT)t=1-t;
            pose.pushPose();pose.translate(Mth.lerp(t,.5F,state.targetX),Mth.lerp(t,sourceY,state.targetY),Mth.lerp(t,.5F,state.targetZ));
            draw(pulse,LIGHT,true,UiPalette.modulePulse(state.kind),state,pose,collector);pose.popPose();
        }
        pose.popPose();
    }
    private void line(float x,float y,float z,float tx,float ty,float tz,IndustrialModuleRenderState state,
            PoseStack pose,SubmitNodeCollector collector){
        float dx=tx-x,dy=ty-y,dz=tz-z,length=Mth.sqrt(dx*dx+dy*dy+dz*dz);
        if(!Float.isFinite(length)||length<.015F||length>12)return;
        pose.pushPose();pose.translate(x,y,z);
        pose.mulPose(Axis.YP.rotation((float)Math.atan2(dx,dz)));
        pose.mulPose(Axis.XP.rotation((float)Math.acos(Math.clamp(dy/length,-1,1))));
        pose.scale(1,length,1);
        draw(beam,LIGHT,true,UiPalette.moduleAccent(state.kind),state,pose,collector);pose.popPose();
    }
    private static void draw(ModelPart part, Identifier texture, boolean glow, int color, IndustrialModuleRenderState state,
            PoseStack pose, SubmitNodeCollector collector) {
        collector.submitModelPart(part, pose, RenderTypes.entitySolid(texture), glow ? 15_728_880 : state.lightCoords,
                OverlayTexture.NO_OVERLAY, null, color, state.breakProgress);
    }
}
