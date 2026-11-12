package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.ScreenChrome.Icon;
import dev.latrox.rematter.client.UiPalette;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Icon and text widgets shared by the ReMatter EMI recipe layouts. */
final class EmiWidgets {
    private EmiWidgets() {}

    static void icon(WidgetHolder widgets, Icon icon, int x, int y, List<Component> tooltip) {
        widgets.addDrawable(x,y,12,12,(graphics,mouseX,mouseY,delta) ->
                ScreenChrome.icon(graphics,icon,0,0,UiPalette.ACCENT));
        widgets.addTooltipText(tooltip,x,y,12,12);
    }
    /** Knowledge can change while the viewer stays open; evaluate only when its tooltip is requested. */
    static void dynamicIcon(WidgetHolder widgets, Icon icon, int x, int y, java.util.function.Supplier<List<Component>> tooltip) {
        widgets.addDrawable(x,y,12,12,(graphics,mouseX,mouseY,delta) -> ScreenChrome.icon(graphics,icon,0,0,UiPalette.ACCENT));
        widgets.addTooltip((mouseX,mouseY)->tooltip.get().stream()
                .map(line->net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(line.getVisualOrderText())).toList(),x,y,12,12);
    }
    /** Text cut to the width, with the full text as a tooltip when it was shortened. */
    static void value(WidgetHolder widgets, String text, int x, int y, int width, int color) {
        var font=Minecraft.getInstance().font;
        String visible=font.plainSubstrByWidth(text,width);
        widgets.addText(Component.literal(visible),x,y,color,false);
        if (!visible.equals(text)) widgets.addTooltipText(List.of(Component.literal(text)),x,y,font.width(visible),9);
    }
}
