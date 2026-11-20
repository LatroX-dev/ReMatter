package dev.latrox.rematter.client.ui;

import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.client.UiIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Accessible text and icon buttons with the same graphite skin. */
public class DarkButton extends Button {
    private @Nullable UiIcon icon;
    private final boolean iconOnly;
    private boolean selected;
    private int accent = UiPalette.ACCENT;

    protected DarkButton(Builder builder,@Nullable UiIcon icon,boolean iconOnly) {
        super(builder);this.icon=icon;this.iconOnly=iconOnly;
    }
    public static DarkButton create(Component name,Component description,int x,int y,int width,int height,OnPress action) {
        return make(null,false,name,description,x,y,width,height,action);
    }
    public static DarkButton icon(UiIcon icon,Component name,Component description,int x,int y,int width,int height,OnPress action) {
        return make(java.util.Objects.requireNonNull(icon),true,name,description,x,y,width,height,action);
    }
    public static DarkButton labelIcon(UiIcon icon,Component name,Component description,int x,int y,int width,int height,OnPress action) {
        return make(java.util.Objects.requireNonNull(icon),false,name,description,x,y,width,height,action);
    }
    public void setIcon(UiIcon icon) { this.icon=java.util.Objects.requireNonNull(icon); }
    private static DarkButton make(@Nullable UiIcon icon,boolean iconOnly,Component name,Component description,
            int x,int y,int width,int height,OnPress action) {
        if(name.getString().isBlank()||description.getString().isBlank())throw new IllegalArgumentException("Controls need a name and description");
        if(width<16||height<16)throw new IllegalArgumentException("Controls need a sixteen-pixel target");
        var builder=Button.builder(name,action).bounds(x,y,width,height)
                .tooltip(Tooltip.create(name.copy().append("\n").append(description)));
        return new DarkButton(builder,icon,iconOnly);
    }
    public void setAccent(int accent) { this.accent=accent; }
    public void setSelected(boolean selected) { this.selected=selected; }
    @Override public boolean shouldTakeFocusAfterInteraction() { return visible; }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        var font=Minecraft.getInstance().font;
        ScreenChrome.raised(g,getX(),getY(),getWidth(),getHeight(),selected||isHoveredOrFocused(),active);
        if(selected&&active)g.fill(getX()+1,getY()+getHeight()-3,getX()+getWidth()-2,getY()+getHeight()-1,accent);
        int color=active?UiPalette.TEXT:UiPalette.MUTED;
        if(iconOnly) {
            ScreenChrome.icon(g,icon,getX()+(getWidth()-ScreenChrome.ICON_SIZE)/2,getY()+(getHeight()-ScreenChrome.ICON_SIZE)/2,
                    active?accent:UiPalette.MUTED);
        } else {
            String label=getMessage().getString();
            int labelWidth=Math.min(font.width(label),getWidth()-(icon==null?6:20));
            int groupWidth=labelWidth+(icon==null?0:16);
            int groupX=getX()+(getWidth()-groupWidth)/2;
            if(icon!=null)ScreenChrome.icon(g,icon,groupX-2,getY()+(getHeight()-16)/2,selected&&active?accent:color);
            ScreenChrome.text(g,font,label,groupX+(icon==null?0:16),getY()+(getHeight()-8)/2,labelWidth,color);
        }
    }
}
