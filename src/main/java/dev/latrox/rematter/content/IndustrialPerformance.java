package dev.latrox.rematter.content;

import dev.latrox.rematter.energy.EnergyMatrixCellTier;
import dev.latrox.rematter.energy.EnergyMatrixProviderTier;

/** Installed hardware grants capabilities and simultaneous capacity; upgrades control speed and FE. */
public record IndustrialPerformance(int grinding, int mixing, int separation, int coils, int chambers,
                                    int emitters, int condensers, int fuelCores, int inductionCoils,
                                    int matrixCells, int matrixProviders, long matrixCapacity, long matrixTransferRate) {
    public static final int MAX_PARTS_PER_TYPE = 2 * (IndustrialStructure.MAX_SIZE - 2) * (IndustrialStructure.MAX_SIZE - 2);
    public static final int MAX_MATRIX_PARTS = (IndustrialStructure.MAX_SIZE - 2)
            * (IndustrialStructure.MAX_SIZE - 2) * (IndustrialStructure.MAX_SIZE - 2);
    public static final IndustrialPerformance EMPTY = new IndustrialPerformance(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L);

    public IndustrialPerformance(int grinding, int mixing, int separation, int coils, int chambers) {
        this(grinding, mixing, separation, coils, chambers, 0, 0, 0, 0, 0, 0, 0L, 0L);
    }

    public IndustrialPerformance(int grinding, int mixing, int separation, int coils, int chambers, int emitters, int condensers) {
        this(grinding, mixing, separation, coils, chambers, emitters, condensers, 0, 0, 0, 0, 0L, 0L);
    }

    public static IndustrialPerformance energyReactor(int fuelCores, int inductionCoils) {
        return new IndustrialPerformance(0, 0, 0, 0, 0, 0, 0, fuelCores, inductionCoils, 0, 0, 0L, 0L);
    }

    public static IndustrialPerformance energyMatrix(int cells, int providers, long capacity, long transferRate) {
        return new IndustrialPerformance(0, 0, 0, 0, 0, 0, 0, 0, 0, cells, providers, capacity, transferRate);
    }

    /** Starter blueprint estimate using Tier-1 Basic Cells and Basic Providers. */
    public static IndustrialPerformance energyMatrixBasic(int cells, int providers) {
        return energyMatrix(cells, providers,
                Math.multiplyExact(EnergyMatrixCellTier.BASIC.capacity(), cells),
                Math.multiplyExact(EnergyMatrixProviderTier.BASIC.transferRate(), providers));
    }

    public IndustrialPerformance {
        for (int count : new int[]{grinding, mixing, separation, coils, chambers, emitters, condensers,
                fuelCores, inductionCoils}) {
            if (count < 0 || count > MAX_PARTS_PER_TYPE) throw new IllegalArgumentException("Invalid industrial part count");
        }
        for (int count : new int[]{matrixCells, matrixProviders}) {
            if (count < 0 || count > MAX_MATRIX_PARTS) throw new IllegalArgumentException("Invalid matrix part count");
        }
        if (matrixCapacity < 0L || matrixTransferRate < 0L) {
            throw new IllegalArgumentException("Invalid matrix energy parameters");
        }
    }

    public int workRate(MachineKind kind) {
        return switch (kind) {
            case INDUSTRIAL_PREPARATOR -> grinding > 0 && mixing > 0 && separation > 0 ? 4 : 0;
            case INDUSTRIAL_REACTOR, INDUSTRIAL_DECOMPOSER, INDUSTRIAL_SYNTHESIZER -> coils > 0 && chambers > 0 ? 2 : 0;
            case TRANSMUTATION_REACTOR -> coils > 0 && emitters > 0 ? 2 : 0;
            case PRIMAL_REACTOR -> emitters > 0 && condensers > 0 ? 2 : 0;
            case ENERGY_MATRIX -> matrixCells > 0 && matrixProviders > 0 ? 2 : 0;
            case ENERGY_REACTOR -> fuelCores > 0 && inductionCoils > 0 ? 2 : 0;
            default -> 0;
        };
    }

    public int field() { return coils + emitters; }
    public int precision() { return separation; }
    /** Space for all simultaneous recipe inputs, with no yield or speed multiplier. */
    public long processRoom() { return 16_000L * (chambers + emitters + condensers); }
    /** 64-bit internal buffer capacity of an Energy Reactor. */
    public long reactorCapacity() { return EnergyReactorRecipes.bufferCapacity(fuelCores); }
    /** Maximum FE/t extraction and push rate of an Energy Reactor. */
    public long reactorMaxOutputRate() { return EnergyReactorRecipes.maxOutputRate(inductionCoils); }
    public boolean supportsInputUnits(long simultaneousInputs) {
        return simultaneousInputs >= 0 && simultaneousInputs <= processRoom();
    }
    public int jobCapacity(java.util.Set<dev.latrox.rematter.content.preparation.PreparationStep> steps) {
        if (steps.isEmpty()) return 0;
        int result = MAX_PARTS_PER_TYPE;
        for (var step : steps) result = Math.min(result, switch (step) {
            case GRIND -> grinding;
            case MIX -> mixing;
            case SEPARATE -> separation;
        });
        return result;
    }

    /** Hardware never discounts power. Kept as the single policy hook used by FE pricing. */
    public int energySavingPercent(MachineKind kind) {
        return 0;
    }

    /** Hardware leaves the exact FE debit to the controller's upgrade policy. */
    public long energyCost(MachineKind kind, long energy) {
        if (energy < 0) throw new IllegalArgumentException("Negative energy");
        return energy;
    }
}
