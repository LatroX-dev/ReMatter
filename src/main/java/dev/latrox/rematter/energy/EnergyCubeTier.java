package dev.latrox.rematter.energy;

/** Physical buffers and aggregate transfer limits, independent of chemistry and research. */
public enum EnergyCubeTier {
    BASIC("energy_cube", 8_000_000L, 8_192, 1),
    REINFORCED("reinforced_energy_cube", 64_000_000L, 65_536, 2),
    RESONANT("resonant_energy_cube", 512_000_000L, 524_288, 4),
    PRIMAL("primal_energy_cube", 4_096_000_000L, 4_194_304, 4);

    private final String id;
    private final long capacity;
    private final int rate, slots;

    EnergyCubeTier(String id, long capacity, int rate, int slots) {
        this.id = id;
        this.capacity = capacity;
        this.rate = rate;
        this.slots = slots;
    }

    public String id() { return id; }
    public long capacity() { return capacity; }
    public int transferRate() { return rate; }
    public int chargingSlots() { return slots; }
    public int dischargingSlots() { return slots; }
    public int totalSlots() { return slots * 2; }

    public static EnergyCubeTier byIndex(int index) {
        if (index < 0 || index >= values().length) throw new IllegalArgumentException("Unknown energy cube tier");
        return values()[index];
    }
}
