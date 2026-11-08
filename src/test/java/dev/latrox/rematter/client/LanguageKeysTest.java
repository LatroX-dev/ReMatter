package dev.latrox.rematter.client;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every fixed ReMatter key that the code translates exists in the English language file. */
class LanguageKeysTest {
    private static final Pattern TRANSLATABLE = Pattern.compile("Component\\.translatable(?:WithFallback)?\\(\"([a-z0-9_.]+)\"\\s*[,)]");
    private static final Pattern PREFIX = Pattern.compile("Component tr\\(String key[^)]*\\)\\s*\\{\\s*return Component\\.translatable\\(\"([a-z0-9_.]+)\" \\+ key");
    private static final Pattern SHORT = Pattern.compile("\\btr\\(\"([a-z0-9_.]+)\"\\s*[,)]");

    @Test void everyLiteralKeyIsTranslated() throws IOException {
        Set<String> keys = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/rematter/lang/en_us.json")))
                .getAsJsonObject().keySet();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var literal = TRANSLATABLE.matcher(source);
                while (literal.find()) check(keys, missing, file, literal.group(1));
                var prefix = PREFIX.matcher(source);
                if (!prefix.find()) continue;
                var shortKey = SHORT.matcher(source);
                while (shortKey.find()) check(keys, missing, file, prefix.group(1) + shortKey.group(1));
            }
        }
        assertEquals(List.of(), missing);
    }

    private static void check(Set<String> keys, List<String> missing, Path file, String key) {
        if (key.contains(".rematter.") && !keys.contains(key)) missing.add(file.getFileName() + ": " + key);
    }
}
