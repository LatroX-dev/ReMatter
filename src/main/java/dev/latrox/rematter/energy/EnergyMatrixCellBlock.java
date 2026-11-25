package dev.latrox.rematter.energy;

import net.minecraft.world.level.block.Block;

/** Stackable interior capacity block for the Energy Matrix multiblock. */
public final class EnergyMatrixCellBlock extends Block {
    private final EnergyMatrixCellTier tier;

    public EnergyMatrixCellBlock(EnergyMatrixCellTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public EnergyMatrixCellTier tier() {
        return tier;
    }
}
