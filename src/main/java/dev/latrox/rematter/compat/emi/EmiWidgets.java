package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.UiIcon;
import dev.latrox.rematter.client.UiPalette;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Icon and text widgets shared by the ReMatter EMI recipe layouts. */
final class EmiWidgets {
    private EmiWidgets() {}

    static void icon(WidgetHolder widgets, UiIcon icon, int x, int y, List<Component> tooltip) {
        widgets.addDrawable(x-2,y-2,16,16,(graphics,mouseX,mouseY,delta) ->
                ScreenChrome.icon(graphics,icon,0,0,UiPalette.ACCENT));
        widgets.addTooltipText(tooltip,x-2,y-2,16,16);
    }
    /** Knowledge can change while the viewer stays open; evaluate only when its tooltip is requested. */
    static void dynamicIcon(WidgetHolder widgets, UiIcon icon, int x, int y, java.util.function.Supplier<List<Component>> tooltip) {
        widgets.addDrawable(x-2,y-2,16,16,(graphics,mouseX,mouseY,delta) -> ScreenChrome.icon(graphics,icon,0,0,UiPalette.ACCENT));
        widgets.addTooltip((mouseX,mouseY)->tooltip.get().stream()
                .map(line->net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(line.getVisualOrderText())).toList(),x-2,y-2,16,16);
    }
    /** Keep the entire value in its allotted width, with a full-size tooltip for long values. */
    static void value(WidgetHolder widgets, String text, int x, int y, int width, int color) {
        var font=Minecraft.getInstance().font;
        widgets.addDrawable(x,y,width,9,(graphics,mouseX,mouseY,delta)->ScreenChrome.text(graphics,font,text,0,0,width,color));
        if (font.width(text)>width) widgets.addTooltipText(List.of(Component.literal(text)),x,y,width,9);
    }
}
