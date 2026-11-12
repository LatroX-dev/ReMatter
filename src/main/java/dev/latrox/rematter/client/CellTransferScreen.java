package dev.latrox.rematter.client;

import dev.latrox.rematter.celltransfer.CellTransferMenu;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.client.ui.*;
import dev.latrox.rematter.content.CellPartitionData;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.network.CellTransferConfigPayload;
import dev.latrox.rematter.network.CellTransferConfigPayload.Action;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Ghost substance filters edit server-owned selections; the six physical Cells remain ordinary menu slots. */
public final class CellTransferScreen extends AbstractContainerScreen<CellTransferMenu> {
    private static final int COLUMNS=18,ROWS=2,GRID_X=18,GRID_Y=96,PITCH=18;
    private static final int ACCENT=UiPalette.machineAccent(MachineKind.DRIVE);
    private final ScrollModel scroll=new ScrollModel(ROWS);
    private final ScrollBar bar=new ScrollBar(scroll);
    private final List<SubstanceTile> tiles=new ArrayList<>();
    private final List<DarkButton> cellButtons=new ArrayList<>();
    private DarkButton transferTab,partitionTab,transfer,any,only,primalOrContents,clear;
    private EditBox search;
    private boolean partition;
    private int selectedCell=3;
    private long acknowledgement=-1,pending=-1;
    private ChemistryRegistry catalogue;
    private List<SubstanceKey> filtered=List.of();

