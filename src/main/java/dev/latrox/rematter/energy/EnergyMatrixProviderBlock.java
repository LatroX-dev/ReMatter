package dev.latrox.rematter.energy;

import net.minecraft.world.level.block.Block;

/** Stackable interior induction transfer block for the Energy Matrix multiblock. */
public final class EnergyMatrixProviderBlock extends Block {
    private final EnergyMatrixProviderTier tier;

    public EnergyMatrixProviderBlock(EnergyMatrixProviderTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public EnergyMatrixProviderTier tier() {
        return tier;
    }
}
