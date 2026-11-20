package dev.latrox.rematter.compat.emi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.SubstanceIcon;
import dev.latrox.rematter.compat.ChemistryViewer;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Native EMI ingredient for stored matter. It cannot be cheated into an item inventory. */
public final class MatterEmiStack extends EmiStack {
    private final SubstanceKey substance;

    public MatterEmiStack(SubstanceKey substance, long amount) {
        if (substance == null || amount <= 0) throw new IllegalArgumentException("Invalid matter ingredient");
        this.substance = substance;
        this.amount = amount;
    }

    @Override public MatterEmiStack copy() {
        var copy = new MatterEmiStack(substance, amount);
        copy.chance = chance;
        copy.comparison = comparison;
        return copy;
    }
    @Override public boolean isEmpty() { return amount <= 0; }
    @Override public DataComponentPatch getComponentChanges() { return DataComponentPatch.EMPTY; }
    @Override public Object getKey() { return substance; }
    @Override public Identifier getId() { return Identifier.parse(substance.id().toString()); }
    @Override public Component getName() { return ChemistryViewer.matterName(substance); }
    @Override public List<Component> getTooltipText() { return ChemistryViewer.matterTooltip(substance, amount); }
    @Override public List<ClientTooltipComponent> getTooltip() {
        return getTooltipText().stream().map(Component::getVisualOrderText).map(ClientTooltipComponent::create).toList();
    }
    @Override public void render(GuiGraphicsExtractor graphics, int x, int y, float delta, int flags) {
        if ((flags & EmiIngredient.RENDER_ICON) == 0) return;
        SubstanceIcon.draw(graphics, x, y, substance);
    }

    static final class Serializer implements EmiIngredientSerializer<MatterEmiStack> {
        @Override public String getType() { return "rematter_matter"; }
        @Override public JsonElement serialize(MatterEmiStack value) {
            JsonObject json = new JsonObject();
            json.addProperty("type", getType());
            json.addProperty("substance", value.substance.id().toString());
            json.addProperty("phase", value.substance.phase().name());
            json.addProperty("amount", value.amount);
            return json;
        }
        @Override public EmiIngredient deserialize(JsonElement json) {
            try {
                JsonObject value = json.getAsJsonObject();
                SubstanceKey key = new SubstanceKey(SubstanceId.parse(value.get("substance").getAsString()),
                        Phase.valueOf(value.get("phase").getAsString()));
                if (!ClientChemistry.current().isKnown(key)) return EmiStack.EMPTY;
                return new MatterEmiStack(key, value.get("amount").getAsLong());
            } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                return EmiStack.EMPTY;
            }
        }
    }
}
