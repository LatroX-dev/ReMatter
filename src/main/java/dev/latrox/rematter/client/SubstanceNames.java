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

    /** Formats a Focus Catalyst or Singularity Seed requirement, explicitly noting Primal Matrix Core backward compatibility. */
    public static Component catalystRequirement(String catalystId) {
        if ("rematter:particle_resonator".equals(catalystId) || "rematter:primal_crystal".equals(catalystId)) {
            return Component.translatable("gui.rematter.viewer.catalyst_or_higher",
                    item(catalystId), item("rematter:primal_matrix_core"));
        }
        return item(catalystId);
    }

    /** Formats a Deep-Core Extractor site requirement from its {@code probe_below} selector. */
    public static Component probeRequirement(String probeBelow) {
        return switch (probeBelow == null ? "" : probeBelow) {
            case "deepslate", "water", "lava", "void" -> Component.translatable("status.rematter.requires_probe_" + probeBelow);
            default -> Component.translatable("status.rematter.requires_probe_target");
        };
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
