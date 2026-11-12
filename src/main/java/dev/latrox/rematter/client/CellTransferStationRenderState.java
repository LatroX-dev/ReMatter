package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;
import org.jspecify.annotations.Nullable;

/** Six coarse mounted-cell appearances only; no stored substances, quantities, stacks, filters or owners. */
public final class CellTransferStationRenderState extends BlockEntityRenderState implements SubmitNodeCollector.CustomGeometryRenderer {
    public Direction facing=Direction.NORTH;
    public int cellMask,cellTiers,cellStatuses,frontLight;
    public CardinalLighting cardinal=CardinalLighting.DEFAULT;
    public @Nullable SpriteUv sprites;

    @Override public void render(PoseStack.Pose pose,VertexConsumer buffer){
        if(sprites!=null)DriveBays.emit(pose,buffer,cellMask,cellTiers,cellStatuses,sprites,
                ARGB.gray(cardinal.byFace(facing)),frontLight);
    }
}
