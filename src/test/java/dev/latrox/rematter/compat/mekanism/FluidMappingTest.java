package dev.latrox.rematter.compat.mekanism;

import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidMappingTest {
    @Test void shippedRotaryFluidsAndChemicalPortsConserveTheSameExternalQuantity() throws Exception {
        var resource = getClass().getClassLoader().getResource("data/rematter/rematter/fluid_profile/fluids/mekanism");
        assertNotNull(resource);
        Set<String> checked = new HashSet<>();
        try (var paths = Files.list(Path.of(resource.toURI()))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String fluid = json.get("fluid").getAsString();
                int index = -1;
                for (int i = 0; i < ChemicalMappings.ALL.size(); i++) {
                    if (ChemicalMappings.ALL.get(i).chemicalId().equals(fluid)) index = i;
                }
                assertTrue(index >= 0, "No reviewed 1:1 rotary counterpart for " + fluid);
                var mapping = ChemicalMappings.ALL.get(index);
                var matter = json.getAsJsonArray("matter");
                assertEquals(1, matter.size(), fluid);
                var entry = matter.get(0).getAsJsonObject();
                var key = SubstanceKey.of(entry.get("substance").getAsString(), Phase.valueOf(entry.get("phase").getAsString().toUpperCase(java.util.Locale.ROOT)));
                assertEquals(mapping.matter(), key, fluid);
                long units = entry.get("amount").getAsLong();
                int millibuckets = json.get("amount").getAsInt();
                assertEquals(Math.multiplyExact((long) millibuckets, mapping.unitsPerChemical()), units, fluid);

                var inventory = new MatterInventory(units + 11);
                inventory.insert(BuiltinChemistry.WATER, 11);
                assertTrue(inventory.apply(Map.of(), Map.of(key, units)));
                var ports = new ChemicalTransfers(() -> inventory, () -> true, ignored -> true, () -> {});
                try (var transaction = Transaction.openRoot()) {
                    assertEquals(millibuckets, ports.extract(index, fluid, Integer.MAX_VALUE, transaction), fluid);
                    transaction.commit();
                }
                assertEquals(Map.of(BuiltinChemistry.WATER, 11L), inventory.contents(), fluid);
                checked.add(fluid);
            }
        }
        assertEquals(Set.of("mekanism:hydrogen", "mekanism:oxygen", "mekanism:chlorine",
                "mekanism:sulfur_dioxide", "mekanism:sulfur_trioxide", "mekanism:sulfuric_acid",
                "mekanism:hydrogen_chloride", "mekanism:hydrofluoric_acid", "mekanism:ethene", "mekanism:lithium"), checked);
    }
}
