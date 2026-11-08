package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.client.ScreenChrome.Icon;
import dev.latrox.rematter.content.MachineChoices;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.SelectProcessPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Accessible product buttons focus a preview; confirmation only changes the selected recipe. */
public final class ProcessSelectionScreen extends Screen {
    private static final int INK = UiPalette.PANEL, EDGE = UiPalette.BORDER;
    private static final int PAPER = UiPalette.TEXT, MUTED = UiPalette.MUTED, CYAN = UiPalette.ACCENT;
    private static final int RESULT_PITCH = 33, RESULT_HEIGHT = 29, MATERIAL_PITCH = 25;
    private static final int PAGE_BUTTON_WIDTH = 20, MATERIAL_PAGER_WIDTH = 82, CLOSE_WIDTH = 50;
    private final MachineMenu menu;
    private ChemistryRegistry registry;
    private List<ProcessPresentation.Product> products = List.of(), filtered = List.of();
    private final List<ProductButton> resultButtons = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private EditBox search;
    private Button previous, next, detailPrevious, detailNext, confirm, backToResults, closeDetails, closeResults;
    private int left, top, panelWidth, panelHeight;
    private int resultsX, resultsWidth, resultsTop, resultsPagerWidth, footerY;
    private int detailX, detailWidth, detailPagerY, ingredientTop, detailRows;
    private int page, detailPage;
    private boolean narrow, showingDetails, showSummary;
    private String focusedId = "";

    public ProcessSelectionScreen(MachineMenu menu) {
        super(Component.literal("Choose a recipe"));
        this.menu = menu;
    }

    @Override protected void init() {
        panelWidth = Math.min(480, width - 12);
        panelHeight = Math.min(324, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        narrow = panelWidth < 296;
        showSummary = panelHeight >= 172;
        resultsX = left + 10;
        resultsWidth = narrow ? panelWidth - 20 : Math.clamp((panelWidth - 28) * 2 / 5, 106, 176);
        detailX = narrow ? resultsX : resultsX + resultsWidth + 8;
        detailWidth = left + panelWidth - 10 - detailX;
        footerY = top + panelHeight - 26;
        resultsTop = top + (panelHeight >= 126 ? 64 : 51);
        resultsPagerWidth = narrow ? resultsWidth - CLOSE_WIDTH - 6 : resultsWidth;
        ingredientTop = top + (showSummary ? 107 : 77);
        detailPagerY = ingredientTop - 21;
        detailRows = Math.max(0, (footerY - ingredientTop - 3) / MATERIAL_PITCH);

        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, resultsX + 6, top + 29, resultsWidth - 12, 18,
                Component.literal("Search products or processes")));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(PAPER);
        search.setHint(Component.literal("Search recipes").withStyle(style -> style.withColor(MUTED & 0xffffff)));
        search.setValue(query);
        search.setResponder(value -> {
            page = 0;
            focusedId = "";
            filter();
        });

