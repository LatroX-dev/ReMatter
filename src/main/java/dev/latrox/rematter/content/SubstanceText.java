package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Substance names for item tooltips and other common code, read from the catalogue of the side that shows them. */
public final class SubstanceText {
    private SubstanceText() {}

    /** The client's synchronized catalogue in a client level, otherwise the server's. */
    public static ChemistryRegistry catalogue(@Nullable Level level) {
        return level != null && level.isClientSide() ? ClientChemistry.current() : ReMatter.chemistry();
    }

    public static Component phase(Phase phase) {
        return Component.translatable("item.rematter.matter_cell.phase." + phase.name().toLowerCase(Locale.ROOT));
    }

    /** The name with its formula. A key the catalogue does not define keeps its stable ID and is marked unknown. */
    public static Component name(ChemistryRegistry registry, SubstanceKey key) {
        if (!registry.isKnown(key)) return Component.translatable("gui.rematter.unknown_substance", key.id().toString(), phase(key.phase()));
        var definition = registry.definition(key.id()).orElseThrow();
        Component name = Component.translatableWithFallback(definition.translationKey(), key.id().toString());
        String symbol = definition.symbol().strip();
        return symbol.isEmpty() ? name : Component.translatable("item.rematter.matter_cell.symbol", name, symbol);
    }

    /** One tooltip row: name, phase and exact quantity. */
    public static Component row(ChemistryRegistry registry, SubstanceKey key, long quantity) {
        return Component.translatable("item.rematter.matter_cell.row", name(registry, key), phase(key.phase()), quantity);
    }
}