    public CellTransferScreen(CellTransferMenu menu,Inventory inventory,Component title){super(menu,inventory,title,CellTransferMenu.WIDTH,CellTransferMenu.HEIGHT);}
    private static Component tr(String key,Object... args){return Component.translatable("screen.rematter.transfer."+key,args);}
    @Override protected void init(){
        super.init();leftPos=Math.max(23,leftPos);
        transferTab=addRenderableWidget(DarkButton.icon(UiIcon.EXCHANGE,tr("selection"),tr("selection_hint"),leftPos-22,topPos+18,24,22,b->tab(false)));
        partitionTab=addRenderableWidget(DarkButton.icon(UiIcon.TARGET,tr("partition"),tr("partition_hint"),leftPos-22,topPos+42,24,22,b->tab(true)));
        transfer=addRenderableWidget(DarkButton.labelIcon(UiIcon.EXCHANGE,tr("move"),tr("move_hint"),leftPos+138,topPos+34,84,20,b->send(Action.TRANSFER,-1,null,false)));
        cellButtons.clear();
        for(int i=0;i<6;i++){
            int slot=i;
            cellButtons.add(addRenderableWidget(DarkButton.create(Component.literal(Integer.toString(i%3+1)),tr("configure_slot",slotName(i)),leftPos+CellTransferMenu.CELL_X[i]-1,topPos+54,18,16,b->{selectedCell=slot;partition=true;scroll.reset();rebuild();})));
        }
        any=addRenderableWidget(DarkButton.icon(UiIcon.STOCK,tr("all"),tr("all_hint"),leftPos+18,topPos+73,20,18,b->send(partition?Action.CELL_ANY:Action.GLOBAL_ALL,partition?selectedCell:-1,null,true)));
        only=addRenderableWidget(DarkButton.icon(UiIcon.TARGET,tr("only"),tr("only_hint"),leftPos+42,topPos+73,20,18,b->{if(!partition||policy().map(p->!p.restricted()).orElse(false))send(partition?Action.CELL_CLEAR:Action.GLOBAL_ALL,partition?selectedCell:-1,null,false);}));
        primalOrContents=addRenderableWidget(DarkButton.icon(UiIcon.PRIMAL,tr("primal"),tr("primal_hint"),leftPos+66,topPos+73,20,18,b->send(partition?Action.CELL_TO_CONTENTS:Action.GLOBAL_PRIMAL,partition?selectedCell:-1,null,!menu.selectedPrimal())));
        clear=addRenderableWidget(DarkButton.icon(UiIcon.CLEAR,tr("clear"),tr("clear_hint"),leftPos+90,topPos+73,20,18,b->send(partition?Action.CELL_CLEAR:Action.GLOBAL_CLEAR,partition?selectedCell:-1,null,false)));
        String query=search==null?"":search.getValue();
        search=addRenderableWidget(new EditBox(font,leftPos+134,topPos+77,202,12,tr("search")));
        search.setMaxLength(128);search.setBordered(false);search.setTextColor(UiPalette.TEXT);
        search.setHint(tr("search").copy().withStyle(s->s.withColor(UiPalette.MUTED&0xffffff)));
        search.setValue(query);search.setResponder(value->{scroll.reset();rebuild();});
        tiles.clear();
        for(int i=0;i<COLUMNS*ROWS;i++){
            int index=i;
            tiles.add(addRenderableWidget(new SubstanceTile(Button.builder(Component.empty(),b->toggle(index)).bounds(leftPos+GRID_X+i%COLUMNS*PITCH,topPos+GRID_Y+i/COLUMNS*PITCH,PITCH,PITCH))));
        }
        bar.setBounds(leftPos+345,topPos+GRID_Y,5,ROWS*PITCH);
        rebuild();
    }
    private void tab(boolean value){partition=value;scroll.reset();rebuild();}
    private static Component slotName(int slot){return tr(slot<3?"source_slot":"target_slot",slot%3+1);}
    private Optional<CellPartitionData.Spec> policy(){return CellPartitionData.read(menu.getSlot(selectedCell).getItem());}
    private boolean editable(){return !menu.locked()&&pending<0;}
    private void send(Action action,int slot,SubstanceKey key,boolean value){
        if(!editable())return;
        pending=menu.acknowledgement();
        ClientPacketDistributor.sendToServer(new CellTransferConfigPayload(menu.containerId,menu.revision(),action,slot,key,value));refresh();
    }
    private void toggle(int tile){int index=scroll.offset()*COLUMNS+tile;if(index>=filtered.size())return;var key=filtered.get(index);send(partition?Action.CELL_TOGGLE:Action.GLOBAL_TOGGLE,partition?selectedCell:-1,key,true);}
    private void rebuild(){
        catalogue=ClientChemistry.current();acknowledgement=menu.acknowledgement();
        var keys=new TreeSet<SubstanceKey>();
        if(partition&&policy().isPresent()){
            for(var definition:catalogue.substances())if(!ReservedSubstances.reserved(definition.id()))for(var phase:definition.phases())keys.add(new SubstanceKey(definition.id(),phase));
            policy().ifPresent(spec->keys.addAll(spec.allowed()));
        }else if(!partition)keys.addAll(menu.selection());
        if(!partition||policy().isPresent())menu.available().forEach(entry->keys.add(entry.key()));
        String query=search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered=keys.stream().filter(key->key.id().toString().length()<=CellPartitionData.MAX_ID_LENGTH).filter(key->query.isEmpty()||key.id().toString().contains(query)
                ||SubstanceNames.labelled(key).getString().toLowerCase(Locale.ROOT).contains(query)
                ||SubstanceNames.phase(key.phase()).getString().toLowerCase(Locale.ROOT).contains(query)
                ||SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(key.id()),query)).toList();
        scroll.setSize((filtered.size()+COLUMNS-1)/COLUMNS);refresh();
    }
    private void refresh(){
        if(search==null)return;
        var spec=policy();boolean cellEditable=editable()&&spec.isPresent();
        transferTab.setSelected(!partition);partitionTab.setSelected(partition);
        transfer.active=editable()&&menu.canTransfer();
        transfer.setTooltip(Tooltip.create(tr("move").copy().append("\n").append(tr("status."+menu.status())).append("\n")
                .append(tr("ordinary_amount",Quantities.number(menu.movableOrdinary()))).append("\n").append(tr("primal_amount",Quantities.number(menu.movablePrimal())))));
        any.active=only.active=clear.active=partition?cellEditable:editable();
        primalOrContents.active=partition?cellEditable:editable()&&!menu.selectedAll();
        boolean unrestricted=partition?spec.map(p->!p.restricted()).orElse(false):menu.selectedAll();
        any.setSelected(unrestricted);only.setSelected(!unrestricted&&(partition?spec.isPresent():true));
        any.setTooltip(Tooltip.create(tr(partition?"any_cell":"all").copy().append("\n").append(tr(partition?"any_cell_hint":"all_hint"))));
        only.setTooltip(Tooltip.create(tr(partition?"only_cell":"only").copy().append("\n").append(tr(partition?"only_cell_hint":"only_hint"))));
        primalOrContents.setIcon(partition?UiIcon.LOAD:UiIcon.PRIMAL);
        primalOrContents.setSelected(!partition&&(menu.selectedAll()||menu.selectedPrimal()));
        primalOrContents.setTooltip(Tooltip.create(tr(partition?"contents":"primal").copy().append("\n").append(tr(partition?"contents_hint":"primal_hint"))));
        clear.setTooltip(Tooltip.create(tr("clear").copy().append("\n").append(tr(partition?"clear_cell_hint":"clear_hint"))));
        for(int i=0;i<cellButtons.size();i++){var b=cellButtons.get(i);b.setSelected(partition&&i==selectedCell);b.active=!menu.getSlot(i).getItem().isEmpty();}
        for(var b:List.of(transferTab,partitionTab,transfer,any,only,primalOrContents,clear))b.setAccent(ACCENT);
        cellButtons.forEach(b->b.setAccent(ACCENT));
        for(int i=0;i<tiles.size();i++){
            var tile=tiles.get(i);int index=scroll.offset()*COLUMNS+i;tile.key=index<filtered.size()?filtered.get(index):null;
            tile.visible=!filtered.isEmpty();
            tile.active=(partition?cellEditable:editable())&&tile.key!=null;
            tile.selected=tile.key!=null&&(partition?spec.map(p->p.restricted()&&p.allowed().contains(tile.key)).orElse(false):!menu.selectedAll()&&menu.selection().contains(tile.key));
            if(tile.key!=null){var key=tile.key;long amount=menu.available().stream().filter(e->e.key().equals(key)).mapToLong(e->e.amount()).findFirst().orElse(0);
                var name=SubstanceNames.labelled(key);tile.setMessage(name);
                tile.setTooltip(Tooltip.create(name.copy().append("\n").append(SubstanceNames.phase(key.phase())).append(" · "+key.id()+"\n")
                        .append(partition?tr(tile.selected?"remove_filter":"add_filter"):tr("available",Quantities.number(amount))).append("\n").append(tr(partition?"ghost_hint":"toggle_hint"))));
            }else{tile.setMessage(Component.empty());tile.setTooltip(null);}
        }
    }
    @Override protected void containerTick(){
        super.containerTick();if(pending>=0&&menu.acknowledgement()!=pending)pending=-1;
        if(acknowledgement!=menu.acknowledgement()||catalogue!=ClientChemistry.current())rebuild();else refresh();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractBackground(g,mx,my,partial);ScreenChrome.panel(g,leftPos,topPos,imageWidth,imageHeight);
        g.fill(leftPos+8,topPos+17,leftPos+352,topPos+18,UiPalette.BORDER);g.fill(leftPos+8,topPos+17,leftPos+56,topPos+18,ACCENT);
        for(int i=0;i<menu.slots.size();i++){var slot=menu.slots.get(i);ScreenChrome.slot(g,leftPos+slot.x-1,topPos+slot.y-1);if(i<6&&!slot.hasItem())ScreenChrome.icon(g,UiIcon.SLOT_CELL,leftPos+slot.x,topPos+slot.y,UiPalette.GHOST);}
        ScreenChrome.inset(g,leftPos+116,topPos+73,225,18);ScreenChrome.icon(g,UiIcon.SEARCH,leftPos+117,topPos+74,UiPalette.MUTED);
        bar.extractRenderState(g);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        ScreenChrome.text(g,font,title.getString(),8,6,344,UiPalette.TEXT);
        ScreenChrome.text(g,font,tr("sources").getString(),18,22,72,UiPalette.MUTED);ScreenChrome.text(g,font,tr("targets").getString(),282,22,62,UiPalette.MUTED);
        ScreenChrome.text(g,font,tr("status."+menu.status()).getString(),106,22,148,menu.locked()?UiPalette.WARNING:UiPalette.MUTED);
        ScreenChrome.icon(g,UiIcon.MATTER,111,55,ACCENT);ScreenChrome.text(g,font,Quantities.compact(menu.movableOrdinary()),128,60,48,UiPalette.TEXT);
        ScreenChrome.icon(g,UiIcon.PRIMAL,180,55,UiPalette.PRIMAL);ScreenChrome.text(g,font,Quantities.compact(menu.movablePrimal()),197,60,57,UiPalette.TEXT);
        if(filtered.isEmpty())ScreenChrome.text(g,font,tr(partition&&policy().isEmpty()?"fixed_or_unreadable":"no_matches").getString(),22,110,316,UiPalette.MUTED);
        Component footer=partition?tr("editing",slotName(selectedCell)):tr("selection_count",menu.selectedAll()?tr("all_short"):Component.literal(Integer.toString(menu.selection().size())));
        ScreenChrome.text(g,font,footer.getString(),18,136,76,UiPalette.MUTED);
        ScreenChrome.text(g,font,Component.translatable("screen.rematter.inventory").getString(),99,140,162,UiPalette.MUTED);
        if(menu.partial())ScreenChrome.icon(g,UiIcon.WARNING,326,135,UiPalette.WARNING);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractRenderState(g,mx,my,partial);int x=mx-leftPos,y=my-topPos;
        if(x>=106&&x<254&&y>=21&&y<32)g.setComponentTooltipForNextFrame(font,List.of(tr("status."+menu.status()),tr("move_hint")),mx,my);
        if(x>=106&&x<254&&y>=55&&y<71)g.setComponentTooltipForNextFrame(font,List.of(tr("preview"),tr("ordinary_amount",Quantities.number(menu.movableOrdinary())),tr("primal_amount",Quantities.number(menu.movablePrimal())),tr("last_ordinary",Quantities.number(menu.movedOrdinaryLast())),tr("last_primal",Quantities.number(menu.movedPrimalLast()))),mx,my);
        if(menu.partial()&&x>=326&&x<342&&y>=135&&y<151)g.setComponentTooltipForNextFrame(font,List.of(tr("partial")),mx,my);
        for(int i=0;i<6;i++){var slot=menu.getSlot(i);if(!slot.hasItem()&&x>=slot.x&&x<slot.x+16&&y>=slot.y&&y<slot.y+16)g.setComponentTooltipForNextFrame(font,List.of(slotName(i),tr(i<3?"source_hint":"target_hint")),mx,my);}
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(x>=leftPos+GRID_X&&x<leftPos+351&&y>=topPos+GRID_Y&&y<topPos+GRID_Y+ROWS*PITCH){scroll.wheel(dy);refresh();return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(bar.mouseClicked(event.x(),event.y(),event.button())){refresh();return true;}return super.mouseClicked(event,doubleClick);}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(bar.mouseDragged(event.x(),event.y())){refresh();return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){boolean changed=bar.mouseReleased();return super.mouseReleased(event)||changed;}
    @Override public boolean keyPressed(KeyEvent event){if(search.canConsumeInput()&&!event.isEscape()){if(search.keyPressed(event))return true;if(event.key()!=258)return true;}return super.keyPressed(event);}
    @Override public boolean charTyped(CharacterEvent event){if(search.isFocused())return search.charTyped(event);return super.charTyped(event);}
    private final class SubstanceTile extends Button{
        private SubstanceKey key;private boolean selected;
        private SubstanceTile(Builder builder){super(builder);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){
            ScreenChrome.raised(g,getX(),getY(),getWidth(),getHeight(),selected||isHoveredOrFocused(),active);
            if(key!=null)SubstanceIcon.draw(g,getX()+1,getY()+1,key);
            if(selected)g.fill(getX()+1,getY()+getHeight()-2,getX()+getWidth()-1,getY()+getHeight()-1,ACCENT);
        }
    }
}
