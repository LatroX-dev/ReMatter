package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.ChemistryText;
import dev.latrox.rematter.content.MachineChoices;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** Client-side names for synchronized IDs, so the player's language and resource packs apply instead of the server's. */
public final class SubstanceNames {
    private SubstanceNames() {}

    /** A stock row whose definition is missing stays visible, marked as unknown with its stable ID. */
    public static Component substance(String id, String phase, boolean known) {
        if (!known) return Component.translatable("gui.rematter.unknown_substance", id, phase(phase));
        try {
            return ClientChemistry.current().definition(SubstanceId.parse(id)).map(SubstanceNames::name).orElseGet(() -> Component.literal(id));
        } catch (IllegalArgumentException invalid) { return Component.literal(id); }
    }

    /** A definition's name in the player's language; a data-pack substance without a translation is named from its ID. */
    public static Component name(SubstanceDefinition definition) {
        return Component.translatableWithFallback(definition.translationKey(), ChemistryText.readable(definition.id().path()));
    }

    /** Recipe viewers and terminals use the client's catalogue to decide whether a key is still defined. */
    public static Component substance(SubstanceKey key) {
        return substance(key.id().toString(), phaseId(key.phase()), ClientChemistry.current().isKnown(key));
    }

    public static boolean known(SubstanceKey key) { return ClientChemistry.current().isKnown(key); }

    /** The name with its formula; an undefined key shows its ID and the unknown marker instead. */
    public static Component labelled(SubstanceKey key) {
        var name = substance(key);
        return known(key) ? SubstanceLabels.withSymbol(key.id(), name) : name;
    }

    public static Component phase(String phase) {
        return switch (phase) {
            case "solid", "liquid", "gas" -> Component.translatable("phase.rematter." + phase);
            default -> Component.literal(phase);
        };
    }

    public static Component phase(Phase phase) { return phase(phaseId(phase)); }

    static String phaseId(Phase phase) { return phase.name().toLowerCase(Locale.ROOT); }

    public static Component item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key)
                ? new ItemStack(BuiltInRegistries.ITEM.getValue(key)).getHoverName() : Component.literal(id);
    }

    public static Component fluid(String id) {
        Identifier key = Identifier.tryParse(id);
        return key != null && BuiltInRegistries.FLUID.containsKey(key)
                ? FluidResource.of(BuiltInRegistries.FLUID.getValue(key)).getHoverName() : Component.literal(id);
    }

    /** A machine's recipe choice as the client's own catalogue names it. */
    public static Component choice(MachineKind kind, String id) {
        return MachineChoices.find(kind, ClientChemistry.current(), id)
                .<Component>map(choice -> Component.literal(choice.name())).orElseGet(() -> Component.literal(id));
    }
}
