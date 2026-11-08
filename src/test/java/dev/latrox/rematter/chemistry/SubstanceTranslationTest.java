package dev.latrox.rematter.chemistry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Every substance needs exactly one English display name, and no name may outlive its substance. */
class SubstanceTranslationTest {
    private static final String PREFIX = "substance.rematter.";

    @Test
    void everyBuiltinAndShippedSubstanceHasADisplayNameAndNoStaleNamesRemain() throws Exception {
        Set<String> required = new TreeSet<>();
        BuiltinChemistry.create().substances().forEach(substance -> required.add(substance.translationKey()));
        try (var files = Files.walk(Path.of("src/main/resources/data/rematter/rematter/substance"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                required.add(read(file).get("translation_key").getAsString());
            }
        }
        JsonObject language = read(Path.of("src/main/resources/assets/rematter/lang/en_us.json"));
        Set<String> missing = new TreeSet<>(required);
        missing.removeIf(key -> language.has(key) && !language.get(key).getAsString().isBlank());
        assertEquals(Set.of(), missing, "Substances without a display name");
        Set<String> named = language.keySet().stream().filter(key -> key.startsWith(PREFIX))
                .collect(Collectors.toCollection(TreeSet::new));
        named.removeAll(required);
        assertEquals(Set.of(), named, "Display names without a substance");
    }

    @Test
    void displayNamesAreUniqueAcrossSubstances() throws Exception {
        JsonObject language = read(Path.of("src/main/resources/assets/rematter/lang/en_us.json"));
        Set<String> names = new HashSet<>();
        for (String key : language.keySet()) {
            if (key.startsWith(PREFIX)) assertTrue(names.add(language.get(key).getAsString()), "Duplicate display name for " + key);
        }
    }

    private static JsonObject read(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
