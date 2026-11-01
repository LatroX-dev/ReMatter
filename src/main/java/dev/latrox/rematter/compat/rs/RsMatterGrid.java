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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
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
        @Override public MutableComponent getTitle() { return Component.literal("Matter"); }
        @Override public Identifier getSprite() { return Identifier.parse("refinedstorage:widget/side_button/grid/resource_type/item"); }
        @Override public Class<? extends ResourceKey> getResourceType() { return RsMatterKey.class; }
        @Override public GridResource apply(ResourceKey key) { return new RsMatterGrid((RsMatterKey) key); }
    };
    public RsMatterGrid(RsMatterKey key) {
        super(key, key.substance().id() + " [" + key.substance().phase().name().toLowerCase(Locale.ROOT) + "]", ignored -> Set.of());
    }
    @Override public int getRegistryId() { return 0; }
    @Override public List<ClientTooltipComponent> getExtractionHints(ItemStack stack, ResourceRepository<GridResource> repository) { return List.of(); }
    @Override public GridResourceType getType() { return TYPE; }
    @Override public ResourceAmount createAutocraftingRequest() { return new ResourceAmount(resource, 1_000); }
    @Override public boolean canExtract(ItemStack stack, ResourceRepository<GridResource> repository) { return false; }
    @Override public void onExtract(GridExtractMode mode, boolean cursor, GridExtractionStrategy strategy) {}
    @Override public void onScroll(GridScrollMode mode, GridScrollingStrategy strategy) {}
    @Override public void render(GuiGraphicsExtractor graphics, int x, int y) {
        int color = switch (resource.substance().phase()) {
            case SOLID -> 0xffcc9169; case LIQUID -> 0xff7ecce8; case GAS -> 0xffbea9e2;
        };
        graphics.fill(x + 1, y + 1, x + 15, y + 15, color);
        graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xff17252c);
        var definition=dev.latrox.rematter.network.ClientChemistry.current().definition(resource.substance().id()).orElse(null);
        String symbol=definition==null?"?":definition.symbol();
        symbol=Minecraft.getInstance().font.plainSubstrByWidth(symbol,13);
        graphics.text(Minecraft.getInstance().font, symbol, x + 2, y + 4, color, false);
    }
    @Override public String getDisplayedAmount(ResourceRepository<GridResource> repository) { return Long.toString(getAmount(repository)); }
    @Override public String getAmountInTooltip(ResourceRepository<GridResource> repository) { return getAmount(repository) + " units"; }
    @Override public List<Component> getTooltip() {
        return List.of(Component.literal(getSortName()), Component.literal("Stored substance. Use crafting to request production."));
    }
    @Override public Optional<TooltipComponent> getTooltipImage() { return Optional.empty(); }
    @Override public boolean is(GridResource other) { return other instanceof RsMatterGrid row && resource.equals(row.resource); }
}
