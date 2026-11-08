package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.content.MachineChoices;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.network.ClientChemistry;
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
        if (!known) return Component.translatable("substance.rematter.unknown", id, phase);
        try {
            return ClientChemistry.current().definition(SubstanceId.parse(id))
                    .<Component>map(definition -> Component.translatable(definition.translationKey()))
                    .orElseGet(() -> Component.literal(id));
        } catch (IllegalArgumentException invalid) { return Component.literal(id); }
    }

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
