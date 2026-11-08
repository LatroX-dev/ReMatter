package dev.latrox.rematter.client;

import com.google.gson.JsonParser;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class StockFavoritesTest {
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final StockKey WATER = new StockKey("rematter:water", "liquid");
    private static final StockKey REMOVED = new StockKey("example:removed_essence", "solid");

    @TempDir Path folder;

    @Test void favoritesArePerPlayerAndSurviveAReload() {
        Path file = folder.resolve("rematter/stock_favorites.json");
        var favorites = StockFavorites.load(file);
        assertTrue(favorites.toggle(ALEX, WATER));
        assertTrue(favorites.toggle(ALEX, REMOVED));
        assertTrue(favorites.contains(ALEX, WATER));
        assertFalse(favorites.contains(SAM, WATER));
        assertEquals(2, favorites.revision());

        var reloaded = StockFavorites.load(file);
        assertTrue(reloaded.contains(ALEX, WATER));
        assertTrue(reloaded.contains(ALEX, REMOVED), "a favorite of an undefined substance is kept");
        assertTrue(reloaded.toggle(ALEX, WATER));
        assertFalse(StockFavorites.load(file).contains(ALEX, WATER));
    }

    @Test void theCurrentSchemaRoundTripsExactly() {
        var data = Map.of(ALEX, Set.of(WATER, REMOVED), SAM, Set.of(WATER));
        var json = StockFavorites.encode(data);
        assertEquals(StockFavorites.SCHEMA_VERSION, json.get("schema_version").getAsInt());
        assertEquals(data, StockFavorites.decode(json));
    }

    @Test void otherSchemasAndMalformedEntriesAreRejectedAsAWhole() {
        String player = ALEX.toString();
        for (String invalid : new String[]{
                "{\"schema_version\":2,\"players\":{}}",
                "{\"players\":{}}",
                "{\"schema_version\":1,\"players\":{},\"extra\":true}",
                "{\"schema_version\":1,\"players\":{\"" + player + "\":[{\"substance\":\"rematter:water\",\"phase\":\"plasma\"}]}}",
                "{\"schema_version\":1,\"players\":{\"" + player + "\":[{\"substance\":\"water\",\"phase\":\"liquid\"}]}}",
                "{\"schema_version\":1,\"players\":{\"" + player + "\":[]}}",
                "{\"schema_version\":1,\"players\":{\"not-a-uuid\":[{\"substance\":\"rematter:water\",\"phase\":\"liquid\"}]}}",
                "{\"schema_version\":1,\"players\":{\"" + player + "\":[{\"substance\":\"rematter:water\",\"phase\":\"liquid\"},"
                        + "{\"substance\":\"rematter:water\",\"phase\":\"liquid\"}]}}"}) {
            assertThrows(RuntimeException.class, () -> StockFavorites.decode(JsonParser.parseString(invalid)), invalid);
        }
    }

    @Test void anUnreadableFileIsNeverOverwritten() throws IOException {
        Path file = folder.resolve("stock_favorites.json");
        String future = "{\"schema_version\":2,\"players\":{}}";
        Files.writeString(file, future);
        var favorites = StockFavorites.load(file);
        assertFalse(favorites.contains(ALEX, WATER));
        assertTrue(favorites.toggle(ALEX, WATER));
        assertTrue(favorites.contains(ALEX, WATER), "the session still keeps the favorite");
        assertEquals(future, Files.readString(file));
    }
}
