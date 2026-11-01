package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Public visual state only: no inventory, chemistry stock or owner information. */
public final class MachineRenderState extends BlockEntityRenderState {
    public MachineKind kind = MachineKind.ANALYZER;
    public Direction facing = Direction.NORTH;
    public boolean active;
    public boolean cellInstalled;
    public float phase;
    public float drive;
    public float crusherPhase, sievePhase, mixerPhase;
    public int preparationStage = -1;
    public boolean preparationMixing;
}
