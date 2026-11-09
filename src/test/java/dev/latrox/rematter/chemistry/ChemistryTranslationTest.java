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

/** Every substance and process needs exactly one English display name, and no name may outlive its definition. */
class ChemistryTranslationTest {
    private static final String PREFIX = "substance.rematter.";
    private static final String PROCESS_PREFIX = "process.rematter.";

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
    void everyBuiltinAndShippedProcessHasADisplayNameAndNoStaleNamesRemain() throws Exception {
        Set<String> required = new TreeSet<>();
        BuiltinChemistry.create().processes().forEach(process -> required.add(process.translationKey()));
        assertFalse(required.isEmpty());
        try (var namespaces = Files.list(Path.of("src/main/resources/data"))) {
            for (Path namespace : namespaces.toList()) {
                Path folder = namespace.resolve("rematter/process");
                if (!Files.isDirectory(folder)) continue;
                try (var files = Files.walk(folder)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                        String path = folder.relativize(file).toString().replace('\\', '/');
                        required.add(ProcessRecipe.translationKey(SubstanceId.parse(
                                namespace.getFileName() + ":" + path.substring(0, path.length() - ".json".length()))));
                    }
                }
            }
        }
        ShippedChemistry.standalone().environmentSources().forEach(source->required.add(source.translationKey()));
        JsonObject language = read(Path.of("src/main/resources/assets/rematter/lang/en_us.json"));
        Set<String> missing = new TreeSet<>(required);
        missing.removeIf(key -> language.has(key) && !language.get(key).getAsString().isBlank());
        assertEquals(Set.of(), missing, "Processes without a display name");
        Set<String> named = language.keySet().stream().filter(key -> key.startsWith(PROCESS_PREFIX))
                .collect(Collectors.toCollection(TreeSet::new));
        named.removeAll(required);
        assertEquals(Set.of(), named, "Display names without a process");
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
