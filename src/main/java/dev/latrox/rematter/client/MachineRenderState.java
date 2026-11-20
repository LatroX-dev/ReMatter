package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.latrox.rematter.content.MachineKind;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;
import org.jspecify.annotations.Nullable;

/**
 * Public visual state only: plain item appearances and coarse cell indicators, no counts, components, chemistry
 * stock or owner data. The state also draws the quads of the kinds that sample block sprites directly, so a frame
 * needs no further object to carry them to the deferred geometry pass.
 */
public final class MachineRenderState extends BlockEntityRenderState implements SubmitNodeCollector.CustomGeometryRenderer {
    public final ItemStackRenderState inputItem = new ItemStackRenderState();
    public final ItemStackRenderState secondaryItem = new ItemStackRenderState();
    public final ItemStackRenderState tertiaryItem = new ItemStackRenderState();
    public final ItemStackRenderState outputItem = new ItemStackRenderState();
    public final ItemStackRenderState processInputItem = new ItemStackRenderState();
    public final ItemStackRenderState processOutputItem = new ItemStackRenderState();
    public boolean hasProcess;
    public int industrialWidth, industrialHeight, industrialDepth, industrialColumn;
    public MachineKind kind = MachineKind.ANALYZER;
    public Direction facing = Direction.NORTH;
    public boolean active;
    /** Drive bays: one bit per occupied bay, then three bits per bay for the tier and for the status ordinal. */
    public int cellMask, cellTiers, cellStatuses;
    /** Packed light of the block in front of the machine, where the faces around the bays take theirs. */
    public int frontLight;
    /** Face shades of the dimension, so drawn quads match the baked faces next to them. */
    public CardinalLighting cardinal = CardinalLighting.DEFAULT;
    /** Atlas rectangles of the sprites that {@link #render} samples for this kind. */
    public @Nullable SpriteUv sprites;
    public int preparationStage;
    public boolean preparationMixing;
    public final dev.latrox.rematter.client.anim.MovingBoxes moving = new dev.latrox.rematter.client.anim.MovingBoxes();
    public double clock, turns;
    public float engage, waiting, progress, handAngle;
    public final float[] itemScales = new float[6];
    public float phase;
    public float drive;

    @Override public void render(PoseStack.Pose pose, VertexConsumer buffer) {
        if (sprites == null) return;
        switch (kind) {
            case DRIVE -> DriveBays.emit(pose, buffer, cellMask, cellTiers, cellStatuses, sprites,
                    ARGB.gray(cardinal.byFace(facing)), frontLight);
            default -> { }
        }
    }
}
