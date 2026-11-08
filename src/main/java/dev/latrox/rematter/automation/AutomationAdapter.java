package dev.latrox.rematter.automation;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Optional native network lifecycle. Stored adapter data remains owned by the interface block. */
public interface AutomationAdapter {
    String id();
    void load(ValueInput input);
    void save(ValueOutput output);
    void tick();
    void close();
    boolean connected();
    /** Native tasks must be retained before a backend can be replaced. */
    default boolean hasTasks() { return false; }
    /** A backend-specific status key, or empty when the adapter has nothing to report. */
    default String status() { return ""; }
}
