package dev.latrox.rematter.content;

/** Coarse public indicator only; no substance identities or exact amounts leave the server. */
public enum CellStatus {
    ABSENT, EMPTY, STORED, FULL, LOCKED;
    private static final CellStatus[] VALUES=values();

    public static CellStatus of(boolean installed, boolean readable, long total, long capacity, int types, int maxTypes) {
        if (!installed) return ABSENT;
        if (!readable) return LOCKED;
        if (total == 0) return EMPTY;
        return total >= capacity || types >= maxTypes ? FULL : STORED;
    }
    public static CellStatus decode(int value) {
        return value >= 0 && value < VALUES.length ? VALUES[value] : LOCKED;
    }
}
