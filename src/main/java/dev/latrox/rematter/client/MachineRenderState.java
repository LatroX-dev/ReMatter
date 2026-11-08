package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

/** Public visual state only: plain item appearances, no counts, components, chemistry stock or owner data. */
public final class MachineRenderState extends BlockEntityRenderState {
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
    public int cellMask;
    public final int[] cellTiers = new int[6];
    public final ItemStackRenderState[] cellItems = {new ItemStackRenderState(),new ItemStackRenderState(),
            new ItemStackRenderState(),new ItemStackRenderState(),new ItemStackRenderState(),new ItemStackRenderState()};
    public int preparationStage;
    public boolean preparationMixing;
    public float phase;
    public float drive;
}
