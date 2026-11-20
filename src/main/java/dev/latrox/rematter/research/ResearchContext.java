package dev.latrox.rematter.research;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Resolved on the server for the machine owner, never for its current GUI visitor. */
public record ResearchContext(UUID owner, ResearchIdentity identity, List<ResearchIdentity> readable) {
    public ResearchContext {
        Objects.requireNonNull(owner); Objects.requireNonNull(identity); readable = List.copyOf(readable);
        if (!readable.contains(ResearchIdentity.player(owner)) || !readable.contains(identity))
            throw new IllegalArgumentException("Incomplete owner knowledge view");
    }
    public static ResearchContext personal(UUID owner) {
        var player = ResearchIdentity.player(owner);
        return new ResearchContext(owner, player, List.of(player));
    }
}
