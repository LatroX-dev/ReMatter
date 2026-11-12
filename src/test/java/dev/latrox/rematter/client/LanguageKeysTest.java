package dev.latrox.rematter.client;

import com.google.gson.JsonParser;
import dev.latrox.rematter.content.TutorialBookItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every fixed ReMatter key that the code translates exists in the English language file, and so does every book page. */
class LanguageKeysTest {
    @Test void screenKeysMustBeReferencedOrBelongToAnExplicitDynamicPrefix() throws IOException {
        var literals=new java.util.HashSet<String>();var prefixes=new java.util.HashSet<String>();
        var string=Pattern.compile("\"([^\"\\n]*)\"");
        try(var files=Files.walk(Path.of("src/main/java"))) {
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList()) {
                var source=Files.readString(file);var match=string.matcher(source);
                while(match.find())literals.add(match.group(1));
                var prefix=PREFIX.matcher(source);while(prefix.find())prefixes.add(prefix.group(2));
                if(file.getFileName().toString().equals("ProcessSelectionScreen.java")) {
                    var buttons=Pattern.compile("button\\((?:Icon\\.[A-Z_]+|null),\\s*\"([a-z0-9_]+)\"").matcher(source);
                    while(buttons.find())literals.add("screen.rematter.recipes."+buttons.group(1)+"_hint");
                }
            }
        }
        var used=new java.util.HashSet<>(literals);
        for(String prefix:prefixes)for(String literal:literals)used.add(prefix+literal);
        var dynamic=Set.of("screen.rematter.transfer.status.","screen.rematter.energy_cube.mode.","screen.rematter.energy_cube.meaning.","screen.rematter.backend_","screen.rematter.filter_","screen.rematter.source_","screen.rematter.help_structure","screen.rematter.settings.section.","screen.rematter.settings.section_hint.","screen.rematter.research.tab.","screen.rematter.research.tab_hint.","screen.rematter.research.state.","screen.rematter.research.help.","screen.rematter.research.learning.");
        var unused=englishKeys().stream().filter(k->k.startsWith("screen.rematter.")&&!used.contains(k)
                &&dynamic.stream().noneMatch(k::startsWith)).sorted().toList();
        assertEquals(List.of(),unused,"Unreferenced screen translations");
    }
    private static final Pattern TRANSLATABLE = Pattern.compile("Component\\.translatable(?:WithFallback)?\\(\"([a-z0-9_.]+)\"\\s*[,)]");
    private static final Pattern PREFIX = Pattern.compile("Component (tr|ui)\\(String key[^)]*\\)\\s*\\{\\s*return Component\\.translatable\\(\"([a-z0-9_.]+)\"\\s*\\+\\s*key");
    private static final Pattern SHORT = Pattern.compile("\\btr\\(\"([a-z0-9_.]+)\"\\s*[,)]");

    @Test void everyLiteralKeyIsTranslated() throws IOException {
        Set<String> keys = englishKeys();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var literal = TRANSLATABLE.matcher(source);
                while (literal.find()) check(keys, missing, file, literal.group(1));
                var prefix = PREFIX.matcher(source);
                while (prefix.find()) {
                    var shortKey = Pattern.compile("\\b"+prefix.group(1)+"\\(\"([a-z0-9_.]+)\"\\s*[,)]").matcher(source);
                    while (shortKey.find()) check(keys, missing, file, prefix.group(2) + shortKey.group(1));
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test void everyTutorialBookPageIsTranslated() throws IOException {
        var pages = englishKeys().stream().filter(key -> key.startsWith("guide.rematter.page.")).collect(Collectors.toSet());
        var expected = IntStream.rangeClosed(1, TutorialBookItem.PAGE_COUNT).mapToObj(page -> "guide.rematter.page." + page)
                .collect(Collectors.toSet());
        assertEquals(expected, pages);
    }

    @Test void everyCellTransferStatusIsTranslated() throws IOException {
        var keys=englishKeys();
        for(var status:dev.latrox.rematter.network.CellTransferPayload.STATUSES)
            org.junit.jupiter.api.Assertions.assertTrue(keys.contains("screen.rematter.transfer.status."+status),status);
    }

    private static Set<String> englishKeys() throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/rematter/lang/en_us.json")))
                .getAsJsonObject().keySet();
    }

    private static void check(Set<String> keys, List<String> missing, Path file, String key) {
        if (key.contains(".rematter.") && !keys.contains(key)) missing.add(file.getFileName() + ": " + key);
    }
}
