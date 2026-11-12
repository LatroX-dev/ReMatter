package dev.latrox.rematter.client;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.energy.EnergyCubeMenu;
import dev.latrox.rematter.energy.EnergyCubeSideMode;
import java.util.EnumMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A storage readout, item charging bays and a compact side map; all changes go through the server menu. */
public final class EnergyCubeScreen extends AbstractContainerScreen<EnergyCubeMenu> {
    private final EnumMap<MachinePortMode.Side,DarkButton> sides=new EnumMap<>(MachinePortMode.Side.class);
    private DarkButton storageTab,settingsTab;
    private boolean settings;

    public EnergyCubeScreen(EnergyCubeMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title,216,216);
    }
    private static Component tr(String key,Object... args){return Component.translatable("screen.rematter.energy."+key,args);}
    @Override protected void init() {
        super.init();leftPos=Math.max(23,leftPos);
        storageTab=addRenderableWidget(DarkButton.icon(UiIcon.ENERGY,tr("storage"),tr("storage_hint"),leftPos-22,topPos+18,24,22,b->{settings=false;refresh();}));
        settingsTab=addRenderableWidget(DarkButton.icon(UiIcon.SIDES,tr("sides"),tr("sides_hint"),leftPos-22,topPos+42,24,22,b->{settings=true;refresh();}));
        sides.clear();
        for(var side:MachinePortMode.Side.values()) {
            var pos=sidePosition(side);
            var button=addRenderableWidget(DarkButton.icon(UiIcon.ENERGY,side.label(),tr("side_hint"),leftPos+24+pos[0],topPos+27+pos[1],24,22,b->{
                if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,side.ordinal());
            }));
            sides.put(side,button);
        }
        refresh();
    }
    private void refresh() {
        menu.setSettings(settings);
        storageTab.setAccent(UiPalette.EMBER);settingsTab.setAccent(UiPalette.EMBER);
        storageTab.setSelected(!settings);settingsTab.setSelected(settings);
        for(var entry:sides.entrySet()) {
            var side=entry.getKey();var button=entry.getValue();var mode=menu.sideMode(side);
            button.visible=settings;button.active=settings&&!menu.locked();
            button.setAccent(UiPalette.EMBER);button.setIcon(modeIcon(mode));
            button.setTooltip(Tooltip.create(side.label().copy().append("\n").append(modeText(mode)).append("\n").append(tr("side_hint"))));
            button.setSelected(mode!=EnergyCubeSideMode.DISABLED);
        }
    }
    @Override protected void containerTick(){super.containerTick();refresh();}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial) {
        super.extractBackground(g,mx,my,partial);
        ScreenChrome.panel(g,leftPos,topPos,imageWidth,imageHeight);
        g.fill(leftPos+8,topPos+17,leftPos+208,topPos+18,UiPalette.BORDER);
        g.fill(leftPos+8,topPos+17,leftPos+56,topPos+18,UiPalette.EMBER);
        if(!settings) {
            ScreenChrome.inset(g,leftPos+18,topPos+30,180,12);
            int fill=(int)(176L*menu.energy()/Math.max(1,menu.capacity()));
            g.fill(leftPos+20,topPos+32,leftPos+20+fill,topPos+40,UiPalette.EMBER);
        }
        for(int i=0;i<menu.slots.size();i++) {
            var slot=menu.slots.get(i);if(!slot.isActive())continue;
            ScreenChrome.slot(g,leftPos+slot.x-1,topPos+slot.y-1);
            if(i<menu.tier().chargingSlots()&&!slot.hasItem())ScreenChrome.icon(g,UiIcon.ENERGY,leftPos+slot.x,topPos+slot.y,UiPalette.GHOST);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my) {
        ScreenChrome.text(g,font,title.getString(),8,6,200,UiPalette.TEXT);
        if(settings) {
            var hovered=sides.entrySet().stream().filter(e->e.getValue().isHoveredOrFocused()).map(java.util.Map.Entry::getKey).findFirst().orElse(MachinePortMode.Side.FRONT);
            ScreenChrome.text(g,font,hovered.label().getString(),122,32,80,UiPalette.TEXT);
            ScreenChrome.icon(g,modeIcon(menu.sideMode(hovered)),120,44,UiPalette.EMBER);
            ScreenChrome.twoLines(g,font,modeText(menu.sideMode(hovered)).getString(),138,48,64,UiPalette.TEXT);
            ScreenChrome.twoLines(g,font,tr("map_hint").getString(),122,73,80,UiPalette.MUTED);
        } else {
            ScreenChrome.text(g,font,tr("reserve",Quantities.compact(menu.energy()),Quantities.compact(menu.capacity())).getString(),18,47,180,UiPalette.TEXT);
            ScreenChrome.icon(g,UiIcon.EXCHANGE,16,56,UiPalette.EMBER);
            ScreenChrome.text(g,font,tr("rate",Quantities.number(menu.transferRate())).getString(),34,60,164,UiPalette.MUTED);
            ScreenChrome.icon(g,menu.locked()?UiIcon.STATUS_ERROR:UiIcon.INFO,16,99,menu.locked()?UiPalette.WHITE:UiPalette.MUTED);
            ScreenChrome.text(g,font,tr(menu.locked()?"locked":"charging_hint").getString(),34,103,164,menu.locked()?UiPalette.WARNING:UiPalette.MUTED);
        }
        ScreenChrome.text(g,font,Component.translatable("screen.rematter.inventory").getString(),18,123,180,UiPalette.MUTED);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial) {
        super.extractRenderState(g,mx,my,partial);
        int x=mx-leftPos,y=my-topPos;
        if(!settings&&x>=18&&x<198&&y>=30&&y<56)g.setComponentTooltipForNextFrame(font,java.util.List.of(
                tr("reserve",Quantities.number(menu.energy()),Quantities.number(menu.capacity())),tr("rate_hint",Quantities.number(menu.transferRate()))),mx,my);
        if(!settings)for(int i=0;i<menu.tier().chargingSlots();i++) {
            var slot=menu.slots.get(i);
            if(!slot.hasItem()&&x>=slot.x&&x<slot.x+16&&y>=slot.y&&y<slot.y+16)
                g.setComponentTooltipForNextFrame(font,java.util.List.of(tr("charge_slot"),tr("charge_slot_hint")),mx,my);
        }
        if(!settings&&x>=16&&x<200&&y>=98&&y<115)g.setComponentTooltipForNextFrame(font,java.util.List.of(tr("charging_hint"),tr("rate_hint",Quantities.number(menu.transferRate()))),mx,my);
    }
    private static UiIcon modeIcon(EnergyCubeSideMode mode){return switch(mode){case INPUT->UiIcon.SLOT_INPUT;case OUTPUT->UiIcon.SLOT_OUTPUT;case DISABLED->UiIcon.DISABLED;};}
    private static Component modeText(EnergyCubeSideMode mode){return mode.label();}
    private static int[] sidePosition(MachinePortMode.Side side){return switch(side){
        case TOP->new int[]{28,0};case LEFT->new int[]{0,24};case FRONT->new int[]{28,24};
        case RIGHT->new int[]{56,24};case BOTTOM->new int[]{28,48};case BACK->new int[]{56,48};
    };}
}
