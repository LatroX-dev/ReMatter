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
    /** A mounted cell whose contents the Drive can read. */
    public boolean readable() { return this == EMPTY || this == STORED || this == FULL; }
    /** Whether any bay of a packed status word, three bits per bay, holds a readable cell. */
    public static boolean anyReadable(int statuses) {
        for (int packed = statuses; packed != 0; packed >>>= 3) if (decode(packed & 7).readable()) return true;
        return false;
    }
}
