package dev.latrox.rematter.research;

import java.util.Objects;
import java.util.UUID;

/** A durable knowledge owner. Team membership itself is always resolved afresh. */
public record ResearchIdentity(Kind kind, UUID id) {
    public enum Kind { PLAYER, TEAM, SERVER }
    public static final ResearchIdentity SERVER = new ResearchIdentity(Kind.SERVER, new UUID(0, 0));
    public ResearchIdentity { Objects.requireNonNull(kind); Objects.requireNonNull(id); }
    public static ResearchIdentity player(UUID id) { return new ResearchIdentity(Kind.PLAYER, id); }
    public static ResearchIdentity team(UUID id) { return new ResearchIdentity(Kind.TEAM, id); }
    public String key() { return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + id; }
    public static ResearchIdentity parse(String key) {
        int split = key.indexOf(':');
        if (split < 1) throw new IllegalArgumentException("Invalid research identity");
        return new ResearchIdentity(Kind.valueOf(key.substring(0, split).toUpperCase(java.util.Locale.ROOT)), UUID.fromString(key.substring(split + 1)));
    }
}
