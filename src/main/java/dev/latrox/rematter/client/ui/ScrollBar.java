package dev.latrox.rematter.client.ui;

import dev.latrox.rematter.client.ScreenChrome;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One draggable scrollbar shared by fixed-row lists. */
public final class ScrollBar {
    private static final Identifier THUMB=Identifier.fromNamespaceAndPath("rematter","scroll_thumb");
    private static final Identifier ACTIVE_THUMB=Identifier.fromNamespaceAndPath("rematter","scroll_thumb_active");
    private final ScrollModel model;
    private int x, y, width = 5, height = 16;
    private boolean dragging;
    private double grab;

    public ScrollBar(ScrollModel model) { this.model = java.util.Objects.requireNonNull(model); }
    public void setBounds(int x, int y, int width, int height) {
        this.x=x; this.y=y; this.width=Math.max(3,width); this.height=Math.max(12,height);
    }
    public boolean contains(double mx, double my) { return mx>=x&&mx<x+width&&my>=y&&my<y+height; }
    private int thumbHeight() { return Math.min(height,Math.max(12,model.size()==0?height:height*model.visibleRows()/model.size())); }
    private int thumbY() { return y+(model.maxOffset()==0?0:(height-thumbHeight())*model.offset()/model.maxOffset()); }
    public void extractRenderState(GuiGraphicsExtractor g) {
        ScreenChrome.inset(g,x,y,width,height);
        if (!model.scrollable()) return;
        int top=thumbY();
        g.blitSprite(RenderPipelines.GUI_TEXTURED,dragging?ACTIVE_THUMB:THUMB,x+1,top,width-2,thumbHeight());
    }
    public boolean mouseClicked(double mx,double my,int button) {
        if (button!=0||!model.scrollable()||!contains(mx,my)) return false;
        int top=thumbY();
        grab=my>=top&&my<top+thumbHeight()?my-top:thumbHeight()/2.0;
        dragging=true;
        return mouseDragged(mx,my);
    }
    public boolean mouseDragged(double mx,double my) {
        if (!dragging) return false;
        int travel=height-thumbHeight();
        model.setOffset(travel<=0?0:(int)Math.round((my-y-grab)*model.maxOffset()/travel));
        return true;
    }
    public boolean mouseReleased() { boolean previous=dragging;dragging=false;return previous; }
}
