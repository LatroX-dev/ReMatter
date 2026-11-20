package dev.latrox.rematter.client;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** A scrollable read-only layer: opening it does not close its machine or send a server action. */
public final class SubstanceDetailsScreen extends SubstanceScreenLayer {
    private static final int LINE = 11;
    private final SubstanceDetails.Hover substance;
    private final AbstractContainerMenu menu;
    private final int accent;
    private final ScrollModel scroll = new ScrollModel(12);
    private final ScrollBar bar = new ScrollBar(scroll);
    private List<FormattedCharSequence> lines = List.of();
    private Object catalogue, language;
    private int left, top, panelWidth, panelHeight, textTop;
    private DarkButton recipes, uses;

    public SubstanceDetailsScreen(SubstanceDetails.Hover substance, AbstractContainerMenu menu, int accent) {
        super(SubstanceDetails.tr("title")); this.substance = substance; this.menu = menu; this.accent = accent;
    }
    @Override protected void init() {
        rememberLayout();
        panelWidth = Math.min(328, width - 12); panelHeight = Math.min(256, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2; textTop = top + 45;
        scroll.setVisibleRows(Math.max(1, (panelHeight - 78) / LINE));
        recipes = addRenderableWidget(DarkButton.icon(UiIcon.RECIPE, SubstanceDetails.tr("recipes"), SubstanceDetails.tr("recipes_hint"),
                left + 8, top + panelHeight - 25, 20, 18, button -> SubstanceDetails.recipes(substance)));
        uses = addRenderableWidget(DarkButton.icon(UiIcon.DERIVE, SubstanceDetails.tr("uses"), SubstanceDetails.tr("uses_hint"),
                left + 32, top + panelHeight - 25, 20, 18, button -> SubstanceDetails.uses(substance)));
        addRenderableWidget(DarkButton.icon(UiIcon.BACK, SubstanceDetails.tr("back"), SubstanceDetails.tr("back_hint"),
                left + panelWidth - 28, top + panelHeight - 25, 20, 18, button -> onClose()));
        for (var button : List.of(recipes, uses)) button.setAccent(accent);
        bar.setBounds(left + panelWidth - 13, textTop, 5, scroll.visibleRows() * LINE);
        rebuild();
    }
    private void rebuild() {
        catalogue = ClientChemistry.current(); language = net.minecraft.locale.Language.getInstance();
        lines = SubstanceDetails.information(substance).stream().flatMap(line -> font.split(line, panelWidth - 32).stream()).toList();
        scroll.setSize(lines.size());
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) { onClose(); return; }
        if (catalogue != ClientChemistry.current() || language != net.minecraft.locale.Language.getInstance()) rebuild();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        recipes.active = uses.active = SubstanceDetails.viewerAvailable();
        ScreenChrome.panel(graphics, left, top, panelWidth, panelHeight);
        ScreenChrome.icon(graphics, UiIcon.INFO, left + 6, top + 1, accent);
        ScreenChrome.text(graphics, font, title.getString(), left + 24, top + 5, panelWidth - 32, UiPalette.TEXT);
        graphics.fill(left + 8, top + 17, left + panelWidth - 8, top + 18, UiPalette.BORDER);
        SubstanceIcon.draw(graphics, left + 8, top + 23, substance.key());
        ScreenChrome.text(graphics, font, SubstanceNames.labelled(substance.key()).getString(), left + 30, top + 27, panelWidth - 38, UiPalette.TEXT);
        for (int row = 0; row < scroll.visibleRows() && scroll.offset() + row < lines.size(); row++)
            graphics.text(font, lines.get(scroll.offset() + row), left + 9, textTop + row * LINE, UiPalette.TEXT, false);
        bar.extractRenderState(graphics);
        if (!SubstanceDetails.viewerAvailable()) ScreenChrome.text(graphics, font, SubstanceDetails.tr("no_viewer").getString(),
                left + 58, top + panelHeight - 20, panelWidth - 92, UiPalette.MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        if (substanceAt(mouseX, mouseY).isPresent()) graphics.setComponentTooltipForNextFrame(font, SubstanceDetails.tooltip(substance, false), mouseX, mouseY);
    }
    @Override public Optional<SubstanceDetails.Hover> substanceAt(double x, double y) {
        return x >= left + 8 && x < left + panelWidth - 8 && y >= top + 22 && y < top + 41 ? Optional.of(substance) : Optional.empty();
    }
    @Override public Rect2i substancePanelBounds() { return new Rect2i(left, top, panelWidth, panelHeight); }
    @Override public int substanceAccent() { return accent; }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { scroll.wheel(dy); return true; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (bar.mouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return bar.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return bar.mouseReleased() | super.mouseReleased(event); }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        switch (key) {
            case 265 -> scroll.scroll(-1);
            case 264 -> scroll.scroll(1);
            case 266 -> scroll.scroll(-scroll.visibleRows());
            case 267 -> scroll.scroll(scroll.visibleRows());
            case 268 -> scroll.setOffset(0);
            case 269 -> scroll.setOffset(scroll.maxOffset());
            default -> { return super.keyPressed(event); }
        }
        return true;
    }
}
