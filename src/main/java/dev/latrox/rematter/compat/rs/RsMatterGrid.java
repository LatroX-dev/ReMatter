package dev.latrox.rematter.compat.rs;

import com.mojang.serialization.MapCodec;
import com.refinedmods.refinedstorage.api.network.node.grid.GridExtractMode;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepository;
import com.refinedmods.refinedstorage.common.api.grid.GridScrollMode;
import com.refinedmods.refinedstorage.common.api.grid.strategy.GridExtractionStrategy;
import com.refinedmods.refinedstorage.common.api.grid.strategy.GridScrollingStrategy;
import com.refinedmods.refinedstorage.common.api.grid.view.AbstractGridResource;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResourceType;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Minimal native Grid row: quantity is visible, but cannot become a fictitious cursor item. */
public final class RsMatterGrid extends AbstractGridResource<RsMatterKey> {
    public static final GridResourceType TYPE = new GridResourceType() {
        @Override public MapCodec<GridResource> getMapCodec() {
            return RsMatterKey.CODEC.xmap(key -> new RsMatterGrid(key), row -> ((RsMatterGrid) row).resource);
        }
        @Override public MutableComponent getTitle() { return Component.translatable("integration.rematter.matter"); }
        @Override public Identifier getSprite() { return Identifier.parse("refinedstorage:widget/side_button/grid/resource_type/item"); }
        @Override public Class<? extends ResourceKey> getResourceType() { return RsMatterKey.class; }
        @Override public GridResource apply(ResourceKey key) { return new RsMatterGrid((RsMatterKey) key); }
    };
    /**
     * RS registers this type during common setup, so dedicated servers load this class. Client presentation
     * is installed by RsClient; this class must never link ReMatter's client package itself.
     */
    interface View {
        Component name(SubstanceKey substance, String sortName);
        String symbol(SubstanceKey substance);
        void render(GuiGraphicsExtractor graphics, int x, int y, SubstanceKey substance);
    }
    private static View view = new View() {
        @Override public Component name(SubstanceKey substance, String sortName) { return Component.literal(sortName); }
        @Override public String symbol(SubstanceKey substance) { return ""; }
        @Override public void render(GuiGraphicsExtractor graphics, int x, int y, SubstanceKey substance) {}
    };
    static void install(View clientView) { view = java.util.Objects.requireNonNull(clientView); }
    public RsMatterGrid(RsMatterKey key) {
        super(key, sortName(key.substance()), ignored -> Set.of());
    }
    private static String sortName(SubstanceKey substance) {
        return substance.id() + " [" + substance.phase().name().toLowerCase(Locale.ROOT) + "]";
    }
    static Component displayName(SubstanceKey substance) { return view.name(substance, sortName(substance)); }
    @Override public Set<String> getSearchableNames() {
        String symbol = view.symbol(resource.substance());
        return symbol.isBlank() || symbol.equals(getSortName()) ? super.getSearchableNames() : Set.of(getSortName(), symbol);
    }
    @Override public int getRegistryId() { return 0; }
    @Override public List<ClientTooltipComponent> getExtractionHints(ItemStack stack, ResourceRepository<GridResource> repository) { return List.of(); }
    @Override public GridResourceType getType() { return TYPE; }
    @Override public ResourceAmount createAutocraftingRequest() { return new ResourceAmount(resource, 1_000); }
    @Override public boolean canExtract(ItemStack stack, ResourceRepository<GridResource> repository) { return false; }
    @Override public void onExtract(GridExtractMode mode, boolean cursor, GridExtractionStrategy strategy) {}
    @Override public void onScroll(GridScrollMode mode, GridScrollingStrategy strategy) {}
    @Override public void render(GuiGraphicsExtractor graphics, int x, int y) {
        view.render(graphics, x, y, resource.substance());
    }
    @Override public String getDisplayedAmount(ResourceRepository<GridResource> repository) { return Long.toString(getAmount(repository)); }
    @Override public String getAmountInTooltip(ResourceRepository<GridResource> repository) { return getAmount(repository) + " u"; }
    @Override public List<Component> getTooltip() {
        return List.of(displayName(resource.substance()), Component.translatable("integration.rematter.request_by_autocrafting"));
    }
    @Override public Optional<TooltipComponent> getTooltipImage() { return Optional.empty(); }
    @Override public boolean is(GridResource other) { return other instanceof RsMatterGrid row && resource.equals(row.resource); }
}
