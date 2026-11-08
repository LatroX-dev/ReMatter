package dev.latrox.rematter.compat.jei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.SubstanceIcon;
import dev.latrox.rematter.compat.ChemistryViewer;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.TooltipFlag;

/** A JEI-only representation of stored matter, never an inventory item. */
public record MatterIngredient(SubstanceKey key, long amount) {
    static final IIngredientType<MatterIngredient> TYPE = () -> MatterIngredient.class;
    private static final Codec<SubstanceId> SUBSTANCE_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(SubstanceId.parse(value)); }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "Invalid substance identifier"); }
    }, SubstanceId::toString);
    private static final Codec<Phase> PHASE_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(Phase.valueOf(value)); }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "Invalid substance phase"); }
    }, Phase::name);
    static final Codec<MatterIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SUBSTANCE_CODEC.fieldOf("substance").forGetter(value -> value.key.id()),
            PHASE_CODEC.fieldOf("phase").forGetter(value -> value.key.phase()),
            Codec.LONG.validate(value -> value > 0 ? DataResult.success(value) : DataResult.error(() -> "Matter amount must be positive"))
                    .fieldOf("amount").forGetter(MatterIngredient::amount)
    ).apply(instance, (id, phase, amount) -> new MatterIngredient(new SubstanceKey(id, phase), amount)));

    public MatterIngredient {
        if (key == null || amount <= 0) throw new IllegalArgumentException("Matter ingredient requires a key and positive amount");
    }

    static final class Helper implements IIngredientHelper<MatterIngredient> {
        @Override public IIngredientType<MatterIngredient> getIngredientType() { return TYPE; }
        @Override public String getDisplayName(MatterIngredient ingredient) { return ChemistryViewer.matterName(ingredient.key).getString(); }
        @Override public Object getUid(MatterIngredient ingredient, UidContext context) { return ingredient.key; }
        @Override public Identifier getIdentifier(MatterIngredient ingredient) { return Identifier.parse(ingredient.key.id().toString()); }
        @Override public long getAmount(MatterIngredient ingredient) { return ingredient.amount; }
        @Override public MatterIngredient copyWithAmount(MatterIngredient ingredient, long amount) { return new MatterIngredient(ingredient.key, amount); }
        @Override public MatterIngredient copyIngredient(MatterIngredient ingredient) { return ingredient; }
        @Override public MatterIngredient normalizeIngredient(MatterIngredient ingredient) { return new MatterIngredient(ingredient.key, 1); }
        @Override public boolean isIngredientOnServer(MatterIngredient ingredient) { return ClientChemistry.current().isKnown(ingredient.key); }
        @Override public String getErrorInfo(MatterIngredient ingredient) { return String.valueOf(ingredient); }
    }

    static final class Renderer implements IIngredientRenderer<MatterIngredient> {
        @Override
        public void render(GuiGraphicsExtractor graphics, MatterIngredient ingredient) {
            SubstanceIcon.draw(graphics, 0, 0, ingredient.key);
        }

        @SuppressWarnings("removal")
        @Override public List<Component> getTooltip(MatterIngredient ingredient, TooltipFlag flag) {
            return ChemistryViewer.matterTooltip(ingredient.key, ingredient.amount);
        }
    }
}
