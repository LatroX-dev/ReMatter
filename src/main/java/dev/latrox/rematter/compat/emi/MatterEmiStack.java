package dev.latrox.rematter.compat.emi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
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
    @Override public Component getName() {
        return ClientChemistry.current().definition(substance.id())
                .map(definition -> Component.translatableWithFallback(definition.translationKey(), substance.id().path().replace('_', ' ')))
                .orElseGet(() -> Component.literal(substance.id().toString()));
    }
    @Override public List<Component> getTooltipText() {
        return List.of(getName(), Component.literal(substance.phase().name().toLowerCase(Locale.ROOT)),
                Component.literal(amount + " matter units"), Component.literal("Stored substance; not an item"));
    }
    @Override public List<ClientTooltipComponent> getTooltip() {
        return getTooltipText().stream().map(Component::getVisualOrderText).map(ClientTooltipComponent::create).toList();
    }
    @Override public void render(GuiGraphicsExtractor graphics, int x, int y, float delta, int flags) {
        if ((flags & EmiIngredient.RENDER_ICON) == 0) return;
        int color = switch (substance.phase()) {
            case SOLID -> 0xFF83D9CF;
            case LIQUID -> 0xFF6FAFE7;
            case GAS -> 0xFFDBB3EF;
        };
        graphics.fill(x, y, x + 16, y + 16, 0xFF19343C);
        graphics.fill(x, y + 14, x + 16, y + 16, color);
        String symbol = ClientChemistry.current().definition(substance.id())
                .map(definition -> definition.symbol().isBlank() ? definition.id().path().substring(0, 1) : definition.symbol()).orElse("?");
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(symbol, 15);
        graphics.text(font, label, x + (16 - font.width(label)) / 2, y + 3, color, false);
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
