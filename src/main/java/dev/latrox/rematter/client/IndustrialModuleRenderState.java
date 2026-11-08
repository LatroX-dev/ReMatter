package dev.latrox.rematter.client;

import dev.latrox.rematter.content.IndustrialModuleKind;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Public optical state. A module has neither an inventory nor an independent process. */
public final class IndustrialModuleRenderState extends BlockEntityRenderState {
    public IndustrialModuleKind kind = IndustrialModuleKind.GRINDING_UNIT;
    public Direction facing = Direction.NORTH;
    public boolean active, emitting, suspended, formed;
    public float phase, drive, progress;
    public float targetX=.5F, targetY=.5F, targetZ=.5F;
}
