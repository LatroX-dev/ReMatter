package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.celltransfer.CellTransferStationBlock;
import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Mounted Cells share the Drive's faceplates and lamps; only public tier/status bits reach the render state. */
public final class CellTransferStationRenderer implements BlockEntityRenderer<CellTransferStationBlockEntity,CellTransferStationRenderState> {
    private final SpriteGetter sprites;
    private final BlockPos.MutableBlockPos front=new BlockPos.MutableBlockPos();
    private @Nullable SpriteUv cellSprites;

    public CellTransferStationRenderer(BlockEntityRendererProvider.Context context){sprites=context.sprites();}
    @Override public CellTransferStationRenderState createRenderState(){return new CellTransferStationRenderState();}
    @Override public int getViewDistance(){return 64;}
    @Override public void extractRenderState(CellTransferStationBlockEntity station,CellTransferStationRenderState state,float partialTick,
            Vec3 cameraPosition,ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress){
        BlockEntityRenderer.super.extractRenderState(station,state,partialTick,cameraPosition,breakProgress);
        state.facing=station.getBlockState().getValue(CellTransferStationBlock.FACING);
        state.cardinal=station.getLevel() instanceof BlockAndTintGetter view?view.cardinalLighting():CardinalLighting.DEFAULT;
        state.cellMask=station.visibleCellMask()&63;
        // Whole assignments prevent a reused state retaining a removed Cell's tier or lamp.
        int tiers=0,statuses=0;
        for(int bay=0;bay<6;bay++){
            tiers|=Math.clamp(station.visibleCellTier(bay),0,7)<<(bay*3);
            statuses|=station.visibleCellStatus(bay).ordinal()<<(bay*3);
        }
        state.cellTiers=tiers;state.cellStatuses=statuses;
        state.frontLight=station.getLevel()==null?state.lightCoords:LevelRenderer.getLightCoords(station.getLevel(),front.setWithOffset(station.getBlockPos(),state.facing));
        if(cellSprites==null)cellSprites=SpriteUv.resolve(sprites,DriveBays.SPRITES);
        state.sprites=cellSprites;
    }
    @Override public void submit(CellTransferStationRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.cellMask==0||state.sprites==null)return;
        pose.pushPose();pose.translate(.5F,0,.5F);pose.mulPose(Axis.YP.rotationDegrees(180-state.facing.toYRot()));pose.translate(-.5F,0,-.5F);
        collector.submitCustomGeometry(pose,RenderTypes.solidMovingBlock(),state);pose.popPose();
    }
}
