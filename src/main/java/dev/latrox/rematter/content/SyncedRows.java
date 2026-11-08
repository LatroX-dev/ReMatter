package dev.latrox.rematter.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Keyed rows synchronised as changes only. The server diffs the current rows against what it last sent;
 * the client applies the same changes to its copy. Rows are kept in key order on both sides.
 */
public final class SyncedRows<K extends Comparable<? super K>, V> {
    public record Delta<K, V>(List<V> changed, List<K> removed) {
        public Delta { changed = List.copyOf(changed); removed = List.copyOf(removed); }
        public boolean isEmpty() { return changed.isEmpty() && removed.isEmpty(); }
    }

    private final Function<V, K> key;
    private final TreeMap<K, V> rows = new TreeMap<>();

    public SyncedRows(Function<V, K> key) { this.key = Objects.requireNonNull(key); }

    /** Replaces the rows and returns the changes since the previous state. */
    public Delta<K, V> update(Collection<V> next) {
        var nextRows = new TreeMap<K, V>();
        for (V row : next) nextRows.put(key.apply(row), row);
        var changed = new ArrayList<V>();
        var removed = new ArrayList<K>();
        nextRows.forEach((rowKey, row) -> { if (!row.equals(rows.get(rowKey))) changed.add(row); });
        for (K rowKey : rows.keySet()) if (!nextRows.containsKey(rowKey)) removed.add(rowKey);
        rows.clear();
        rows.putAll(nextRows);
        return new Delta<>(changed, removed);
    }

    public void apply(boolean reset, List<V> changed, List<K> removed) {
        if (reset) rows.clear();
        removed.forEach(rows::remove);
        for (V row : changed) rows.put(key.apply(row), row);
    }

    public @Nullable V get(K rowKey) { return rows.get(rowKey); }
    public void clear() { rows.clear(); }
    public int size() { return rows.size(); }
    public List<V> values() { return List.copyOf(rows.values()); }
}
