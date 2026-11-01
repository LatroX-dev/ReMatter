package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.*;

/** Recognizes only the exact released internal-storage format. Unknown fields remain opaque. */
final class LegacyMatterData {
    private LegacyMatterData() {}
    static Optional<MatterInventory> open(Tag raw) {
        try {
            if (!(raw instanceof CompoundTag tag) || !tag.keySet().equals(Set.of("schema", "capacity", "contents"))
                    || !(tag.get("schema") instanceof IntTag) || tag.getIntOr("schema", -1) != 1
                    || !(tag.get("capacity") instanceof LongTag) || !(tag.get("contents") instanceof ListTag rows))
                return Optional.empty();
            var contents = new ArrayList<SubstanceStack>();
            for (var element : rows) {
                if (!(element instanceof CompoundTag row) || !row.keySet().equals(Set.of("substance", "phase", "amount"))
                        || !(row.get("amount") instanceof LongTag)) return Optional.empty();
                contents.add(new SubstanceStack(new SubstanceKey(SubstanceId.parse(row.getString("substance").orElseThrow()),
                        Phase.valueOf(row.getString("phase").orElseThrow())), row.getLong("amount").orElseThrow()));
            }
            return Optional.of(MatterInventory.restore(new MatterInventory.Snapshot(1, tag.getLong("capacity").orElseThrow(), contents, List.of())));
        } catch (RuntimeException unknown) { return Optional.empty(); }
    }
}
