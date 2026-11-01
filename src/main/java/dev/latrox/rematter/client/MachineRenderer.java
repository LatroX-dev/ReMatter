package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.IndustrialStructure;
import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Small rigid mechanisms use vanilla's submitted model-part pipeline. */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity, MachineRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter", "mechanisms"), "main");
    private static final Identifier METAL = Identifier.fromNamespaceAndPath("rematter", "textures/entity/mechanism.png");
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/light.png");
    private static final Identifier CELL = Identifier.fromNamespaceAndPath("rematter", "textures/entity/cell.png");
    private static final Identifier CERAMIC = Identifier.fromNamespaceAndPath("rematter", "textures/block/ceramic.png");
    private static final Identifier WOOD = Identifier.fromNamespaceAndPath("rematter", "textures/block/bench_wood.png");
    private final Map<MachineBlockEntity, MachineMotion> motion = new WeakHashMap<>();
    private final Map<MachineBlockEntity, MachineMotion[]> preparationMotion = new WeakHashMap<>();
    private final ModelPart rotor;
    private final ModelPart scanner;
    private final ModelPart scanline;
    private final ModelPart press;
    private final ModelPart piston;
    private final ModelPart agitator;
    private final ModelPart bubbles;
    private final ModelPart cell;
    private final ModelPart cellIndicator;
    private final ModelPart crusher, sieve, paddle;
    private final ModelPart handCrank, handGrip;
    private final ModelPart industrialSpindle;
    // Bound the per-shape static geometry cache independently of the number of loaded machines.
    private final Map<IndustrialGeometryKey, IndustrialGeometry> industrialGeometry = new LinkedHashMap<>(32, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<IndustrialGeometryKey, IndustrialGeometry> entry) {
            return size() > 32;
        }
    };

    public MachineRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        rotor = root.getChild("rotor");
        scanner = root.getChild("scanner");
        scanline = root.getChild("scanline");
        press = root.getChild("press");
        piston = root.getChild("piston");
        agitator = root.getChild("agitator");
        bubbles = root.getChild("bubbles");
        cell = root.getChild("cell");
        cellIndicator = root.getChild("cell_indicator");
        crusher = root.getChild("preparation_crusher");
        sieve = root.getChild("preparation_sieve");
        paddle = root.getChild("preparation_paddle");
        handCrank = root.getChild("hand_crank");
        handGrip = root.getChild("hand_grip");
        industrialSpindle = root.getChild("industrial_spindle");
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
        root.addOrReplaceChild("bubbles", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.35F, -0.35F, -0.35F, 0.7F, 0.7F, 0.7F), PartPose.ZERO);
        root.addOrReplaceChild("cell", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, 0, 0, 8, 4.3F, 7.1F), PartPose.ZERO);
        root.addOrReplaceChild("cell_indicator", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.4F, 0, -0.03F, 4.8F, 0.35F, 0.06F), PartPose.ZERO);
        // Two crossed square sections form a quiet octagonal roller. Axis X, radius 1.6.
        // The electric pair scales only Y/Z; its eight-pixel working width is unchanged.
        float half = 1.1313708F;
        var crusher = root.addOrReplaceChild("preparation_crusher", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -half, -half, 8, half * 2, half * 2)
                .texOffs(0, 16).addBox(-4.3F, -0.3F, -0.3F, 8.6F, 0.6F, 0.6F), PartPose.ZERO);
        crusher.addOrReplaceChild("rounded_section", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -half, -half, 8, half * 2, half * 2), PartPose.rotation(Mth.PI / 4, 0, 0));
        var grid = CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -0.15F, -3, 9, 0.3F, 0.3F)
                .addBox(-4.5F, -0.15F, 2.7F, 9, 0.3F, 0.3F)
                .addBox(-4.5F, -0.15F, -2.7F, 0.3F, 0.3F, 5.4F)
                .addBox(4.2F, -0.15F, -2.7F, 0.3F, 0.3F, 5.4F);
        for (int rod = -3; rod <= 3; rod++)
            grid.addBox(rod, -0.04F, -2.7F, 0.12F, 0.1F, 5.4F);
        for (int rod = -2; rod <= 2; rod++)
            grid.addBox(-4.2F, -0.04F, rod, 8.4F, 0.1F, 0.12F);
        root.addOrReplaceChild("preparation_sieve", grid, PartPose.ZERO);
        var paddle = root.addOrReplaceChild("preparation_paddle", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.22F, -1, -0.22F, 0.44F, 2.25F, 0.44F), PartPose.ZERO);
        for (int blade = 0; blade < 3; blade++) paddle.addOrReplaceChild("paddle_" + blade,
                CubeListBuilder.create().texOffs(0, 16).addBox(0.15F, -0.25F, -0.22F, 1.65F, 0.5F, 0.44F),
                PartPose.rotation(0, blade * Mth.TWO_PI / 3, 0));
        root.addOrReplaceChild("hand_crank", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.7F, -0.2F, -0.2F, 2, 0.4F, 0.4F)
                .texOffs(0, 16).addBox(-0.15F, -0.22F, -0.22F, 0.3F, 1.72F, 0.44F), PartPose.ZERO);
        root.addOrReplaceChild("hand_grip", CubeListBuilder.create().texOffs(0, 0)
                .addBox(0.15F, 1.15F, -0.25F, 0.7F, 0.4F, 0.5F), PartPose.ZERO);
        // This normalized spindle is scaled to each validated chamber's actual height.
        var spindle = root.addOrReplaceChild("industrial_spindle", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.6F, -14.5F, -0.6F, 1.2F, 29, 1.2F), PartPose.ZERO);
        for (int level = 0; level < 3; level++) {
            float y = -9 + level * 8;
            for (int blade = 0; blade < 3; blade++) {
                spindle.addOrReplaceChild("paddle_" + level + "_" + blade,
                        CubeListBuilder.create().texOffs(0, 16).addBox(0.6F, y, -0.55F, 3.8F, 0.65F, 1.1F),
                        PartPose.rotation(0, blade * Mth.TWO_PI / 3 + level * Mth.PI / 6, 0));
            }
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override public MachineRenderState createRenderState() { return new MachineRenderState(); }
    @Override public boolean shouldRender(MachineBlockEntity machine, Vec3 camera) {
        return (machine.kind().processes() || machine.kind() == MachineKind.DRIVE)
                && BlockEntityRenderer.super.shouldRender(machine, camera);
    }
    @Override public AABB getRenderBoundingBox(MachineBlockEntity machine) {
        int width = machine.industrialWidth(), height = machine.industrialHeight();
        int depth = machine.industrialDepth(), column = machine.industrialControllerColumn();
        if (!machine.industrialFormed() || !validIndustrialShape(machine.kind(), width, height, depth, column))
            return new AABB(machine.getBlockPos());
        var facing = machine.getBlockState().getValue(MachineBlock.FACING);
        var pos = machine.getBlockPos();
        var a = pos.relative(facing.getClockWise(), -column);
        var b = pos.relative(facing.getClockWise(), width - column - 1).relative(facing.getOpposite(), depth - 1);
        return new AABB(Math.min(a.getX(), b.getX()), pos.getY() - 1, Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, pos.getY() + height - 1,
                Math.max(a.getZ(), b.getZ()) + 1);
    }
    private static boolean validIndustrialShape(MachineKind kind, int width, int height, int depth, int column) {
        return IndustrialStructure.isIndustrial(kind) && width >= 3 && width <= 9 && depth >= 3 && depth <= 9
                && height >= (kind == MachineKind.INDUSTRIAL_REACTOR ? 4 : 3) && height <= 9
                && column >= 1 && column <= width - 2;
    }
    @Override public int getViewDistance() { return 48; }
    @Override public void extractRenderState(MachineBlockEntity machine, MachineRenderState state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine, state, partialTick, cameraPosition, breakProgress);
        state.kind = machine.kind();
        state.facing = machine.getBlockState().getValue(MachineBlock.FACING);
        state.industrialWidth = machine.industrialWidth(); state.industrialHeight = machine.industrialHeight();
        state.industrialDepth = machine.industrialDepth(); state.industrialControllerColumn = machine.industrialControllerColumn();
        state.industrialFormed = machine.industrialFormed() && validIndustrialShape(machine.kind(),
                state.industrialWidth, state.industrialHeight, state.industrialDepth, state.industrialControllerColumn);
        state.active = machine.getBlockState().getValue(MachineBlock.ACTIVE)
                && (!IndustrialStructure.isIndustrial(machine.kind()) || state.industrialFormed);
        state.cellInstalled = machine.kind() == MachineKind.DRIVE && machine.hasVisibleCell();
        if (machine.kind() == MachineKind.DRIVE) {
            state.phase = 0;
            state.drive = state.cellInstalled ? 1 : 0;
            return;
        }
        state.preparationStage = machine.preparationStage();
        state.preparationMixing = machine.preparationMixing();
        if (PreparationTier.isPreparation(machine.kind())) {
            var parts = preparationMotion.computeIfAbsent(machine, key -> new MachineMotion[] {
                    new MachineMotion(key.getBlockPos().asLong()), new MachineMotion(key.getBlockPos().asLong() + 1),
                    new MachineMotion(key.getBlockPos().asLong() + 2)});
            boolean manual = PreparationTier.manual(machine.kind());
            if (machine.getLevel() != null) {
                double time = machine.getLevel().getGameTime() + (double) partialTick;
                parts[0].advance(time, state.active && state.preparationStage == 0);
                // Single-input recipes sieve, then collect. Two-input recipes mix at
                // stage 1 and pack at stage 2; the manual screen is driven by the crank.
                parts[1].advance(time, state.active && (state.preparationStage == 2
                        || state.preparationStage == 1 && (manual || !state.preparationMixing)));
                parts[2].advance(time, state.active && !manual && state.preparationMixing && state.preparationStage == 1);
                MachineMotion drive = motion.computeIfAbsent(machine, key -> new MachineMotion(key.getBlockPos().asLong()));
                drive.advance(time, state.active && state.preparationStage >= 0);
                state.phase = drive.phase();
                state.drive = drive.drive();
            }
            state.crusherPhase = parts[0].phase(); state.sievePhase = parts[1].phase(); state.mixerPhase = parts[2].phase();
            return;
        }
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
            case PREPARATOR, HAND_PREPARATOR -> {
                boolean manual = state.kind == MachineKind.HAND_PREPARATOR;
                float crushingAngle = (manual ? state.phase * 360 : state.crusherPhase * 1080);
                float rollerY = manual ? 11 : 8.9F;
                float frontZ = manual ? 6 : 4.7F;
                float backZ = manual ? 10 : 8.3F;
                for (int roller = 0; roller < 2; roller++) {
                    pose.pushPose();
                    pose.translate(0.5F, rollerY / 16, (roller == 0 ? frontZ : backZ) / 16);
                    pose.mulPose(Axis.XP.rotationDegrees(roller == 0 ? crushingAngle : -crushingAngle));
                    if (!manual) pose.scale(1, 1.35F / 1.6F, 1.35F / 1.6F);
                    part(crusher, 0, 0, 0, 0, false, false, state, pose, collector);
                    pose.popPose();
                }
                pose.pushPose();
                pose.translate((8 + Mth.sin(state.sievePhase * Mth.TWO_PI * 8) * 0.12F) / 16,
                        (manual ? 7.7F : 6.3F) / 16, (manual ? 4.8F : 6.4F) / 16);
                if (!manual) pose.scale(8.5F / 9, 1, 1);
                part(sieve, 0, 0, 0, 0, false, false, state, pose, collector);
                pose.popPose();
                if (manual) {
                    pose.pushPose();
                    pose.translate(15.1F / 16, 11F / 16, 6F / 16);
                    pose.mulPose(Axis.XP.rotationDegrees(crushingAngle));
                    part(handCrank, 0, 0, 0, 0, false, false, state, pose, collector);
                    collector.submitModelPart(handGrip, pose, RenderTypes.entitySolid(WOOD), state.lightCoords,
                            OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
                    pose.popPose();
                } else part(paddle, 8, 4.7F, 7, state.mixerPhase * 720, false, false, state, pose, collector);
            }
            case INDUSTRIAL_PREPARATOR -> renderIndustrial(state, pose, collector, true);
            case ANALYZER -> {
                float z = 7.5F + Mth.sin(cycle) * 3;
                part(scanner, 8, 11, z, 0, false, false, state, pose, collector);
                if (state.active && state.drive > 0.02F) {
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
                float stroke = eased * 3.1F;
                part(press, 8, 10 - stroke, 8, 0, false, false, state, pose, collector);
                pose.pushPose();
                pose.translate(0.5F, 14.5F / 16, 0.5F);
                pose.scale(1, 3.5F + stroke, 1);
                part(piston, 0, 0, 0, 0, false, false, state, pose, collector);
                pose.popPose();
            }
            case ELECTROLYZER -> {
                if (state.active && state.drive > 0.02F) {
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
            case INDUSTRIAL_REACTOR -> renderIndustrial(state, pose, collector, false);
            case DRIVE -> {
                if (state.cellInstalled) {
                    pose.pushPose();
                    pose.translate(0.5F, 5.8F / 16, 3.3F / 16);
                    collector.submitModelPart(cell, pose, RenderTypes.entitySolid(CELL), state.lightCoords,
                            OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
                    pose.popPose();
                    part(cellIndicator, 8, 8.5F, 3.25F, 0, false, true, state, pose, collector);
                }
            }
            default -> { }
        }
        pose.popPose();
    }

    private record IndustrialGeometryKey(int width, int height, int depth, boolean preparation) {}
    private record Bay(float x, float z, float width, float depth, float radialScale,
                       float rollerY, float sieveY, float mixerY) {}
    private record IndustrialGeometry(ModelPart frame, List<Bay> bays, float chamberHeight) {}

    /** Static supports retain their thickness; only open spans follow the actual shell size. */
    private static final class FrameBuilder {
        private final MeshDefinition mesh = new MeshDefinition();
        private final List<float[]> scales = new ArrayList<>();
        void box(float x, float y, float z, float width, float height, float depth) {
            if (width <= 0 || height <= 0 || depth <= 0) return;
            String name = "support_" + scales.size();
            mesh.getRoot().addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-2, -2, -2, 4, 4, 4), PartPose.offset(x + width / 2, y + height / 2, z + depth / 2));
            scales.add(new float[] {width / 4, height / 4, depth / 4});
        }
        ModelPart bake() {
            ModelPart root = LayerDefinition.create(mesh, 32, 32).bakeRoot();
            for (int i = 0; i < scales.size(); i++) {
                ModelPart child = root.getChild("support_" + i);
                child.xScale = scales.get(i)[0]; child.yScale = scales.get(i)[1]; child.zScale = scales.get(i)[2];
            }
            return root;
        }
    }

    private static IndustrialGeometry createIndustrialGeometry(IndustrialGeometryKey key) {
        float width = (key.width() - 2) * 16F, height = (key.height() - 2) * 16F, depth = (key.depth() - 2) * 16F;
        int columns = Math.min(3, (key.width() - 1) / 2), rows = Math.min(3, (key.depth() - 1) / 2);
        float bayWidth = width / columns, bayDepth = depth / rows;
        FrameBuilder frame = new FrameBuilder();
        List<Bay> bays = new ArrayList<>();
        frame.box(1, 0.5F, 1, width - 2, key.preparation() ? 0.6F : 1, depth - 2);
        for (float x : new float[] {1, width - 1.8F}) for (float z : new float[] {1, depth - 1.8F})
            frame.box(x, 1.1F, z, 0.8F, height - 2.3F, 0.8F);
        for (float z : new float[] {1, depth - 1.8F}) frame.box(1, height - 1.2F, z, width - 2, 0.7F, 0.8F);
        for (float x : new float[] {1, width - 1.8F}) frame.box(x, height - 1.2F, 1, 0.8F, 0.7F, depth - 2);
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            float x = (column + 0.5F) * bayWidth, z = (row + 0.5F) * bayDepth;
            float scale = Math.min(Math.min(bayWidth, bayDepth), height) / 16;
            float rollerY = height - 5 * scale, mixerY = 4.5F * scale, sieveY = height / 2 - 0.6F * scale;
            bays.add(new Bay(x, z, bayWidth, bayDepth, scale, rollerY, sieveY, mixerY));
            if (!key.preparation()) continue;
            // Repeated tall, open processing lines: crusher above screen, shallow mixer below.
            for (int side : new int[] {-1, 1}) {
                float postX = x + side * bayWidth * 0.42F;
                for (int end : new int[] {-1, 1})
                    frame.box(postX - 0.4F, 1.1F, z + end * 3.9F * scale - 0.4F, 0.8F, height - 2.3F, 0.8F);
                frame.box(postX - 0.4F, rollerY - 0.7F * scale, z - 3.9F * scale, 0.8F, 0.6F * scale, 7.8F * scale);
                for (int roller : new int[] {-1, 1}) {
                    float shaftEnd = x + side * bayWidth * 4.3F / 16;
                    frame.box(Math.min(postX, shaftEnd), rollerY - 0.6F * scale, z + roller * 1.9F * scale - 0.7F * scale,
                            Math.abs(shaftEnd - postX), 1.2F * scale, 1.4F * scale);
                }
            }
            frame.box(x - 3 * scale, 1.1F, z - 3 * scale, 6 * scale, 1.6F * scale - 1.1F, 6 * scale);
            frame.box(x - 3 * scale, 1.6F * scale, z - 3 * scale, 0.4F * scale, 3.8F * scale, 6 * scale);
            frame.box(x + 2.6F * scale, 1.6F * scale, z - 3 * scale, 0.4F * scale, 3.8F * scale, 6 * scale);
            frame.box(x - 2.6F * scale, 1.6F * scale, z + 2.6F * scale, 5.2F * scale, 3.8F * scale, 0.4F * scale);
            frame.box(x - 2.6F * scale, 1.6F * scale, z - 3 * scale, 5.2F * scale, 0.6F * scale, 0.4F * scale);
            // The lower agitator's shaft seats against a narrow carrier, clear of the moving screen.
            for (int side : new int[] {-1, 1})
                frame.box(x + side * 2.4F * scale - 0.2F * scale, 5.75F * scale, z - 2.6F * scale,
                        0.4F * scale, 0.4F * scale, 5.2F * scale);
            frame.box(x - 2.6F * scale, 5.75F * scale, z - 0.3F * scale, 5.2F * scale, 0.4F * scale, 0.6F * scale);
            for (int gx : new int[] {-1, 1}) for (int gz : new int[] {-1, 1}) {
                float bottom = mixerY + 1.25F * scale;
                frame.box(x + gx * 2.4F * scale - 0.125F * scale, bottom, z + gz * 2.4F * scale - 0.125F * scale,
                        0.25F * scale, sieveY - 0.3F - bottom, 0.25F * scale);
            }
        }
        if (!key.preparation()) {
            // A grid of upper cross-members anchors each full-height reactor shaft without an opaque ceiling.
            for (int row = 0; row < rows; row++) frame.box(1, height - 1.5F, (row + 0.5F) * bayDepth - 0.6F, width - 2, 1, 1.2F);
            for (int column = 0; column < columns; column++) frame.box((column + 0.5F) * bayWidth - 0.6F, height - 1.5F, 1, 1.2F, 1, depth - 2);
        }
        return new IndustrialGeometry(frame.bake(), List.copyOf(bays), height);
    }

    private void renderIndustrial(MachineRenderState state, PoseStack pose, SubmitNodeCollector collector, boolean preparation) {
        if (!state.industrialFormed) return;
        IndustrialGeometryKey key = new IndustrialGeometryKey(state.industrialWidth, state.industrialHeight, state.industrialDepth, preparation);
        IndustrialGeometry layout = industrialGeometry.computeIfAbsent(key, MachineRenderer::createIndustrialGeometry);
        pose.pushPose();
        // Controller may occupy any non-edge front column. Geometry begins at the actual inner corner.
        pose.translate(1 - state.industrialControllerColumn, 0, 1);
        ceramic(layout.frame(), 0, 0, 0, state, pose, collector);
        int index = 0;
        for (Bay bay : layout.bays()) {
            if (preparation) {
                for (int roller = 0; roller < 2; roller++) {
                    pose.pushPose();
                    pose.translate(bay.x() / 16, bay.rollerY() / 16, (bay.z() + (roller == 0 ? -1.9F : 1.9F) * bay.radialScale()) / 16);
                    pose.mulPose(Axis.XP.rotationDegrees(state.crusherPhase * (roller == 0 ? 1080 : -1080)));
                    pose.scale(bay.width() / 16, bay.radialScale(), bay.radialScale());
                    part(crusher, 0, 0, 0, 0, false, false, state, pose, collector);
                    pose.popPose();
                }
                pose.pushPose();
                pose.translate((bay.x() + Mth.sin(state.sievePhase * Mth.TWO_PI * 8) * 0.12F * bay.width() / 16) / 16,
                        bay.sieveY() / 16, bay.z() / 16);
                pose.scale(bay.width() / 16, 1, bay.depth() / 16);
                part(sieve, 0, 0, 0, 0, false, false, state, pose, collector);
                pose.popPose();
                pose.pushPose();
                pose.translate(bay.x() / 16, bay.mixerY() / 16, bay.z() / 16);
                pose.scale(bay.radialScale(), bay.radialScale(), bay.radialScale());
                part(paddle, 0, 0, 0, state.mixerPhase * 720, false, false, state, pose, collector);
                pose.popPose();
            } else {
                float radiusScale = (Math.min(bay.width(), bay.depth()) - 3) / 13;
                pose.pushPose();
                pose.translate(bay.x() / 16, layout.chamberHeight() / 32, bay.z() / 16);
                pose.scale(radiusScale, (layout.chamberHeight() - 3) / 29, radiusScale);
                part(industrialSpindle, 0, 0, 0, state.phase * 720 + index * 30, false, false, state, pose, collector);
                pose.popPose();
            }
            index++;
        }
        pose.popPose();
    }

    private static void ceramic(ModelPart model, float x, float y, float z,
            MachineRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(x / 16, y / 16, z / 16);
        collector.submitModelPart(model, pose, RenderTypes.entitySolid(CERAMIC), state.lightCoords,
                OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
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
