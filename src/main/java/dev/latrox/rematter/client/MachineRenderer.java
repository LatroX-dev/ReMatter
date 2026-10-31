package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineKind;
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

/** Small rigid mechanisms use vanilla's submitted model-part pipeline. */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity, MachineRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter", "mechanisms"), "main");
    private static final Identifier METAL = Identifier.fromNamespaceAndPath("rematter", "textures/entity/mechanism.png");
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/light.png");
    private final Map<MachineBlockEntity, MachineMotion> motion = new WeakHashMap<>();
    private final ModelPart rotor;
    private final ModelPart scanner;
    private final ModelPart scanline;
    private final ModelPart press;
    private final ModelPart piston;
    private final ModelPart agitator;
    private final ModelPart turbine;
    private final ModelPart bubbles;

    public MachineRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        rotor = root.getChild("rotor");
        scanner = root.getChild("scanner");
        scanline = root.getChild("scanline");
        press = root.getChild("press");
        piston = root.getChild("piston");
        agitator = root.getChild("agitator");
        turbine = root.getChild("turbine");
        bubbles = root.getChild("bubbles");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var cutter = root.addOrReplaceChild("rotor", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.8F, -0.8F, -1.05F, 1.6F, 1.6F, 2.1F), PartPose.ZERO);
        for (int tooth = 0; tooth < 6; tooth++) {
            cutter.addOrReplaceChild("tooth_" + tooth, CubeListBuilder.create().texOffs(0, 16)
                    .addBox(-0.55F, 0.65F, -0.8F, 1.1F, 1.5F, 1.6F),
                    PartPose.rotation(0, 0, tooth * Mth.TWO_PI / 6));
        }
        root.addOrReplaceChild("scanner", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -0.3F, -0.4F, 8, 0.6F, 0.8F)
                .texOffs(0, 16).addBox(-3.6F, -0.5F, -0.25F, 7.2F, 0.2F, 0.5F), PartPose.ZERO);
        root.addOrReplaceChild("scanline", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -0.025F, -0.12F, 7, 0.05F, 0.24F), PartPose.ZERO);
        root.addOrReplaceChild("press", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -1, -3.5F, 7, 2, 7)
                .texOffs(0, 16).addBox(-3.1F, -1.3F, -3.1F, 6.2F, 0.3F, 6.2F), PartPose.ZERO);
        root.addOrReplaceChild("piston", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.65F, -1, -0.65F, 1.3F, 1, 1.3F), PartPose.ZERO);
        var mixer = root.addOrReplaceChild("agitator", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.45F, -2.5F, -0.45F, 0.9F, 5, 0.9F), PartPose.ZERO);
        for (int blade = 0; blade < 3; blade++) {
            mixer.addOrReplaceChild("paddle_" + blade, CubeListBuilder.create().texOffs(0, 16)
                    .addBox(0.4F, -0.5F, -0.45F, 2.4F, 1, 0.9F),
                    PartPose.rotation(0, blade * Mth.TWO_PI / 3, 0));
        }
        var fan = root.addOrReplaceChild("turbine", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.8F, -0.8F, -0.8F, 1.6F, 1.6F, 1.6F), PartPose.ZERO);
        for (int blade = 0; blade < 6; blade++) {
            fan.addOrReplaceChild("vane_" + blade, CubeListBuilder.create().texOffs(0, 16)
                    .addBox(0.7F, -0.18F, -0.7F, 2.7F, 0.36F, 1.4F),
                    PartPose.rotation(0, blade * Mth.TWO_PI / 6, 0.16F));
        }
        root.addOrReplaceChild("bubbles", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.35F, -0.35F, -0.35F, 0.7F, 0.7F, 0.7F), PartPose.ZERO);
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
        MachineMotion movement = motion.computeIfAbsent(machine,
                key -> new MachineMotion(key.getBlockPos().asLong()));
        if (machine.getLevel() != null) movement.advance(machine.getLevel().getGameTime() + (double) partialTick, state.active);
        state.phase = movement.phase();
        state.drive = movement.drive();
    }

    @Override public void submit(MachineRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
        float angle = state.phase * 360;
        float cycle = state.phase * Mth.TWO_PI;
        switch (state.kind) {
            case ANALYZER -> {
                float z = 7.5F + Mth.sin(cycle) * 3 * state.drive;
                part(scanner, 8, 11, z, 0, false, false, state, pose, collector);
                if (state.drive > 0.02F) {
                    part(scanline, 8, 4.78F, z, 0, false, true, state, pose, collector);
                    part(scanline, 8, 10.48F, z, 0, false, true, state, pose, collector);
                }
            }
            case DECOMPOSER -> {
                part(rotor, 5.5F, 7, 5, angle * 3, true, false, state, pose, collector);
                part(rotor, 10.5F, 7, 5, -angle * 3 + 30, true, false, state, pose, collector);
            }
            case SYNTHESIZER -> {
                float compression = (1 - Mth.cos(cycle * 2)) * 0.5F;
                float eased = compression * compression * (3 - 2 * compression);
                float stroke = eased * 3.1F * state.drive;
                part(press, 8, 10 - stroke, 8, 0, false, false, state, pose, collector);
                pose.pushPose();
                pose.translate(0.5F, 14.5F / 16, 0.5F);
                pose.scale(1, 3.5F + stroke, 1);
                part(piston, 0, 0, 0, 0, false, false, state, pose, collector);
                pose.popPose();
            }
            case ELECTROLYZER -> {
                if (state.drive > 0.02F) {
                    for (int column = 0; column < 2; column++) {
                        for (int bubble = 0; bubble < 3; bubble++) {
                            float rise = (state.phase * 3 + bubble / 3F + column * 0.17F) % 1;
                            float size = Mth.sin(rise * Mth.PI) * state.drive;
                            pose.pushPose();
                            pose.translate((4.5F + column * 7) / 16, (5.4F + rise * 5.8F) / 16, 6.3F / 16);
                            pose.scale(size, size, size);
                            part(bubbles, 0, 0, 0, angle, false, true, state, pose, collector);
                            pose.popPose();
                        }
                    }
                }
            }
            case REACTOR -> part(agitator, 8, 11, 8, angle * 2, false, false, state, pose, collector);
            case INDUSTRIAL_REACTOR -> part(turbine, 8, 13, 8, angle * 3, false, false, state, pose, collector);
            default -> { }
        }
        pose.popPose();
    }

    private static int glowColor(float drive) {
        int value = (int) (255 * Math.clamp(drive, 0, 1));
        return 0xff000000 | value << 16 | value << 8 | value;
    }

    private static void part(ModelPart part, float x, float y, float z, float rotation, boolean frontAxis, boolean glow,
            MachineRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(x / 16, y / 16, z / 16);
        if (rotation != 0) pose.mulPose((frontAxis ? Axis.ZP : Axis.YP).rotationDegrees(rotation));
        collector.submitModelPart(part, pose, RenderTypes.entitySolid(glow ? LIGHT : METAL),
                glow ? 15_728_880 : state.lightCoords, OverlayTexture.NO_OVERLAY, null,
                glow ? glowColor(state.drive) : -1, state.breakProgress);
        pose.popPose();
    }
}
