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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Local mechanisms are visible even when unformed; motion requires authoritative work. */
public final class IndustrialModuleRenderer implements BlockEntityRenderer<IndustrialModuleBlockEntity, IndustrialModuleRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter", "industrial_modules"), "main");
    private static final Identifier METAL = Identifier.fromNamespaceAndPath("rematter", "textures/entity/mechanism.png");
    private static final Identifier CRYSTAL = Identifier.fromNamespaceAndPath("rematter", "textures/block/reaction_chamber_crystal.png");
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/light.png");
    private final Map<IndustrialModuleBlockEntity, MachineMotion> motions = new WeakHashMap<>();
    private final ModelPart roller, sieve, paddle, crystal, field;

    public IndustrialModuleRenderer(BlockEntityRendererProvider.Context context) {
        var root = context.bakeLayer(LAYER);
        roller = root.getChild("roller"); sieve = root.getChild("sieve"); paddle = root.getChild("paddle");
        crystal = root.getChild("crystal"); field = root.getChild("field");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        float half = 2.1F / Mth.SQRT_OF_TWO;
        var roller = root.addOrReplaceChild("roller", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -half, -half, 8, 2 * half, 2 * half)
                .texOffs(0, 16).addBox(-4.5F, -0.35F, -0.35F, 9, 0.7F, 0.7F), PartPose.ZERO);
        roller.addOrReplaceChild("octagon", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -half, -half, 8, 2 * half, 2 * half), PartPose.rotation(Mth.PI / 4, 0, 0));
        // A shallow raised rib at each quarter makes the roller's direction legible.
        for (int i = 0; i < 4; i++) roller.addOrReplaceChild("rib_" + i, CubeListBuilder.create().texOffs(0, 16)
                .addBox(-3.7F, 1.49F, -0.18F, 7.4F, 0.25F, 0.36F), PartPose.rotation(i * Mth.HALF_PI, 0, 0));
        var screen = CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5, -0.3F, -5, 10, 0.6F, 0.5F).addBox(-5, -0.3F, 4.5F, 10, 0.6F, 0.5F)
                .addBox(-5, -0.3F, -4.5F, 0.5F, 0.6F, 9).addBox(4.5F, -0.3F, -4.5F, 0.5F, 0.6F, 9);
        for (int n = -4; n <= 4; n++) {
            screen.addBox(n - 0.06F, -0.06F, -4.5F, 0.12F, 0.12F, 9);
            screen.addBox(-4.5F, 0.06F, n - 0.06F, 9, 0.12F, 0.12F);
        }
        root.addOrReplaceChild("sieve", screen, PartPose.ZERO);
        var paddle = root.addOrReplaceChild("paddle", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.4F, -2.5F, -0.4F, 0.8F, 9.3F, 0.8F), PartPose.ZERO);
        for (int n = 0; n < 3; n++) paddle.addOrReplaceChild("arm_" + n, CubeListBuilder.create().texOffs(0, 16)
                .addBox(0.4F, -0.5F, -0.4F, 3.6F, 1, 0.8F)
                .addBox(3.6F, -1.1F, -0.6F, 0.5F, 1.6F, 1.2F), PartPose.rotation(0, n * Mth.TWO_PI / 3, 0));
        var crystal = root.addOrReplaceChild("crystal", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.6F, -2.7F, -1.6F, 3.2F, 5.4F, 3.2F), PartPose.ZERO);
        crystal.addOrReplaceChild("facets", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.6F, -2.7F, -1.6F, 3.2F, 5.4F, 3.2F), PartPose.rotation(0, Mth.PI / 4, 0));
        for (int sign : new int[] {-1, 1}) crystal.addOrReplaceChild("tip_" + sign, CubeListBuilder.create().texOffs(0, 10)
                .addBox(-1.1F, -1.1F, -1.1F, 2.2F, 2.2F, 2.2F), PartPose.offsetAndRotation(0, sign * 2.7F, 0, 0, 0, Mth.PI / 4));
        root.addOrReplaceChild("field", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.25F, -4.2F, -0.25F, 0.5F, 8.4F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
    @Override public IndustrialModuleRenderState createRenderState() { return new IndustrialModuleRenderState(); }
    @Override public int getViewDistance() { return 48; }
    @Override public void extractRenderState(IndustrialModuleBlockEntity module, IndustrialModuleRenderState state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(module, state, partialTick, cameraPosition, breakProgress);
        state.kind = module.kind();
        state.facing = module.getBlockState().getValue(IndustrialModuleBlock.FACING);
        state.active = module.getBlockState().getValue(IndustrialModuleBlock.ACTIVE);
        int stage = module.preparationStage();
        boolean moving = state.active && switch (state.kind) {
            case GRINDING_UNIT -> stage == 0;
            case SEPARATION_UNIT -> stage == 2 || stage == 1 && !module.preparationMixing();
            case MIXING_BASIN -> stage == 1 && module.preparationMixing();
            case FIELD_COIL, REACTION_CHAMBER -> true;
        };
        var motion = motions.computeIfAbsent(module, key -> new MachineMotion(key.getBlockPos().asLong()));
        if (module.getLevel() != null) motion.advance(module.getLevel().getGameTime() + (double) partialTick, moving);
        state.phase = motion.phase(); state.drive = motion.drive();
    }
    @Override public void submit(IndustrialModuleRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
        switch (state.kind) {
            case GRINDING_UNIT -> {
                for (int i = 0; i < 2; i++) {
                    pose.pushPose(); pose.translate(0.5F, 10.5F / 16, (5.5F + i * 5) / 16);
                    pose.mulPose(Axis.XP.rotationDegrees(state.phase * (i == 0 ? 1080 : -1080)));
                    draw(roller, METAL, false, state, pose, collector); pose.popPose();
                }
            }
            case SEPARATION_UNIT -> {
                pose.translate((8 + Mth.sin(state.phase * Mth.TWO_PI * 8) * 0.18F) / 16, 0.5F, 0.5F);
                draw(sieve, METAL, false, state, pose, collector);
            }
            case MIXING_BASIN -> {
                pose.translate(0.5F, 5.2F / 16, 0.5F); pose.mulPose(Axis.YP.rotationDegrees(state.phase * 720));
                draw(paddle, METAL, false, state, pose, collector);
            }
            case FIELD_COIL -> {
                if (state.active && state.drive > 0.01F) {
                    pose.translate(0.5F, 0.5F, 0.5F);
                    float pulse = 0.65F + 0.35F * Mth.sin(state.phase * Mth.TWO_PI * 3);
                    pose.scale(pulse, 1, pulse); draw(field, LIGHT, true, state, pose, collector);
                }
            }
            case REACTION_CHAMBER -> {
                pose.translate(0.5F, (8 + Mth.sin(state.phase * Mth.TWO_PI) * 0.25F) / 16, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(state.phase * 360));
                draw(crystal, CRYSTAL, false, state, pose, collector);
            }
        }
        pose.popPose();
    }
    private static void draw(ModelPart part, Identifier texture, boolean glow, IndustrialModuleRenderState state,
            PoseStack pose, SubmitNodeCollector collector) {
        collector.submitModelPart(part, pose, RenderTypes.entitySolid(texture), glow ? 15_728_880 : state.lightCoords,
                OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
    }
}
