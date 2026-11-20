package dev.latrox.rematter.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Research validates against the same candidate chemistry before either catalogue is published. */
public final class ResearchData {
    private static final String DIRECTORY = "rematter/material_research";
    private static final Map<ChemistryRegistry, ResearchCatalogue> STAGED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static volatile ResearchCatalogue current = ResearchCatalogue.EMPTY;
    private ResearchData() {}
    public static ResearchCatalogue current() { return current; }
    /** Called at the end of ChemistryData.prepare, after all ordinary chemistry validation succeeded. */
    public static void stage(ResourceManager manager, ChemistryRegistry chemistry) {
        var values = new java.util.ArrayList<MaterialResearch>();
        documents(manager).forEach((id, json) -> ResearchCatalogue.parse(id, json, chemistry).ifPresent(values::add));
        STAGED.put(chemistry, new ResearchCatalogue(values));
    }
    /** Called immediately before ChemistryData publishes the same validated registry, on the reload apply thread. */
    public static void publish(ChemistryRegistry chemistry) {
        var prepared = STAGED.remove(chemistry);
        if (prepared == null) throw new IllegalStateException("Research was not staged with this chemistry reload");
        current = prepared;
        ReMatter.LOGGER.info("Loaded {} active material research families", prepared.families().size());
    }
    private static Map<String, JsonObject> documents(ResourceManager manager) {
        var documents = new TreeMap<String, JsonObject>();
        var resources = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
        if (resources.size() > 8192) throw new IllegalArgumentException("Too many research families");
        for (var entry : resources.entrySet()) {
            var source = entry.getKey(); String path = source.getPath().substring(DIRECTORY.length() + 1, source.getPath().length() - 5);
            String id = Identifier.fromNamespaceAndPath(source.getNamespace(), path).toString();
            try (var reader = entry.getValue().openAsReader()) {
                var text = new StringBuilder(); char[] buffer = new char[4096]; int count;
                while ((count = reader.read(buffer)) >= 0) { if (text.length() + count > 262_144) throw new IllegalArgumentException("Research document too large: " + source); text.append(buffer, 0, count); }
                documents.put(id, JsonParser.parseString(text.toString()).getAsJsonObject());
            } catch (IOException problem) { throw new IllegalArgumentException("Cannot read research family " + source, problem); }
        }
        return Map.copyOf(documents);
    }
}
