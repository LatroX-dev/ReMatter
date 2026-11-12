package dev.latrox.rematter.energy;

/** Physical buffers and aggregate transfer limits, independent of chemistry and research. */
public enum EnergyCubeTier {
    BASIC("energy_cube",4_000_000,2_048,1),
    REINFORCED("reinforced_energy_cube",32_000_000,16_384,2),
    RESONANT("resonant_energy_cube",256_000_000,131_072,4);

    private final String id;
    private final int capacity,rate,slots;
    EnergyCubeTier(String id,int capacity,int rate,int slots){this.id=id;this.capacity=capacity;this.rate=rate;this.slots=slots;}
    public String id(){return id;}
    public int capacity(){return capacity;}
    public int transferRate(){return rate;}
    public int chargingSlots(){return slots;}
    public static EnergyCubeTier byIndex(int index){
        if(index<0||index>=values().length)throw new IllegalArgumentException("Unknown energy cube tier");
        return values()[index];
    }
}
