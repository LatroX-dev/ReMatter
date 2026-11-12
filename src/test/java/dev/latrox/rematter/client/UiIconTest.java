package dev.latrox.rematter.client;

import com.google.gson.JsonParser;
import dev.latrox.rematter.content.MachineKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Protects the vocabulary between shipped art, controls and future server status messages. */
class UiIconTest {
    @Test void everyIconHasExactlyOneShippedSpriteAndEverySpriteHasOneIcon() throws Exception {
        var icons=Arrays.stream(UiIcon.values()).map(icon->icon.sprite.getPath()).toList();
        assertEquals(icons.size(),Set.copyOf(icons).size(),"Two icon names resolve to the same sprite");
        try(var files=Files.list(Path.of("src/main/resources/assets/rematter/textures/gui/sprites/icon"))){
            var sprites=files.filter(file->file.toString().endsWith(".png"))
                    .map(file->"icon/"+file.getFileName().toString().replaceFirst("\\.png$",""))
                    .collect(Collectors.toSet());
            assertEquals(sprites,Set.copyOf(icons));
        }
    }

    @Test void everyTranslatedMachineStatusHasOneExplicitLampMapping() throws Exception {
        var english=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/rematter/lang/en_us.json"))).getAsJsonObject();
        var statuses=english.keySet().stream().filter(key->key.startsWith("status.rematter."))
                .map(key->key.substring("status.rematter.".length())).collect(Collectors.toSet());
        var source=Files.readString(Path.of("src/main/java/dev/latrox/rematter/client/StatusText.java"));
        source=source.substring(source.indexOf("public static UiIcon icon(String status)"),source.indexOf("public static UiIcon icon(String status,"));
        var cases=Pattern.compile("case\\s+([^;]+?)\\s*->").matcher(source);
        var counts=new HashMap<String,Integer>();
        while(cases.find()){
            var names=Pattern.compile("\"([a-z_]+)\"").matcher(cases.group(1));
            while(names.find())counts.merge(names.group(1),1,Integer::sum);
        }
        assertEquals(statuses,counts.keySet(),"A new status needs an explicit severity");
        counts.forEach((status,count)->{assertEquals(1,count,status);assertNotNull(StatusText.icon(status));});
        assertEquals(UiIcon.STATUS_WARNING,StatusText.icon("future_server_status"));
        assertEquals(UiIcon.NETWORK_ONLINE,StatusText.icon("ready",MachineKind.NETWORK_INTERFACE));
        assertEquals(UiIcon.STATUS_IDLE,StatusText.icon("ready",MachineKind.ANALYZER));
    }
}
