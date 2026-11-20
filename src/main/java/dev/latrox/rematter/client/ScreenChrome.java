package dev.latrox.rematter.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import static dev.latrox.rematter.client.UiPalette.*;

/** Pixel-aligned, font-independent controls shared by ReMatter screens and recipe panels. */
public final class ScreenChrome {
    public static final int ICON_SIZE = 16;
    private static final Identifier PANEL_SKIN = skin("panel"), BUTTON_SKIN = skin("button"),
            BUTTON_HOVER = skin("button_hover"), BUTTON_DISABLED = skin("button_disabled"),
            INSET_SKIN = skin("inset"), SLOT_SKIN = skin("slot");
    private static Identifier skin(String name) { return Identifier.fromNamespaceAndPath("rematter", name); }

    private ScreenChrome() {}

    /** A bounded readout retains the whole value. Interactive controls expose full names in their tooltip. */
    public static void text(GuiGraphicsExtractor graphics,Font font,String value,int x,int y,int width,int color) {
        if(width<=0||value.isEmpty())return;
        float scale=Math.min(1F,(float)width/Math.max(1,font.width(value)));
        var pose=graphics.pose();pose.pushMatrix();
        pose.translate(x,y+(1F-scale)*4F);pose.scale(scale,scale);
        graphics.text(font,value,0,0,color,false);pose.popMatrix();
    }

    /** Balance complete words over two lines; no characters are discarded to fit a hint. */
    public static void twoLines(GuiGraphicsExtractor graphics,Font font,String value,int x,int y,int width,int color) {
        if(width<=0||value.isEmpty())return;
        if(font.width(value)<=width){text(graphics,font,value,x,y,width,color);return;}
        int split=0,widest=font.width(value);
        for(int i=1;i<value.length();i++)if(Character.isWhitespace(value.charAt(i))) {
            int candidate=Math.max(font.width(value.substring(0,i)),font.width(value.substring(i+1)));
            if(candidate<widest){widest=candidate;split=i;}
        }
        if(split==0){text(graphics,font,value,x,y,width,color);return;}
        String first=value.substring(0,split),second=value.substring(split+1);
        float scale=Math.min(1F,(float)width/Math.max(1,widest));
        var pose=graphics.pose();pose.pushMatrix();pose.translate(x,y);pose.scale(scale,scale);
        graphics.text(font,first,0,0,color,false);graphics.text(font,second,0,10,color,false);pose.popMatrix();
    }

    public static void icon(GuiGraphicsExtractor g, UiIcon icon, int x, int y, int color) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite, x, y, ICON_SIZE, ICON_SIZE, color);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SKIN, x, y, width, height);
    }

    public static void raised(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean highlighted, boolean enabled) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_DISABLED : highlighted ? BUTTON_HOVER : BUTTON_SKIN,
                x, y, width, height);
    }

    public static void inset(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, INSET_SKIN, x, y, width, height);
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SKIN, x, y, 18, 18);
    }
}
