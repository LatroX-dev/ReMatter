package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.MachineChoices;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.SelectProcessPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
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
    private static final int INK = 0xffd6d4ca, PANEL = 0xffeee9dd, EDGE = 0xff8b9590;
    private static final int PAPER = 0xff243639, MUTED = 0xff586b6c, CYAN = 0xff247c78;
    private static final int COPPER = 0xffa15d38, MISSING = 0xffa54731, CELL = 32;
    private static final int CERAMIC = 0xffe7e1d3, CERAMIC_EDGE = 0xff8b9590, LABEL = 0xff243639;
    private final MachineMenu menu;
    private ChemistryRegistry registry;
    private List<ProcessPresentation.Product> products = List.of(), filtered = List.of();
    private final List<ProductButton> tiles = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final Map<SubstanceKey, ItemStack> substanceIcons = new HashMap<>();
    private EditBox search;
    private Button previous, next, detailPrevious, detailNext, confirm;
    private int left, top, panelWidth, panelHeight, columns, gridRows, page, detailPage, detailRows;
    private int detailX, detailWidth, detailPagerY;
    private String focusedId = "";

    public ProcessSelectionScreen(MachineMenu menu) {
        super(Component.literal("Choose a recipe"));
        this.menu = menu;
    }

    @Override protected void init() {
        panelWidth = Math.min(420, width - 12);
        panelHeight = Math.min(300, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        columns = Math.clamp((panelWidth - 180) / CELL, 2, 6);
        gridRows = Math.max(1, (panelHeight - 108) / CELL);
        detailX = left + 20 + columns * CELL;
        detailWidth = left + panelWidth - 10 - detailX;
        detailPagerY = top + panelHeight - 47;
        detailRows = Math.max(1, (detailPagerY - (top + 143)) / 18);
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + 10, top + 28, panelWidth - 20, 18,
                Component.literal("Search products or processes")));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(PAPER);
        search.setHint(Component.literal("Search by name or mod:item"));
        search.setValue(query);
        search.setResponder(value -> { page = 0; filter(); });
        tiles.clear();
        for (int i = 0; i < columns * gridRows; i++) {
            int index = i;
            tiles.add(addRenderableWidget(new ProductButton(Button.builder(Component.empty(), button -> focus(index))
                    .bounds(left + 10 + i % columns * CELL, top + 66 + i / columns * CELL, 29, 29))));
        }
        int footer = top + panelHeight - 27;
        previous = button("<", left + 10, footer, 22, "Previous products", b -> changePage(-1));
        next = button(">", left + 10 + columns * CELL - 25, footer, 22, "Next products", b -> changePage(1));
        detailPrevious = button("<", detailX + 6, detailPagerY, 20, "Previous recipe ingredients", b -> detailPage--);
        detailNext = button(">", detailX + detailWidth - 26, detailPagerY, 20, "Next recipe ingredients", b -> detailPage++);
        confirm = button("Use recipe", detailX, footer, detailWidth - 55,
                "Select this recipe. Start production from the machine controls.", b -> selectFocused());
        button("Close", detailX + detailWidth - 51, footer, 51, "Close without changing the recipe", b -> onClose());
        loadChoices();
        setInitialFocus(search);
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
        substanceIcons.clear();
        // Illustration only: using a profile's icon never grants an output or synthesis permission.
        registry.profiles().stream().filter(profile -> profile.matter().size() == 1)
                .sorted(Comparator.comparingInt((MaterialProfile profile) -> profile.itemId().startsWith("minecraft:") ? 0 : 1)
                        .thenComparing(MaterialProfile::itemId))
                .forEach(profile -> {
                    var icon = item(profile.itemId());
                    if (!icon.isEmpty()) substanceIcons.putIfAbsent(profile.matter().keySet().iterator().next(), icon);
                });
        registry.fluidProfiles().stream().filter(profile -> profile.matter().size() == 1).forEach(profile -> {
            var icon = fluidIcon(profile.fluidId());
            if (!icon.isEmpty()) substanceIcons.putIfAbsent(profile.matter().keySet().iterator().next(), icon);
        });
        if (focusedId.isEmpty()) focusedId = menu.snapshot().selectedId();
        filter();
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id().equals(focusedId)) { page = i / tiles.size(); break; }
        }
        refreshTiles();
    }

    private void filter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = products.stream().filter(product -> product.name().toLowerCase(Locale.ROOT).contains(query)
                || product.id().toLowerCase(Locale.ROOT).contains(query)).toList();
        if (filtered.stream().noneMatch(product -> product.id().equals(focusedId))) {
            focusedId = filtered.isEmpty() ? "" : filtered.getFirst().id();
            detailPage = 0;
        }
        refreshTiles();
    }

    private void refreshTiles() {
        page = Math.clamp(page, 0, pages() - 1);
        for (int i = 0; i < tiles.size(); i++) {
            ProductButton tile = tiles.get(i);
            int index = page * tiles.size() + i;
            tile.product = index < filtered.size() ? filtered.get(index) : null;
            tile.visible = tile.active = tile.product != null;
            if (tile.product != null) {
                tile.setMessage(Component.literal(tile.product.name()));
                tile.setTooltip(Tooltip.create(Component.literal(tile.product.name() + "\n" + tile.product.id() + "\nView recipe details")));
            }
        }
        previous.active = page > 0;
        next.active = page + 1 < pages();
        confirm.active = focused() != null;
    }

    private int pages() { return Math.max(1, (filtered.size() + tiles.size() - 1) / tiles.size()); }
    private void changePage(int step) {
        page = Math.clamp(page + step, 0, pages() - 1);
        if (!filtered.isEmpty()) focusedId = filtered.get(page * tiles.size()).id();
        detailPage = 0;
        refreshTiles();
    }
    private ProcessPresentation.Product focused() {
        return filtered.stream().filter(product -> product.id().equals(focusedId)).findFirst().orElse(null);
    }
    private void focus(int tile) {
        int index = page * tiles.size() + tile;
        if (index < filtered.size()) { focusedId = filtered.get(index).id(); detailPage = 0; }
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
        graphics.fill(left, top, left + panelWidth, top + panelHeight, CERAMIC_EDGE);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, CERAMIC);
        graphics.fill(left + 4, top + 24, left + panelWidth - 4, top + panelHeight - 4, INK);
        graphics.fill(left + 10, top + 20, left + 30, top + 22, COPPER);
        graphics.text(font, title, left + 10, top + 9, LABEL, false);
        graphics.fill(left + 8, top + 26, left + panelWidth - 8, top + 47, EDGE);
        graphics.fill(left + 9, top + 27, left + panelWidth - 9, top + 46, 0xfff7f2e6);
        graphics.text(font, filtered.size() + " results", left + 10, top + 53, MUTED, false);
        String pageLabel = (page + 1) + " / " + pages();
        graphics.text(font, pageLabel, left + 10 + (columns * CELL - font.width(pageLabel)) / 2,
                top + panelHeight - 22, MUTED, false);
        if (filtered.isEmpty()) {
            graphics.text(font, "No matches", left + 12, top + 83, MUTED, false);
            graphics.text(font, "Try another search", left + 12, top + 96, MUTED, false);
        }
        drawDetails(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(detailX, top + 56, detailX + detailWidth, top + panelHeight - 32, EDGE);
        graphics.fill(detailX + 1, top + 57, detailX + detailWidth - 1, top + panelHeight - 33, PANEL);
        ProcessPresentation.Product product = focused();
        detailPrevious.visible = detailNext.visible = product != null;
        if (product == null) { confirm.active = false; return; }
        confirm.active = true;
        boolean selected = product.id().equals(menu.snapshot().selectedId());
        var preview = menu.snapshot().preview();
        boolean effective = selected && preview.id().equals(product.id());
        long energy = effective ? preview.energy() : product.energy();
        int ticks = effective ? preview.ticks() : product.ticks();
        drawProductIcon(graphics, product, detailX + 7, top + 65);
        String name = product.name();
        int nameWidth = detailWidth - 37;
        String first = font.plainSubstrByWidth(name, nameWidth);
        graphics.text(font, first, detailX + 29, top + 64, PAPER, false);
        if (first.length() < name.length()) graphics.text(font,
                font.plainSubstrByWidth(name.substring(first.length()).strip(), nameWidth), detailX + 29, top + 75, PAPER, false);
        String batch = product.outputCount() + (product.outputKind().equals("fluid") ? " mB / batch"
                : product.outputKind().equals("item") ? " item" + (product.outputCount() == 1 ? "" : "s") + " / batch"
                : " substance" + (product.outputCount() == 1 ? "" : "s") + " produced");
        graphics.text(font, batch, detailX + 7, top + 90, CYAN, false);
        graphics.text(font, (effective ? "Cost: " : "Base: ") + compact(energy) + " FE", detailX + 7, top + 103, PAPER, false);
        graphics.text(font, (effective ? "Time: " : "Base time: ") + seconds(ticks) + " s", detailX + 7, top + 114, MUTED, false);
        graphics.fill(detailX + 6, top + 127, detailX + detailWidth - 6, top + 128, EDGE);
        graphics.text(font, "Materials · need / have", detailX + 7, top + 131, MUTED, false);
        // A bounded server preview must not silently hide further recipe ingredients.
        // In that case use the full synchronized catalogue and the independently bounded stock view.
        List<MaterialRow> rows = materialRows(product, effective && preview.complete());
        int pages = Math.max(1, (rows.size() + detailRows - 1) / detailRows);
        detailPage = Math.clamp(detailPage, 0, pages - 1);
        detailPrevious.active = detailPage > 0;
        detailNext.active = detailPage + 1 < pages;
        for (int row = 0; row < detailRows && detailPage * detailRows + row < rows.size(); row++) {
            MaterialRow material = rows.get(detailPage * detailRows + row);
            int y = top + 143 + row * 18;
            int color = phaseColor(material.phase());
            graphics.fill(detailX + 7, y, detailX + 9, y + 14, color);
            String label = (material.output() ? "+ " : "") + material.name();
            graphics.text(font, font.plainSubstrByWidth(label, detailWidth - 22), detailX + 13, y, PAPER, false);
            String quantities = material.output() ? "+" + compact(material.required()) + " u"
                    : compact(material.required()) + " / " + (material.available().isPresent() ? compact(material.available().getAsLong()) : "?") + " u";
            int quantityColor = material.output() ? color : material.available().isEmpty() ? MUTED
                    : material.available().getAsLong() >= material.required() ? CYAN : MISSING;
            graphics.text(font, quantities, detailX + 13, y + 9, quantityColor, false);
            if (mouseX >= detailX + 5 && mouseX < detailX + detailWidth - 5 && mouseY >= y && mouseY < y + 18) {
                String tooltip = material.name() + " · " + material.phase() + "\n" + material.id() + "\n"
                        + (material.output() ? "Produces " : "Needs ") + number(material.required()) + " units"
                        + (material.output() ? "" : "\nAvailable: " + (material.available().isPresent() ? number(material.available().getAsLong()) : "Unknown; inventory view is incomplete"));
                graphics.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
            }
        }
        String detailPageLabel = (detailPage + 1) + " / " + pages;
        graphics.text(font, detailPageLabel, detailX + (detailWidth - font.width(detailPageLabel)) / 2, detailPagerY + 5, MUTED, false);
        if (mouseX >= detailX && mouseX < detailX + detailWidth && mouseY >= top + 59 && mouseY < top + 100) {
            graphics.setTooltipForNextFrame(Component.literal(product.name() + "\n" + product.id() + "\n" + batch
                    + (product.outputKind().equals("matter") ? "\nOutputs enter Matter Storage; icons illustrate substances" : "")
                    + (selected ? "\nCurrently selected" : "\nPreview only; use recipe to select")), mouseX, mouseY);
        } else if (mouseX >= detailX && mouseX < detailX + detailWidth && mouseY >= top + 100 && mouseY < top + 126) {
            graphics.setTooltipForNextFrame(Component.literal(number(energy) + " FE · " + ticks + " ticks\n"
                    + (effective ? "Includes the current machine upgrades" : "Base recipe values; machine upgrades may change cost and duration")
                    + "\nSelecting a recipe does not start production"), mouseX, mouseY);
        }
    }

    private List<MaterialRow> materialRows(ProcessPresentation.Product product, boolean effective) {
        List<MaterialRow> rows = new ArrayList<>();
        if (effective) {
            var preview = menu.snapshot().preview();
            for (var amount : preview.inputs()) rows.add(new MaterialRow(amount.id(), amount.name(), amount.phase(), amount.required(),
                    menu.snapshot().capacity() > 0 ? OptionalLong.of(amount.available()) : OptionalLong.empty(), false));
            for (var amount : preview.outputs()) rows.add(new MaterialRow(amount.id(), amount.name(), amount.phase(), amount.required(), OptionalLong.empty(), true));
        } else {
            Map<SubstanceKey, Long> available = new HashMap<>();
            for (var entry : menu.snapshot().entries()) {
                try { available.put(new SubstanceKey(SubstanceId.parse(entry.id()), Phase.valueOf(entry.phase().toUpperCase(Locale.ROOT))), entry.available()); }
                catch (IllegalArgumentException ignored) { /* An unknown phase cannot become a stock estimate. */ }
            }
            boolean complete = menu.snapshot().entriesComplete() && menu.snapshot().capacity() > 0;
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
    private record MaterialRow(String id, String name, String phase, long required, OptionalLong available, boolean output) {}

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
            return product.outputs().keySet().stream().sorted().map(substanceIcons::get)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(ItemStack.EMPTY);
        });
        if (!icon.isEmpty()) graphics.item(icon, x, y);
        else {
            var key = product.outputs().keySet().stream().sorted().findFirst().orElse(null);
            String symbol = key == null ? "?" : registry.definition(key.id()).map(SubstanceDefinition::symbol).orElse("?");
            int color = key == null ? CYAN : phaseColor(key.phase().name().toLowerCase(Locale.ROOT));
            graphics.fill(x + 1, y + 1, x + 15, y + 15, EDGE);
            graphics.fill(x + 2, y + 2, x + 14, y + 14, INK);
            symbol = font.plainSubstrByWidth(symbol, 18);
            graphics.text(font, symbol, x + 8 - font.width(symbol) / 2, y + 4, color, false);
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
        return switch (phase) { case "gas" -> 0xff806599; case "liquid" -> 0xff367ba0; default -> COPPER; };
    }

    private class PanelButton extends Button {
        private final String visualText;
        PanelButton(Builder builder) { this(builder, null); }
        PanelButton(Builder builder, String visualText) { super(builder); this.visualText = visualText; }
        void background(GuiGraphicsExtractor graphics, boolean selected) {
            int edge = selected || active && isHoveredOrFocused() ? CYAN : active ? EDGE : 0xffaaa99d;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), edge);
            graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
                    selected || active && isHoveredOrFocused() ? 0xffd5e6de : PANEL);
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics, false);
            Component label = visualText == null ? getMessage() : Component.literal(visualText);
            graphics.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - 8) / 2, active ? PAPER : MUTED, false);
        }
    }
    private final class ProductButton extends PanelButton {
        private ProcessPresentation.Product product;
        ProductButton(Builder builder) { super(builder); }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (product == null) return;
            background(graphics, product.id().equals(focusedId));
            drawProductIcon(graphics, product, getX() + 6, getY() + 4);
            if (product.id().equals(menu.snapshot().selectedId())) graphics.fill(getX() + 2, getY() + 2, getX() + 5, getY() + 5, COPPER);
            String quantity = product.outputKind().equals("item") ? product.outputCount() > 1 ? Long.toString(product.outputCount()) : ""
                    : product.outputKind().equals("fluid") ? "mB" : product.outputCount() > 1 ? "+" + (product.outputCount() - 1) : "";
            if (!quantity.isEmpty()) graphics.text(font, quantity, getX() + getWidth() - 3 - font.width(quantity), getY() + 19, PAPER, false);
        }
    }
}
