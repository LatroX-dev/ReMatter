package dev.latrox.rematter.content;

import java.util.List;
import java.util.Objects;

/** Keeps removed recipe identities instead of selecting a different product on reload. */
public record RecipeSelection(int index, String id) {
    public static RecipeSelection restore(String savedId, List<String> availableIds) {
        Objects.requireNonNull(savedId);
        if (savedId.isEmpty()) return availableIds.isEmpty()
                ? new RecipeSelection(-1, "") : new RecipeSelection(0, availableIds.getFirst());
        return new RecipeSelection(availableIds.indexOf(savedId), savedId);
    }

    public boolean unavailable() { return index < 0 && !id.isEmpty(); }
}
