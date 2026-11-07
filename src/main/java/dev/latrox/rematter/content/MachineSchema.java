package dev.latrox.rematter.content;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The single machine data schema. Data in any other shape is kept opaque: never interpreted, truncated or deleted. */
final class MachineSchema {
    static final int VERSION = 1;

    private MachineSchema() {}

    /** Null when stored machine data can be read; otherwise the reason it must stay opaque. */
    static @Nullable String rejection(Optional<Integer> schema, Optional<String> owner, Optional<Integer> storedItems, int inventorySize) {
        if (schema.isEmpty() || schema.get() != VERSION) return "unsupported schema " + schema.map(String::valueOf).orElse("(missing)");
        if (owner.isPresent() && owner(owner.get()) == null) return "malformed owner";
        if (storedItems.isEmpty()) return "unreadable items";
        if (storedItems.get() != inventorySize) return storedItems.get() + " item slots instead of " + inventorySize;
        return null;
    }

    /** Only the canonical UUID form that this mod writes. */
    static @Nullable UUID owner(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equals(value) ? parsed : null;
        } catch (IllegalArgumentException malformed) { return null; }
    }

    /** A stored selection that could never be a choice identifier is dropped instead of reaching clients. */
    static String selection(String saved) {
        if (saved.isEmpty() || saved.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH) return "";
        String id = saved.startsWith(MachineChoices.FLUID_PREFIX) ? saved.substring(MachineChoices.FLUID_PREFIX.length()) : saved;
        return id.indexOf(':') > 0 && Identifier.tryParse(id) != null ? saved : "";
    }
}
