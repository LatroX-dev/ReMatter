package dev.latrox.rematter.client;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.content.MachineMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Search a complete explicit conversion allowlist; unreached research stays visible and cannot be selected. */
public final class PrimalTargetScreen extends SubstanceScreenLayer {
    private static final int ROW=28;
    private final MachineMenu menu;
    private final Consumer<String> select;
    private final ScrollModel scroll=new ScrollModel(4);
    private final ScrollBar bar=new ScrollBar(scroll);
    private final List<TargetButton> rows=new ArrayList<>();
    private List<PrimalConverterView.Target> targets=List.of(),filtered=List.of();
    private com.google.gson.JsonObject source;
    private net.minecraft.locale.Language language;
    private EditBox search;
    private DarkButton confirm,clear;
    private String focused="";
    private int left,top,panelWidth,panelHeight,listTop,footer;

    public PrimalTargetScreen(MachineMenu menu,Consumer<String> select){super(tr("choose"));this.menu=menu;this.select=select;focused=PrimalConverterView.text(menu.extras(),"conversionTarget","");}
    private static Component tr(String key,Object...args){return Component.translatable("screen.rematter.converter."+key,args);}
    @Override protected void init(){
        rememberLayout();
        panelWidth=Math.min(320,width-12);panelHeight=Math.min(224,height-12);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        listTop=top+45;footer=top+panelHeight-25;scroll.setVisibleRows(Math.max(1,(footer-listTop-5)/ROW));
        String query=search==null?"":search.getValue();
        search=addRenderableWidget(new EditBox(font,left+27,top+26,panelWidth-59,12,tr("search")));
        search.setMaxLength(128);search.setBordered(false);search.setTextColor(UiPalette.TEXT);
        search.setHint(tr("search").copy().withStyle(style->style.withColor(UiPalette.MUTED&0xffffff)));
        search.setValue(query);search.setResponder(value->{scroll.reset();filter();});
        clear=addRenderableWidget(DarkButton.icon(UiIcon.CLEAR,tr("clear_search"),tr("clear_search_hint"),left+panelWidth-26,top+21,18,18,b->{search.setValue("");setFocused(search);}));
        rows.clear();for(int row=0;row<scroll.visibleRows();row++){
            int index=row;rows.add(addRenderableWidget(new TargetButton(Button.builder(Component.empty(),b->focus(index)).bounds(left+8,listTop+row*ROW,panelWidth-22,ROW))));
        }
        confirm=addRenderableWidget(DarkButton.labelIcon(UiIcon.CONFIRM,tr("use_target"),tr("use_target_hint"),left+8,footer,panelWidth-64,18,b->choose()));
        addRenderableWidget(DarkButton.icon(UiIcon.CANCEL,tr("close"),tr("close_hint"),left+panelWidth-26,footer,18,18,b->onClose()));
        confirm.setAccent(UiPalette.PRIMAL);clear.setAccent(UiPalette.PRIMAL);bar.setBounds(left+panelWidth-12,listTop,5,scroll.visibleRows()*ROW);
        reload();setInitialFocus(search);
    }
    private void reload(){source=menu.extras();language=net.minecraft.locale.Language.getInstance();targets=PrimalConverterView.targets(source).stream().sorted(Comparator.comparing(PrimalConverterView.Target::known).reversed().thenComparing(t->label(t.id()),String.CASE_INSENSITIVE_ORDER)).toList();filter();}
    private void filter(){
        String query=search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered=targets.stream().filter(t->label(t.id()).toLowerCase(Locale.ROOT).contains(query)||t.id().toLowerCase(Locale.ROOT).contains(query)||SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(t.id()),query)).toList();
        scroll.setSize(filtered.size());int index=0;for(int i=0;i<filtered.size();i++)if(filtered.get(i).id().equals(focused)){index=i;break;}
        scroll.select(index);focused=filtered.isEmpty()?"":filtered.get(scroll.selected()).id();refresh();
    }
    private PrimalConverterView.Target focused(){return filtered.stream().filter(t->t.id().equals(focused)).findFirst().orElse(null);}
    private void refresh(){
        for(int row=0;row<rows.size();row++){
            var button=rows.get(row);int index=scroll.offset()+row;button.target=index<filtered.size()?filtered.get(index):null;
            button.visible=button.active=button.target!=null;
            if(button.target!=null){var t=button.target;button.setMessage(Component.literal(label(t.id())));button.setTooltip(Tooltip.create(SubstanceDetails.joined(SubstanceDetails.tooltip(hover(t),true)).append("\n").append(ratio(t)).append("\n").append(t.known()?tr("known_target"):tr("locked_target",name(t.id())))));}
        }
        var selected=focused();confirm.active=selected!=null&&selected.known()&&!PrimalConverterView.flag(menu.extras(),"converting",false);
        clear.visible=!search.getValue().isEmpty();
    }
    private void focus(int row){int index=scroll.offset()+row;if(index>=filtered.size())return;scroll.select(index);focused=filtered.get(index).id();refresh();}
    private void choose(){var value=focused();if(value!=null&&value.known()&&confirm.active&&minecraft.player!=null&&minecraft.player.containerMenu==menu){select.accept(value.id());onClose();}}
    @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=menu){onClose();return;}if(source!=menu.extras()||language!=net.minecraft.locale.Language.getInstance())reload();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(inside(x,y,left+8,listTop,panelWidth-16,scroll.visibleRows()*ROW)){scroll.wheel(dy);refresh();return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(event.button()==1){var hovered=substanceAt(event.x(),event.y());if(hovered.isPresent()){SubstanceDetails.open(hovered.get(),UiPalette.PRIMAL);return true;}}if(bar.mouseClicked(event.x(),event.y(),event.button())){refresh();return true;}boolean result=super.mouseClicked(event,doubleClick);if(result&&doubleClick&&event.button()==0&&inside(event.x(),event.y(),left+8,listTop,panelWidth-22,scroll.visibleRows()*ROW))choose();return result;}
    private static SubstanceDetails.Hover hover(PrimalConverterView.Target target){return new SubstanceDetails.Hover(dev.latrox.rematter.chemistry.SubstanceKey.of(target.id(),dev.latrox.rematter.chemistry.Phase.SOLID),1);}
    @Override public java.util.Optional<SubstanceDetails.Hover> substanceAt(double x,double y){for(var row:rows)if(row.visible&&row.target!=null&&inside(x,y,row.getX(),row.getY(),row.getWidth(),row.getHeight()))return java.util.Optional.of(hover(row.target));return java.util.Optional.empty();}
    @Override public net.minecraft.client.renderer.Rect2i substancePanelBounds(){return new net.minecraft.client.renderer.Rect2i(left,top,panelWidth,panelHeight);}
    @Override public int substanceAccent(){return UiPalette.PRIMAL;}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(bar.mouseDragged(event.x(),event.y())){refresh();return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){return super.mouseReleased(event)|bar.mouseReleased();}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.isEscape()){onClose();return true;}int key=event.key();if(key==257||key==335){choose();return true;}
        if((key==264||key==265||key==266||key==267||!search.isFocused()&&(key==268||key==269))&&scroll.key(key)){focused=filtered.isEmpty()?"":filtered.get(scroll.selected()).id();refresh();return true;}
        if(search.canConsumeInput()){if(search.keyPressed(event))return true;if(key!=258)return true;}return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event){if(event.isAllowedChatCharacter()){setFocused(search);return search.charTyped(event);}return super.charTyped(event);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        refresh();ScreenChrome.panel(g,left,top,panelWidth,panelHeight);ScreenChrome.icon(g,UiIcon.TARGET,left+6,top+1,UiPalette.PRIMAL);
        ScreenChrome.text(g,font,title.getString(),left+24,top+5,panelWidth-32,UiPalette.TEXT);
        g.fill(left+8,top+17,left+panelWidth-8,top+18,UiPalette.BORDER);g.fill(left+8,top+17,left+56,top+18,UiPalette.PRIMAL);
        ScreenChrome.inset(g,left+8,top+21,panelWidth-16,18);ScreenChrome.icon(g,UiIcon.SEARCH,left+9,top+22,UiPalette.MUTED);bar.extractRenderState(g);
        if(filtered.isEmpty())ScreenChrome.twoLines(g,font,tr("no_matches").getString(),left+12,listTop+8,panelWidth-28,UiPalette.MUTED);
        super.extractRenderState(g,mx,my,partial);
    }
    static Component name(String id){return SubstanceNames.substance(id,"solid",true);}
    static String label(String id){return SubstanceLabels.prefixed(SubstanceLabels.iconLabel(id),name(id).getString());}
    static Component ratio(PrimalConverterView.Target t){return tr("ratio",t.ratio(),SubstanceLabels.iconLabel(t.id()));}
    private static boolean inside(double x,double y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    private final class TargetButton extends DarkButton {
        private PrimalConverterView.Target target;
        TargetButton(Button.Builder builder){super(builder,null,false);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){
            if(target==null)return;boolean chosen=target.id().equals(focused);int color=target.known()?UiPalette.TEXT:UiPalette.MUTED;
            ScreenChrome.raised(g,getX(),getY(),getWidth(),getHeight(),chosen||isHoveredOrFocused(),true);
            ScreenChrome.icon(g,target.known()?UiIcon.UNLOCKED:UiIcon.LOCKED,getX()+1,getY()+6,target.known()?UiPalette.PRIMAL:UiPalette.MUTED);
            ScreenChrome.text(g,font,label(target.id()),getX()+22,getY()+4,getWidth()-26,color);
            ScreenChrome.text(g,font,(target.known()?ratio(target):tr("locked_target",name(target.id()))).getString(),getX()+22,getY()+16,getWidth()-26,target.known()?UiPalette.PRIMAL_LIGHT:UiPalette.MUTED);
            if(chosen)g.fill(getX()+1,getY()+getHeight()-2,getX()+getWidth()-1,getY()+getHeight()-1,UiPalette.PRIMAL);
        }
    }
}
