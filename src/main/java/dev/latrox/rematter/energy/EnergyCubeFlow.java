package dev.latrox.rematter.energy;

/** Samples net buffer movement; no transfer or accounting code reads this visual state. */
final class EnergyCubeFlow {
    private long sampledAt = Long.MIN_VALUE, changedAt = Long.MIN_VALUE;
    private int sampledEnergy, previousSign, matchingWindows;
    private EnergyCubeBlock.Flow shown = EnergyCubeBlock.Flow.NONE;

    EnergyCubeBlock.Flow sample(long tick, int energy) {
        if (sampledAt == Long.MIN_VALUE || tick < sampledAt) {
            sampledAt = tick;
            sampledEnergy = energy;
            previousSign = 0;
            matchingWindows = 0;
            return shown;
        }
        if (tick - sampledAt < 10) return shown;
        int sign = Integer.compare(energy, sampledEnergy);
        sampledAt = tick;
        sampledEnergy = energy;
        matchingWindows = sign == previousSign ? matchingWindows + 1 : 1;
        previousSign = sign;
        if (matchingWindows >= 2 && (changedAt == Long.MIN_VALUE || tick - changedAt >= 20)) {
            var next = sign > 0 ? EnergyCubeBlock.Flow.IN : sign < 0 ? EnergyCubeBlock.Flow.OUT : EnergyCubeBlock.Flow.NONE;
            if (next != shown) { shown = next; changedAt = tick; }
            matchingWindows = 2;
        }
        return shown;
    }
}
