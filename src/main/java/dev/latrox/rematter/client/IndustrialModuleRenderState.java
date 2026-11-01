package dev.latrox.rematter.client;

import dev.latrox.rematter.content.IndustrialModuleKind;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Public mechanical state, without inventory, owner or controller coordinates. */
public final class IndustrialModuleRenderState extends BlockEntityRenderState {
    public IndustrialModuleKind kind = IndustrialModuleKind.GRINDING_UNIT;
    public Direction facing = Direction.NORTH;
    public boolean active;
    public float phase;
    public float drive;
}
