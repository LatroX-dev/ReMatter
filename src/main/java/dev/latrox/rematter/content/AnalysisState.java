package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.SubstanceId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;

/** A completed inspection is valid only for the exact sample and material profile that was scanned. */
public record AnalysisState(String itemId, String profileFingerprint) {
    public static final int SCHEMA_VERSION = 1;
    public static final AnalysisState EMPTY = new AnalysisState("", "");

    public AnalysisState {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(profileFingerprint);
        if (!itemId.isEmpty() || !profileFingerprint.isEmpty()) {
            SubstanceId.parse(itemId);
            if (!profileFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid analysis fingerprint");
        }
    }

    public static AnalysisState completed(MaterialProfile profile) {
        return new AnalysisState(profile.itemId(), fingerprint(profile));
    }

    /** Unsupported/malformed derived data is discarded; no resource or processing state is reconstructed. */
    public static AnalysisState restore(int schema, String itemId, String fingerprint) {
        if (schema != SCHEMA_VERSION) return EMPTY;
        try { return new AnalysisState(itemId, fingerprint); }
        catch (IllegalArgumentException invalid) { return EMPTY; }
    }

    public boolean isEmpty() { return itemId.isEmpty(); }

    public boolean matches(String currentItemId, boolean componentFree, MaterialProfile currentProfile) {
        return !isEmpty() && componentFree && itemId.equals(currentItemId) && currentProfile != null
                && itemId.equals(currentProfile.itemId()) && profileFingerprint.equals(fingerprint(currentProfile));
    }

    private static String fingerprint(MaterialProfile profile) {
        // Every field is unambiguously delimited; identifiers and enum names cannot contain newlines.
        StringBuilder canonical = new StringBuilder("analysis-profile-v1\n")
                .append(profile.itemId()).append('\n').append(profile.itemCount()).append('\n')
                .append(profile.decompositionEnergy()).append('\n').append(profile.synthesisEnergy()).append('\n')
                .append(profile.ticks()).append('\n');
        new TreeMap<>(profile.matter()).forEach((key, amount) -> canonical.append(key.id()).append('\n')
                .append(key.phase().name()).append('\n').append(amount).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
