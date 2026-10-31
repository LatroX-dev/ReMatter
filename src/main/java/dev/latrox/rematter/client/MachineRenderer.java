package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineKind;
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

/** Small rigid mechanisms use vanilla's submitted model-part pipeline. */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity, MachineRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter", "mechanisms"), "main");
    private static final Identifier METAL = Identifier.fromNamespaceAndPath("rematter", "textures/entity/mechanism.png");
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/light.png");
    private final ModelPart rotor;
    private final ModelPart scanner;
    private final ModelPart scanline;
    private final ModelPart press;
    private final ModelPart agitator;
    private final ModelPart turbine;
    private final ModelPart bubbles;

    public MachineRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        rotor = root.getChild("rotor");
        scanner = root.getChild("scanner");
        scanline = root.getChild("scanline");
        press = root.getChild("press");
        agitator = root.getChild("agitator");
        turbine = root.getChild("turbine");
        bubbles = root.getChild("bubbles");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("rotor", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.4F, -0.5F, -0.7F, 4.8F, 1, 1.4F)
                .addBox(-0.5F, -2.4F, -0.7F, 1, 4.8F, 1.4F)
                .addBox(-0.85F, -0.85F, -1.05F, 1.7F, 1.7F, 2.1F), PartPose.ZERO);
        root.addOrReplaceChild("scanner", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -0.3F, -0.4F, 8, 0.6F, 0.8F), PartPose.ZERO);
        root.addOrReplaceChild("scanline", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -0.025F, -0.15F, 8, 0.05F, 0.3F), PartPose.ZERO);
        root.addOrReplaceChild("press", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -1, -3.5F, 7, 2, 7)
                .addBox(-0.65F, 1, -0.65F, 1.3F, 3.5F, 1.3F), PartPose.ZERO);
        root.addOrReplaceChild("agitator", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.6F, -2.5F, -0.6F, 1.2F, 5, 1.2F)
                .addBox(-3, -0.3F, -0.6F, 6, 0.6F, 1.2F)
                .addBox(-0.6F, -0.3F, -3, 1.2F, 0.6F, 6), PartPose.ZERO);
        root.addOrReplaceChild("turbine", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -0.5F, -0.7F, 8, 1, 1.4F)
                .addBox(-0.7F, -0.5F, -4, 1.4F, 1, 8)
                .addBox(-1.1F, -0.7F, -1.1F, 2.2F, 1.4F, 2.2F), PartPose.ZERO);
        root.addOrReplaceChild("bubbles", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.4F, -0.4F, -0.4F, 0.8F, 0.8F, 0.8F)
                .addBox(0.5F, 1.6F, 0.2F, 0.45F, 0.45F, 0.45F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override public MachineRenderState createRenderState() { return new MachineRenderState(); }
    @Override public boolean shouldRender(MachineBlockEntity machine, Vec3 camera) {
        return machine.kind().processes() && BlockEntityRenderer.super.shouldRender(machine, camera);
    }
    @Override public int getViewDistance() { return 48; }
    @Override public void extractRenderState(MachineBlockEntity machine, MachineRenderState state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine, state, partialTick, cameraPosition, breakProgress);
        state.kind = machine.kind();
        state.facing = machine.getBlockState().getValue(MachineBlock.FACING);
        state.active = machine.getBlockState().getValue(MachineBlock.ACTIVE);
        state.time = state.active && machine.getLevel() != null ? (machine.getLevel().getGameTime() % 24_000) + partialTick : 0;
    }

    @Override public void submit(MachineRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
        float time = state.time;
        switch (state.kind) {
            case ANALYZER -> {
                float z = 7.5F + (state.active ? Mth.sin(time * 0.12F) * 3 : 0);
                part(scanner, 8, 11, z, 0, false, false, state, pose, collector);
                if (state.active) part(scanline, 8, 4.78F, z, 0, false, true, state, pose, collector);
            }
            case DECOMPOSER -> {
                part(rotor, 5.5F, 7, 5, time * 12, true, false, state, pose, collector);
                part(rotor, 10.5F, 7, 5, -time * 12, true, false, state, pose, collector);
            }
            case SYNTHESIZER -> part(press, 8, 10 + (state.active ? (Mth.sin(time * 0.22F) - 1) * 0.85F : 0), 8,
                    0, false, false, state, pose, collector);
            case ELECTROLYZER -> {
                if (state.active) {
                    part(bubbles, 4.5F, 5 + (time * 0.13F) % 6, 6.3F, 0, false, true, state, pose, collector);
                    part(bubbles, 11.5F, 5 + (time * 0.13F + 3) % 6, 6.3F, 0, false, true, state, pose, collector);
                }
            }
            case REACTOR -> part(agitator, 8, 11, 8, time * 6, false, false, state, pose, collector);
            case INDUSTRIAL_REACTOR -> part(turbine, 8, 13, 8, time * 8, false, false, state, pose, collector);
            default -> { }
        }
        pose.popPose();
    }

    private static void part(ModelPart part, float x, float y, float z, float rotation, boolean frontAxis, boolean glow,
            MachineRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(x / 16, y / 16, z / 16);
        if (rotation != 0) pose.mulPose((frontAxis ? Axis.ZP : Axis.YP).rotationDegrees(rotation));
        collector.submitModelPart(part, pose, RenderTypes.entitySolid(glow ? LIGHT : METAL),
                glow ? 15_728_880 : state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();
    }
}
