package dev.latrox.rematter.content.generation;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import org.jspecify.annotations.Nullable;

/** The unspent part of one fully consumed crystal batch, separate from the machine FE buffer. */
public final class CrystalBurner {
    public static final int SCHEMA = 1;
    private long remaining, revision;
    private @Nullable CompoundTag locked;
    public long remaining() { return readable() ? remaining : 0; }
    public boolean readable() { return locked == null; }
    public static final class Plan {
        private final long expectedRevision, expectedRemaining, producedEnergy, nextRemaining;
        private final int consumedItems;
        private Plan(long revision, long before, int consumed, long produced, long after) {
            expectedRevision = revision; expectedRemaining = before; consumedItems = consumed; producedEnergy = produced; nextRemaining = after;
        }
        public int consumedItems() { return consumedItems; }
        public long producedEnergy() { return producedEnergy; }
        public long nextRemaining() { return nextRemaining; }
        public boolean changesState() { return consumedItems != 0 || producedEnergy != 0; }
    }
    /** Planning is pure; the owner validates its item/FE state and commits all three together. */
    public Plan tick(long bufferSpace, int rate, CrystalFuelData.@Nullable Fuel fuel, int availableItems) {
        if (bufferSpace < 0 || rate < 1 || availableItems < 0) throw new IllegalArgumentException("Invalid generator tick");
        if (!readable() || bufferSpace == 0) return new Plan(revision, remaining, 0, 0, remaining);
        long energy = remaining; int consumed = 0;
        if (energy == 0 && fuel != null && availableItems >= fuel.count()) { energy = fuel.energy(); consumed = fuel.count(); }
        long produced = Math.min(Math.min(bufferSpace, rate), energy);
        return new Plan(revision, remaining, consumed, produced, energy - produced);
    }
    public boolean apply(Plan plan) {
        if (!readable() || plan.expectedRevision != revision || plan.expectedRemaining != remaining) return false;
        if (plan.changesState()) { remaining = plan.nextRemaining; revision++; }
        return true;
    }
    public CompoundTag save() {
        if (locked != null) return locked.copy();
        var tag = new CompoundTag(); tag.putInt("schema", SCHEMA); tag.putLong("remaining", remaining); return tag;
    }
    /** Malformed and future layouts remain exactly present and cannot generate power. */
    public void load(CompoundTag raw) {
        revision++; remaining = 0; locked = raw.copy();
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA || !(raw.get("remaining") instanceof LongTag)
                || !raw.keySet().equals(Set.of("schema", "remaining"))) return;
        long value = raw.getLong("remaining").orElse(-1L);
        if (value < 0 || value > 1_000_000_000L) return;
        remaining = value; locked = null;
    }
}
