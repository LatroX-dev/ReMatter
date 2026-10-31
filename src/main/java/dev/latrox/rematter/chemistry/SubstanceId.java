package dev.latrox.rematter.chemistry;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, language-independent identity. Display names never serve as storage keys. */
public record SubstanceId(String namespace, String path) implements Comparable<SubstanceId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public SubstanceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid substance ID: " + namespace + ":" + path);
        }
    }

    public static SubstanceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("A substance ID requires namespace:path: " + value);
        }
        return new SubstanceId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public int compareTo(SubstanceId other) {
        int namespaceOrder = namespace.compareTo(other.namespace);
        return namespaceOrder != 0 ? namespaceOrder : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
