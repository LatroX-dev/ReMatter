package dev.latrox.rematter.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrimalConverterViewTest {
    @Test void lockedTargetsKeepTheirExactServerPriceButDoNotBecomeKnown(){
        var input=JsonParser.parseString("""
            {"targets":[
              {"id":"rematter:iron","known":true,"ratio":1,"primal":1000,"energy":64000,"ticks":400},
              {"id":"rematter:gold","known":false,"ratio":2,"primal":2000,"energy":96000,"ticks":500},
              {"id":"rematter:gold","known":true,"ratio":1,"primal":1,"energy":1,"ticks":1},
              {"id":"not an id","known":true,"ratio":1,"primal":1,"energy":1,"ticks":1},
              {"id":"rematter:tin","known":true,"ratio":0,"primal":1000,"energy":64000,"ticks":400}
            ]}
            """).getAsJsonObject();
        var targets=PrimalConverterView.targets(input);
        assertEquals(2,targets.size());assertTrue(targets.getFirst().known());
        var gold=targets.get(1);assertEquals("rematter:gold",gold.id());assertFalse(gold.known());
        assertEquals(2,gold.ratio());assertEquals(2000,gold.primal());assertEquals(96000,gold.energy());assertEquals(500,gold.ticks());
        assertFalse(input.getAsJsonArray("targets").get(1).getAsJsonObject().get("known").getAsBoolean());
    }
    @Test void destinationKeepsNegativeCoordinatesAndExactLargeCapacity(){
        var source=JsonParser.parseString("""
            {"conversionDestination":{"status":"ready","x":-112,"y":224,"z":-48,"bay":6,"item":"rematter:matter_cell_1m","used":123456,"capacity":1024000}}
            """).getAsJsonObject();
        var destination=PrimalConverterView.destination(source);
        assertTrue(destination.ready());assertEquals(-112,destination.x());assertEquals(-48,destination.z());assertEquals(6,destination.bay());
        assertEquals(900544,destination.free());assertEquals(123456,destination.used());
    }
    @Test void absentAndMalformedDestinationsNeverAdvertiseReady(){
        assertEquals("choose_target",PrimalConverterView.destination(new com.google.gson.JsonObject()).status());
        for(String row:new String[]{"{\"status\":\"ready\",\"bay\":0,\"capacity\":64000}","{\"status\":\"ready\",\"bay\":1,\"used\":64001,\"capacity\":64000}"}){
            var source=new com.google.gson.JsonObject();source.add("conversionDestination",JsonParser.parseString(row));
            assertFalse(PrimalConverterView.destination(source).ready());
        }
    }
}