        resultButtons.clear();
        int visibleResults = Math.max(1, (footerY - resultsTop - 4) / RESULT_PITCH);
        for (int i = 0; i < visibleResults; i++) {
            int index = i;
            resultButtons.add(addRenderableWidget(new ProductButton(Button.builder(Component.empty(), button -> focusResult(index))
                    .bounds(resultsX, resultsTop + i * RESULT_PITCH, resultsWidth, RESULT_HEIGHT))));
        }
        previous = button("<", resultsX, footerY, PAGE_BUTTON_WIDTH, "Previous recipes", b -> changePage(-1));
        next = button(">", resultsX + resultsPagerWidth - PAGE_BUTTON_WIDTH, footerY, PAGE_BUTTON_WIDTH,
                "Next recipes", b -> changePage(1));
        detailPrevious = button("<", detailX + detailWidth - MATERIAL_PAGER_WIDTH - 6, detailPagerY, PAGE_BUTTON_WIDTH,
                "Previous recipe ingredients", b -> detailPage--);
        detailNext = button(">", detailX + detailWidth - PAGE_BUTTON_WIDTH - 6, detailPagerY, PAGE_BUTTON_WIDTH,
                "Next recipe ingredients", b -> detailPage++);
        confirm = button("Use recipe", detailX, footerY, detailWidth - CLOSE_WIDTH - 6,
                "Select this recipe. Start production from the machine controls.", b -> selectFocused());
        closeDetails = button("Close", detailX + detailWidth - CLOSE_WIDTH, footerY, CLOSE_WIDTH,
                "Close without changing the recipe", b -> onClose());
        backToResults = button("Results", left + panelWidth - 66, top + 4, 56,
                "Return to recipe results", b -> showResults());
        closeResults = button("Close", resultsX + resultsWidth - CLOSE_WIDTH, footerY, CLOSE_WIDTH,
                "Close without changing the recipe", b -> onClose());
        loadChoices();
        setInitialFocus(showingDetails && narrow ? backToResults : search);
    }

    private Button button(String text, int x, int y, int width, String hint, Button.OnPress action) {
        String accessibleName = text.equals("<") || text.equals(">") ? hint : text;
        return addRenderableWidget(Button.builder(Component.literal(accessibleName), action).bounds(x, y, width, 18)
                .tooltip(Tooltip.create(Component.literal(hint))).build(builder -> new PanelButton(builder, text)));
    }

    private void loadChoices() {
        registry = ClientChemistry.current();
        MachineKind kind = MachineKind.values()[menu.snapshot().kind()];
        products = MachineChoices.forKind(kind, registry).stream()
                .map(choice -> ProcessPresentation.resolve(registry, kind, choice.id(), choice.name()))
                .flatMap(java.util.Optional::stream).toList();
        icons.clear();
        if (focusedId.isEmpty()) focusedId = menu.snapshot().selectedId();
        filter();
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id().equals(focusedId)) { page = i / resultButtons.size(); break; }
        }
        refreshResults();
    }

    private void filter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = products.stream().filter(product -> product.name().toLowerCase(Locale.ROOT).contains(query)
                || product.id().toLowerCase(Locale.ROOT).contains(query)
                || Stream.concat(product.inputs().keySet().stream(), product.outputs().keySet().stream())
                        .anyMatch(key -> SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(key.id()), query))).toList();
        if (filtered.stream().noneMatch(product -> product.id().equals(focusedId))) {
            focusedId = filtered.isEmpty() ? "" : filtered.getFirst().id();
            detailPage = 0;
        }
        refreshResults();
    }

    private void refreshResults() {
        page = Math.clamp(page, 0, pages() - 1);
        boolean results = resultsVisible();
        boolean details = detailsVisible();
        search.visible = results;
        for (int i = 0; i < resultButtons.size(); i++) {
            ProductButton tile = resultButtons.get(i);
            int index = page * resultButtons.size() + i;
            tile.product = index < filtered.size() ? filtered.get(index) : null;
            tile.visible = tile.active = results && tile.product != null;
            if (tile.product != null) {
                String label = productLabel(tile.product);
                tile.setMessage(Component.literal(label));
                tile.setTooltip(Tooltip.create(Component.literal(label + "\n" + tile.product.id() + "\nView recipe details")));
            }
        }
        previous.visible = next.visible = results && pages() > 1;
        previous.active = page > 0;
        next.active = page + 1 < pages();
        confirm.visible = closeDetails.visible = details;
        confirm.active = focused() != null;
        backToResults.visible = narrow && details;
        closeResults.visible = narrow && results;
        if (!details) detailPrevious.visible = detailNext.visible = false;
    }

    private boolean resultsVisible() { return !narrow || !showingDetails; }
    private boolean detailsVisible() { return !narrow || showingDetails; }

    private void showResults() {
        showingDetails = false;
        refreshResults();
        setFocused(search);
    }

    private int pages() { return Math.max(1, (filtered.size() + resultButtons.size() - 1) / resultButtons.size()); }
    private void changePage(int step) {
        page = Math.clamp(page + step, 0, pages() - 1);
        if (!filtered.isEmpty()) focusedId = filtered.get(page * resultButtons.size()).id();
        detailPage = 0;
        refreshResults();
    }
    private ProcessPresentation.Product focused() {
        return filtered.stream().filter(product -> product.id().equals(focusedId)).findFirst().orElse(null);
    }
    private void focusResult(int row) {
        int index = page * resultButtons.size() + row;
        if (index >= filtered.size()) return;
        focusedId = filtered.get(index).id();
        detailPage = 0;
        if (narrow) {
            showingDetails = true;
            refreshResults();
            setFocused(backToResults);
        }
    }
    private void selectFocused() {
        if (focused() != null && minecraft.player != null && minecraft.player.containerMenu == menu) {
            ClientPacketDistributor.sendToServer(new SelectProcessPayload(menu.containerId, focusedId));
            onClose();
        }
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) { onClose(); return; }
        if (registry != ClientChemistry.current()) loadChoices();
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ScreenChrome.panel(graphics, left, top, panelWidth, panelHeight);
        graphics.fill(left + 4, top + 24, left + panelWidth - 4, top + panelHeight - 4, INK);
        graphics.fill(left + 10, top + 22, left + 40, top + 24, UiPalette.machineAccent(menu.kind()));
        ScreenChrome.icon(graphics, Icon.RECIPE, left + 10, top + 7, CYAN);
        String heading = narrow && showingDetails ? "Recipe details" : title.getString();
        int headingWidth = panelWidth - (narrow && showingDetails ? 98 : 37);
        graphics.text(font, ScreenChrome.fit(font, heading, headingWidth), left + 27, top + 9, PAPER, false);

        if (resultsVisible()) drawResults(graphics);
        if (detailsVisible()) drawDetails(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawResults(GuiGraphicsExtractor graphics) {
        ScreenChrome.inset(graphics, resultsX, top + 28, resultsWidth, 20);
        if (resultsTop - top >= 64) {
            String count = filtered.size() + (filtered.size() == 1 ? " recipe" : " recipes");
            graphics.text(font, ScreenChrome.fit(font, count, resultsWidth), resultsX + 2, top + 53, MUTED, false);
        }
        if (filtered.isEmpty()) {
            graphics.text(font, ScreenChrome.fit(font, "No recipes match", resultsWidth - 4),
                    resultsX + 2, resultsTop + 5, MUTED, false);
        }
        if (pages() > 1) drawPage(graphics, page + 1, pages(), resultsX, footerY, resultsPagerWidth);
    }

    private void drawPage(GuiGraphicsExtractor graphics, int current, int pages, int x, int y, int width) {
        String label = ScreenChrome.fit(font, current + " / " + pages, width - 2 * PAGE_BUTTON_WIDTH - 4);
        graphics.text(font, label, x + (width - font.width(label)) / 2, y + 5, MUTED, false);
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int detailTop = top + 28;
        ScreenChrome.inset(graphics, detailX, detailTop, detailWidth, footerY - detailTop - 5);
        ProcessPresentation.Product product = focused();
        detailPrevious.visible = detailNext.visible = false;
        if (product == null) {
            graphics.text(font, ScreenChrome.fit(font, "Select a recipe to see its materials", detailWidth - 16),
                    detailX + 8, detailTop + 8, MUTED, false);
            return;
        }

        boolean selected = product.id().equals(menu.snapshot().selectedId());
        var preview = menu.snapshot().preview();
        boolean effective = selected && preview.id().equals(product.id());
        long energy = effective ? preview.energy() : product.energy();
        int ticks = effective ? preview.ticks() : product.ticks();
        drawProductIcon(graphics, product, detailX + 8, detailTop + 7);
        ScreenChrome.twoLines(graphics, font, productLabel(product), detailX + 30, detailTop + 6, detailWidth - 38, PAPER);
        if (inside(mouseX, mouseY, detailX + 7, detailTop + 5, detailWidth - 14, 23)) {
            graphics.setTooltipForNextFrame(Component.literal(productLabel(product) + "\n" + product.id()
                    + "\n" + batchValue(product) + " / batch\n" + number(energy) + " FE · " + ticks + " ticks"
                    + (effective ? "\nIncludes the current machine upgrades" : "\nBase recipe values")
                    + (selected ? "\nCurrently selected" : "\nPreview only")), mouseX, mouseY);
        }
        if (showSummary) {
            ScreenChrome.icon(graphics, Icon.OUTPUT, detailX + 8, detailTop + 32, CYAN);
            graphics.text(font, ScreenChrome.fit(font, batchValue(product) + " / batch", detailWidth - 32),
                    detailX + 25, detailTop + 34, CYAN, false);
            ScreenChrome.icon(graphics, Icon.POWER, detailX + 8, detailTop + 44, CYAN);
            graphics.text(font, ScreenChrome.fit(font, compact(energy) + " FE  ·  " + seconds(ticks) + " s", detailWidth - 32),
                    detailX + 25, detailTop + 46, PAPER, false);
        }

        // An incomplete preview must not hide further recipe ingredients.
        List<MaterialRow> rows = materialRows(product, effective && preview.complete());
        int pages = detailRows == 0 ? 1 : Math.max(1, (rows.size() + detailRows - 1) / detailRows);
        detailPage = Math.clamp(detailPage, 0, pages - 1);
        detailPrevious.visible = detailNext.visible = pages > 1;
        detailPrevious.active = detailPage > 0;
        detailNext.active = detailPage + 1 < pages;
        int headingWidth = detailWidth - 16 - (pages > 1 ? MATERIAL_PAGER_WIDTH + 4 : 0);
        int headingY = detailPagerY + 5;
        graphics.fill(detailX + 7, detailPagerY - 2, detailX + detailWidth - 7, detailPagerY - 1, EDGE);
        String materialHeading = "Materials  ·  Needed / Available";
        if (font.width(materialHeading) > headingWidth) materialHeading = "Materials";
        graphics.text(font, ScreenChrome.fit(font, materialHeading, headingWidth), detailX + 8, headingY, MUTED, false);
        if (inside(mouseX, mouseY, detailX + 7, headingY - 1, headingWidth, 11)) {
            graphics.setTooltipForNextFrame(Component.literal("Materials needed / available on the matter network\n"
                    + "+ marks a material produced by this recipe"), mouseX, mouseY);
        }
        int firstRow = detailPage * detailRows;
        for (int row = 0; row < detailRows && firstRow + row < rows.size(); row++) {
            drawMaterial(graphics, rows.get(firstRow + row), ingredientTop + row * MATERIAL_PITCH, mouseX, mouseY);
        }
        if (pages > 1) {
            drawPage(graphics, detailPage + 1, pages, detailX + detailWidth - MATERIAL_PAGER_WIDTH - 6,
                    detailPagerY, MATERIAL_PAGER_WIDTH);
        }
    }

    private void drawMaterial(GuiGraphicsExtractor graphics, MaterialRow material, int y, int mouseX, int mouseY) {
        int color = phaseColor(material.phase());
        graphics.fill(detailX + 5, y, detailX + detailWidth - 5, y + MATERIAL_PITCH - 2, INK);
        try {
            SubstanceIcon.draw(graphics, detailX + 8, y + 3, new SubstanceKey(SubstanceId.parse(material.id()),
                    Phase.valueOf(material.phase().toUpperCase(Locale.ROOT))));
        } catch (IllegalArgumentException ignored) {
            ScreenChrome.icon(graphics, Icon.INFO, detailX + 10, y + 5, color);
        }
        String label = SubstanceLabels.shortLabel(material.id(), material.name());
        String visibleLabel = ScreenChrome.fit(font, (material.output() ? "+ " : "") + label, detailWidth - 36);
        graphics.text(font, visibleLabel, detailX + 28, y + 2, PAPER, false);

        String available = material.available().isPresent() ? compact(material.available().getAsLong()) : "?";
        String quantities = material.output() ? "+" + compact(material.required()) + " u"
                : compact(material.required()) + " / " + available + " u";
        int quantityColor = material.output() ? color : material.available().isEmpty() ? MUTED
                : material.available().getAsLong() >= material.required() ? CYAN : UiPalette.WARNING;
        graphics.text(font, ScreenChrome.fit(font, quantities, detailWidth - 36),
                detailX + 28, y + 13, quantityColor, false);
        if (inside(mouseX, mouseY, detailX + 5, y, detailWidth - 10, MATERIAL_PITCH - 2)) {
            String tooltip = label + " · " + material.phase() + "\n" + material.id() + "\n"
                    + (material.output() ? "Produces " : "Needs ") + number(material.required()) + " units";
            if (!material.output()) {
                tooltip += "\nAvailable: " + (material.available().isPresent() ? number(material.available().getAsLong())
                        : "Unknown; inventory view is incomplete");
            }
            graphics.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
        }
    }

    private static String batchValue(ProcessPresentation.Product product) {
        long count = product.outputCount();
        String unit = switch (product.outputKind()) {
            case "fluid" -> " mB";
            case "item" -> count == 1 ? " item" : " items";
            default -> count == 1 ? " substance" : " substances";
        };
        return count + unit;
    }

    private static boolean inside(int mouseX,int mouseY,int x,int y,int width,int height) {
        return mouseX>=x && mouseX<x+width && mouseY>=y && mouseY<y+height;
    }

    private List<MaterialRow> materialRows(ProcessPresentation.Product product, boolean effective) {
        List<MaterialRow> rows = new ArrayList<>();
        if (effective) {
            var preview = menu.snapshot().preview();
            for (var amount : preview.inputs()) rows.add(new MaterialRow(amount.id(), name(amount), amount.phase(), amount.required(),
                    menu.snapshot().capacity() > 0 ? OptionalLong.of(amount.available()) : OptionalLong.empty(), false));
            for (var amount : preview.outputs()) rows.add(new MaterialRow(amount.id(), name(amount), amount.phase(), amount.required(), OptionalLong.empty(), true));
        } else {
            Map<SubstanceKey, Long> available = new HashMap<>();
            for (var entry : menu.stock().entries()) {
                try { available.put(new SubstanceKey(SubstanceId.parse(entry.id()), Phase.valueOf(entry.phase().toUpperCase(Locale.ROOT))), entry.available()); }
                catch (IllegalArgumentException ignored) { /* An unknown phase cannot become a stock estimate. */ }
            }
            boolean complete = menu.stock().complete() && menu.snapshot().capacity() > 0;
            product.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> rows.add(row(entry,
                    ProcessPresentation.available(available, complete, entry.getKey()), false)));
            product.outputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> rows.add(row(entry, OptionalLong.empty(), true)));
        }
        return rows;
    }

    private MaterialRow row(Map.Entry<SubstanceKey, Long> amount, OptionalLong available, boolean output) {
        SubstanceKey key = amount.getKey();
        String name = registry.definition(key.id()).map(definition -> {
            String translated = Component.translatable(definition.translationKey()).getString();
            return translated.equals(definition.translationKey()) ? pretty(key.id().path()) : translated;
        }).orElseGet(() -> pretty(key.id().path()));
        return new MaterialRow(key.id().toString(), name, key.phase().name().toLowerCase(Locale.ROOT), amount.getValue(), available, output);
    }
    private static String name(MachineSnapshot.Amount amount) {
        return SubstanceNames.substance(amount.id(), amount.phase(), amount.known()).getString();
    }
    private record MaterialRow(String id, String name, String phase, long required, OptionalLong available, boolean output) {}

    private static String productSymbol(ProcessPresentation.Product product) {
        return product.outputs().size() == 1
                ? SubstanceLabels.iconLabel(product.outputs().keySet().iterator().next().id()) : "";
    }

    private static String productLabel(ProcessPresentation.Product product) {
        return SubstanceLabels.prefixed(productSymbol(product), product.name());
    }

    private static ItemStack item(String id) {
        Identifier key = Identifier.parse(id);
        return BuiltInRegistries.ITEM.containsKey(key) ? new ItemStack(BuiltInRegistries.ITEM.getValue(key)) : ItemStack.EMPTY;
    }
    private static ItemStack fluidIcon(String id) {
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.FLUID.containsKey(key)) return ItemStack.EMPTY;
        var bucket = BuiltInRegistries.FLUID.getValue(key).getBucket();
        return new ItemStack(bucket == Items.AIR ? Items.BUCKET : bucket);
    }
    private void drawProductIcon(GuiGraphicsExtractor graphics, ProcessPresentation.Product product, int x, int y) {
        ItemStack icon = icons.computeIfAbsent(product.id(), ignored -> {
            if (product.outputKind().equals("item")) return item(product.outputId());
            if (product.outputKind().equals("fluid")) return fluidIcon(product.outputId());
            return ItemStack.EMPTY;
        });
        if (!icon.isEmpty()) graphics.item(icon, x, y);
        else {
            var key = product.outputs().keySet().stream().sorted().findFirst().orElse(null);
            if(key!=null) SubstanceIcon.draw(graphics,x,y,key);
            else ScreenChrome.icon(graphics,Icon.INFO,x+2,y+2,CYAN);
        }
    }

    private static String number(long value) { return String.format(Locale.ROOT, "%,d", value); }
    private static String compact(long value) {
        if (value >= 1_000_000_000_000L) return String.format(Locale.ROOT, "%.1e", (double) value);
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000L) return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        return number(value);
    }
    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20.0); }
    private static String pretty(String id) {
        String value = id.replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private static int phaseColor(String phase) {
        return switch (phase) { case "gas" -> UiPalette.GAS; case "liquid" -> UiPalette.LIQUID; default -> UiPalette.WARNING; };
    }

    private class PanelButton extends Button {
        private final String visualText;
        PanelButton(Builder builder) { this(builder, null); }
        PanelButton(Builder builder, String visualText) { super(builder); this.visualText = visualText; }
        void background(GuiGraphicsExtractor graphics, boolean selected) {
            ScreenChrome.raised(graphics,getX(),getY(),getWidth(),getHeight(),selected || isHoveredOrFocused(),active);
        }
        @Override public boolean shouldTakeFocusAfterInteraction() { return visible; }

        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics,false);
            Icon icon=switch (visualText==null?"":visualText) {
                case "<" -> Icon.PREVIOUS;
                case ">" -> Icon.NEXT;
                case "Use recipe" -> Icon.CHECK;
                case "Close" -> Icon.BACK;
                default -> null;
            };
            boolean iconOnly="<".equals(visualText) || ">".equals(visualText);
            String label=iconOnly?"":visualText==null?getMessage().getString():visualText;
            int iconWidth=icon==null?0:label.isEmpty()?12:16;
            label=ScreenChrome.fit(font,label,getWidth()-iconWidth-6);
            int textWidth=font.width(label);
            int x=getX()+(getWidth()-textWidth-iconWidth)/2;
            if (icon!=null) ScreenChrome.icon(graphics,icon,x,getY()+(getHeight()-12)/2,active?CYAN:MUTED);
            if (!label.isEmpty()) graphics.text(font,label,x+iconWidth,getY()+(getHeight()-8)/2,active?PAPER:MUTED,false);
        }
    }

    private final class ProductButton extends PanelButton {
        private ProcessPresentation.Product product;
        ProductButton(Builder builder) { super(builder); }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (product == null) return;
            background(graphics, product.id().equals(focusedId));
            drawProductIcon(graphics, product, getX() + 7, getY() + 6);
            boolean selected = product.id().equals(menu.snapshot().selectedId());
            if (selected) graphics.fill(getX() + 2, getY() + 4, getX() + 4, getY() + getHeight() - 4, CYAN);
            int textWidth = getWidth() - 34;
            graphics.text(font, ScreenChrome.fit(font, productLabel(product), textWidth),
                    getX() + 28, getY() + 5, PAPER, false);
            String caption = selected ? "Selected" : batchValue(product) + " / batch";
            graphics.text(font, ScreenChrome.fit(font, caption, textWidth),
                    getX() + 28, getY() + 16, selected ? CYAN : MUTED, false);
        }
    }
}
