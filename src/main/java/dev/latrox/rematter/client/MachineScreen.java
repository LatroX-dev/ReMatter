package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;
import net.minecraft.client.renderer.RenderPipelines;
import dev.latrox.rematter.client.ui.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.*;
import dev.latrox.rematter.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Compact machine controls, a real storage list and embedded terminal devices; all writes stay server-authoritative. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> implements SubstanceHoverProvider {
    private static final int W=216,H=216;
    private final ScrollModel stockScroll=new ScrollModel(3),deviceScroll=new ScrollModel(5);
    private final ScrollBar stockBar=new ScrollBar(stockScroll),deviceBar=new ScrollBar(deviceScroll);
    private final List<StockRow> stockRows=new ArrayList<>();
    private final List<DeviceRow> deviceRows=new ArrayList<>();
    private final List<DarkButton> filters=new ArrayList<>();
    private final Set<String> phaseFilter=new HashSet<>();
    private EditBox search;
    private DarkButton machineTab,devicesTab,researchTab,settingsTab,choose,primary,repeat,pause,buildHint,back;
    private MachineSettingsPanel settingsPanel;
    private ResearchPanel researchPanel;
    private PrimalConverterPanel converterPanel;
    private boolean favoritesOnly,observing;
    private String deviceSearch="";
    private List<MachineSnapshot.Entry> shownEntries=List.of();
    private List<TerminalDevicesPayload.Device> shownDevices=List.of();
    private StockQuery sentQuery;
    private int shownFavorites=-1;
    private MachineMenu.Stock shownStock;
    private ChemistryRegistry shownCatalogue;
    private StockFilter shownFilter;
    private net.minecraft.locale.Language shownLanguage;
    private int layoutWidth=-1,layoutHeight=-1;

    public MachineScreen(MachineMenu menu,Inventory inventory,Component title) {super(menu,inventory,title,W,H);}
    private static Component tr(String key,Object...args){return Component.translatable("screen.rematter."+key,args);}
    private static Component ui(String key,Object...args){return Component.translatable("screen.rematter.ui."+key,args);}
    private boolean main(){return menu.view()==MachineMenu.View.MAIN;}
    private boolean configuration(){return menu.view()==MachineMenu.View.CONFIGURATION;}
    private boolean machines(){return menu.view()==MachineMenu.View.MACHINES&&menu.kind()==MachineKind.TERMINAL;}
    private boolean passive(){return !menu.kind().processes()&&!menu.kind().generator();}
    private boolean researching(){return menu.view()==MachineMenu.View.RESEARCH&&menu.kind()==MachineKind.ANALYZER;}
    private boolean converter(){return menu.kind()==MachineKind.PRIMAL_CONVERTER;}
    private boolean disk(){return menu.diskRole();}
    private boolean terminal(){return menu.kind()==MachineKind.TERMINAL;}
    private boolean handPreparator(){return menu.kind()==MachineKind.HAND_PREPARATOR;}
    private boolean preparator(){return dev.latrox.rematter.content.preparation.PreparationTier.isPreparation(menu.kind());}
    private boolean industrial(){return IndustrialStructure.isIndustrial(menu.kind());}
    private boolean selecting(){return menu.kind().selectable();}
    private int accent(){return machineAccent(menu.kind());}
    private static boolean working(String s){return List.of("working","grinding","sieving","collecting","dosing","mixing","packing","pressing","sintering","cooling").contains(s);}
    private boolean driveRecovery(){return disk()&&java.util.stream.IntStream.of(1,2,3,4,10,11,12,13).anyMatch(i->menu.slots.get(i).hasItem());}
    private boolean storageList(){return main()&&passive();}
    private int stockX(){return disk()?82:8;}
    private int stockY(){return terminal()?50:48;}
    private int stockWidth(){return 180-stockX();}
    private int stockCount(){return terminal()?8:3;}
    private UUID player(){return minecraft.player==null?null:minecraft.player.getUUID();}
    private boolean favorite(MachineSnapshot.StockKey key){return player()!=null&&StockFavorites.get().contains(player(),key);}
    private void toggleFavorite(MachineSnapshot.Entry entry){if(player()!=null){StockFavorites.get().toggle(player(),entry.key());shownFilter=null;refresh();}}

    @Override protected void init(){
        layoutWidth=width;layoutHeight=height;
        super.init();leftPos=Math.max(23,leftPos);
        String query=search==null?"":search.getValue();
        machineTab=tab(UiIcon.MACHINE,ui("tab_machine"),0,MachineMenu.View.MAIN);
        devicesTab=tab(UiIcon.DEVICES,ui("tab_machines"),1,MachineMenu.View.MACHINES);
        researchTab=tab(UiIcon.RESEARCH,ui("tab_research"),1,MachineMenu.View.RESEARCH);
        settingsTab=tab(UiIcon.SETTINGS,ui("tab_settings"),2,MachineMenu.View.CONFIGURATION);
        search=addRenderableWidget(new EditBox(font,leftPos+27,topPos+24,82,12,tr("search")));
        search.setBordered(false);search.setTextColor(TEXT);search.setMaxLength(128);search.setValue(query);
        search.setResponder(value->{stockScroll.setOffset(0);deviceScroll.setOffset(0);shownFilter=null;refresh();});
        filters.clear();
        for(int i=0;i<4;i++){
            int index=i;var icon=new UiIcon[]{UiIcon.STAR,UiIcon.SOLID,UiIcon.LIQUID,UiIcon.GAS}[i];
            var label=tr("filter_"+new String[]{"favorites","solid","liquid","gas"}[i]);
            var b=addRenderableWidget(DarkButton.icon(icon,label,tr("filter_"+new String[]{"favorites","solid","liquid","gas"}[i]+"_hint"),leftPos+116+i*18,topPos+20,16,16,button->{
                if(index==0)favoritesOnly=!favoritesOnly;
                else{String phase=new String[]{"","solid","liquid","gas"}[index];if(!phaseFilter.remove(phase))phaseFilter.add(phase);}
                stockScroll.setOffset(0);shownFilter=null;refresh();
            }));b.setAccent(accent());filters.add(b);
        }
        choose=button(ui("recipe"),tr("choose_hint"),24,20,164,b->{if(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR)action(MachineActionPayload.Action.ASSAY,"",0);else if(selecting())minecraft.pushGuiLayer(new ProcessSelectionScreen(menu));});
        primary=button(tr("start"),tr("start_once_hint"),24,102,58,b->{if(menu.kind().generator()){send(2);return;}var d=MachinePrimaryAction.forSnapshot(menu.snapshot());if(d.opensRecipeSelection())minecraft.pushGuiLayer(new ProcessSelectionScreen(menu));else if(selecting()&&!menu.snapshot().networkJobs()&&validSelection())send(4);else if(d.enabled()&&d.menuAction()>=0)send(d.menuAction());});
        repeat=addRenderableWidget(DarkButton.icon(UiIcon.LOOP_OFF,ui("repeat_off"),tr("continuous_hint"),leftPos+86,topPos+102,18,18,b->send(5)));
        pause=addRenderableWidget(DarkButton.icon(UiIcon.PAUSE,tr("pause"),tr("pause_hint"),leftPos+108,topPos+102,18,18,b->send(2)));
        buildHint=button(ui("missing_part"),tr("locate_part_hint"),24,102,138,b->onClose());
        back=addRenderableWidget(DarkButton.icon(UiIcon.BACK,tr("terminal.return"),tr("terminal.return_hint"),leftPos+171,topPos+2,17,16,b->send(MachineMenu.RETURN_TO_TERMINAL)));
        stockRows.clear();for(int i=0;i<8;i++){var row=new StockRow();stockRows.add(addRenderableWidget(row));}
        deviceRows.clear();for(int i=0;i<5;i++){var row=new DeviceRow();deviceRows.add(row);addRenderableWidget(row.open);addRenderableWidget(row.queue);}
        settingsPanel=new MachineSettingsPanel(menu,minecraft,font,w->addRenderableWidget(w),this::rebuildWidgets,leftPos+8,topPos+20,180);
        settingsPanel.initWidgets();
        researchPanel=new ResearchPanel(menu,minecraft,font,w->addRenderableWidget(w),leftPos+8,topPos+20,200,188);researchPanel.initWidgets();
        converterPanel=new PrimalConverterPanel(menu,minecraft,font,w->addRenderableWidget(w),leftPos+8,topPos+20,200,100);converterPanel.initWidgets();
        refresh();if(passive()&&main())setInitialFocus(search);
    }
    /** Native recipe viewers return this same screen; retain its exact focus, filters and scroll at the same size. */
    @Override protected void repositionElements(){if(layoutWidth!=width||layoutHeight!=height)super.repositionElements();}
    private DarkButton button(Component name,Component hint,int x,int y,int width,Button.OnPress action){return addRenderableWidget(DarkButton.labelIcon(UiIcon.START,name,hint,leftPos+x,topPos+y,width,18,action));}
    private DarkButton tab(UiIcon icon,Component name,int row,MachineMenu.View view){return addRenderableWidget(DarkButton.icon(icon,name,ui(switch(view){case MAIN->"tab_machine_hint";case MACHINES->"tab_machines_hint";case RESEARCH->"tab_research_hint";case CONFIGURATION->"tab_settings_hint";}),leftPos-22,topPos+18+row*24,24,22,b->setView(view)));}
    public void setView(MachineMenu.View view){
        if(observing&&view!=MachineMenu.View.MACHINES){send(MachineMenu.CLOSE_TERMINAL_DEVICES);observing=false;}
        if(view==MachineMenu.View.MACHINES&&!observing&&terminal()) {menu.resetTerminalDevices();send(MachineMenu.OPEN_TERMINAL_DEVICES);observing=true;}
        menu.setView(view);search.setValue("");stockScroll.setOffset(0);deviceScroll.setOffset(0);refresh();
    }
    public List<Rect2i> viewerExclusionAreas(){var result=new ArrayList<Rect2i>();for(var tab:List.of(machineTab,devicesTab,researchTab,settingsTab))if(tab!=null&&tab.visible)result.add(new Rect2i(tab.getX(),tab.getY(),tab.getWidth(),tab.getHeight()));return result;}
    @Override protected boolean hasClickedOutside(double mx,double my,int x,int y){for(var r:viewerExclusionAreas())if(mx>=r.getX()&&mx<r.getX()+r.getWidth()&&my>=r.getY()&&my<r.getY()+r.getHeight())return false;return super.hasClickedOutside(mx,my,x,y);}
    private static Component lines(List<Component> values){var text=Component.empty();for(var value:values){if(!text.getSiblings().isEmpty())text.append("\n");text.append(value);}return text;}
    private boolean validSelection(){var s=menu.snapshot();return s.selected()>=0&&!s.preview().id().isEmpty()&&s.selectedId().equals(s.preview().id())&&!s.status().equals("selection_unavailable");}
    private void send(int action){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private void deviceAction(TerminalDevicesPayload.Device device,TerminalDeviceActionPayload.Action action){if(device!=null)ClientPacketDistributor.sendToServer(new TerminalDeviceActionPayload(menu.containerId,device.position(),action,action==TerminalDeviceActionPayload.Action.QUEUE_BATCH?device.recipeId():""));}
    private List<MachineSnapshot.Entry> entries(){
        var stock=menu.stock();var catalogue=ClientChemistry.current();var language=net.minecraft.locale.Language.getInstance();
        var filter=new StockFilter(search.getValue(),phaseFilter,favoritesOnly);int revision=StockFavorites.get().revision();
        if(stock==shownStock&&catalogue==shownCatalogue&&filter.equals(shownFilter)&&revision==shownFavorites&&language==shownLanguage)return shownEntries;
        var query=filter.query(player()==null?Set.of():StockFavorites.get().of(player()),catalogue.substances(),d->SubstanceNames.name(d).getString(),SubstanceLabels::iconLabel);
        if(!query.equals(sentQuery)){sentQuery=query;ClientPacketDistributor.sendToServer(new StockQueryPayload(menu.containerId,query));}
        shownEntries=filter.apply(stock.entries(),this::favorite,e->name(e).getString(),SubstanceLabels::iconLabel);
        shownStock=stock;shownCatalogue=catalogue;shownFilter=filter;shownFavorites=revision;shownLanguage=language;return shownEntries;
    }
    private void refresh(){
        if(search==null)return;var s=menu.snapshot();boolean ready=s.containerId()==menu.containerId;
        for(var control:List.of(machineTab,devicesTab,researchTab,settingsTab,choose,primary,repeat,pause,buildHint,back))control.setAccent(machineAccent(menu.kind()));

        machineTab.setMessage(ui(terminal()?"tab_storage":passive()?"tab_contents":"tab_machine"));
        machineTab.setIcon(passive()?UiIcon.STOCK:UiIcon.MACHINE);
        machineTab.setTooltip(Tooltip.create(machineTab.getMessage().copy().append("\n").append(ui(terminal()?"tab_storage_hint":passive()?"tab_contents_hint":"tab_machine_hint"))));
        machineTab.setSelected(main());devicesTab.setSelected(machines());settingsTab.setSelected(configuration());
        devicesTab.visible=terminal();researchTab.visible=menu.kind()==MachineKind.ANALYZER;researchTab.setSelected(researching());settingsTab.setY(topPos+18+(terminal()||menu.kind()==MachineKind.ANALYZER?2:1)*24);
        back.visible=ready&&menu.remote();
        boolean list=ready&&(storageList()||machines());search.visible=search.active=list;
        search.setWidth(machines()?154:82);search.setHint(tr("ui.search_short").copy().withStyle(style->style.withColor(MUTED&0xffffff)));search.setSuggestion(null);if(!list)search.setFocused(false);
        for(int i=0;i<filters.size();i++){var b=filters.get(i);b.visible=b.active=ready&&storageList();b.setSelected(i==0?favoritesOnly:phaseFilter.contains(new String[]{"","solid","liquid","gas"}[i]));}
        settingsPanel.setVisible(ready&&configuration()&&!driveRecovery());if(configuration()&&!driveRecovery())settingsPanel.tick();
        researchPanel.setVisible(ready&&researching());if(researching())researchPanel.tick();
        converterPanel.setVisible(ready&&main()&&converter());if(main()&&converter())converterPanel.tick();
        var decision=MachinePrimaryAction.forSnapshot(s);boolean process=ready&&main()&&!passive();
        choose.visible=process&&!converter()&&!menu.kind().generator();choose.active=process&&!s.networkJobs()&&(selecting()&&s.choiceCount()>0||menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR);
        choose.setIcon(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR&&extraFlag("assay")?UiIcon.ANALYSE:UiIcon.RECIPE);
        choose.setMessage(previewTitle(s));choose.setTooltip(Tooltip.create(lines(recipeDetails(s))));
        if(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR)choose.setMessage(ui(extraFlag("assay")?"assay":"preparation"));
        primary.visible=process&&!converter()&&decision.visible();primary.active=decision.enabled();primary.setMessage(tr(decision.labelKey()));
        primary.setIcon(switch(decision.action()) {
            case CHOOSE_RECIPE -> UiIcon.RECIPE; case MAKE_ONCE -> UiIcon.MAKE_ONE;
            case START -> menu.kind()==MachineKind.ANALYZER?UiIcon.ANALYSE:UiIcon.START;
            case PAUSE -> UiIcon.PAUSE; case RESUME -> UiIcon.RESUME;
            case CRANK -> UiIcon.CRANK; case LOCAL_CONTROL -> UiIcon.MANUAL; case NONE -> UiIcon.START;
        });
        if(decision.opensRecipeSelection())primary.setMessage(ui("choose"));
        if(decision.visible())primary.setTooltip(Tooltip.create(tr(decision.labelKey()).copy().append("\n").append(tr(decision.hintKey()))));
        boolean selectable=process&&selecting()&&!s.networkJobs();
        boolean valid=s.selected()>=0&&!s.preview().id().isEmpty()&&s.selectedId().equals(s.preview().id())&&!s.status().equals("selection_unavailable");
        repeat.visible=selectable;repeat.active=valid;repeat.setMessage(ui("loop"));repeat.setIcon(s.repeat()?UiIcon.LOOP:UiIcon.LOOP_OFF);repeat.setTooltip(Tooltip.create(ui(s.repeat()?"repeat_on":"repeat_off").copy().append("\n").append(tr("continuous_hint"))));repeat.setSelected(s.repeat());
        pause.visible=selectable&&(s.repeat()||s.queuedBatches()>0);pause.active=pause.visible;
        pause.setMessage(tr(s.running()?"pause":"resume"));pause.setIcon(s.running()?UiIcon.PAUSE:UiIcon.RESUME);pause.setTooltip(Tooltip.create(pause.getMessage().copy().append("\n").append(tr("pause_hint"))));pause.setSelected(!s.running());
        if(selectable&&valid){primary.setMessage(ui(s.queuedBatches()>0?"add_one":"make_one"));primary.setIcon(s.queuedBatches()>0?UiIcon.QUEUE_ADD:UiIcon.MAKE_ONE);primary.active=!s.repeat()&&s.queuedBatches()<64;primary.setTooltip(Tooltip.create(primary.getMessage().copy().append("\n").append(tr(s.queuedBatches()>0?"add_batch_hint":"start_once_hint"))));}
        if(menu.kind().generator()){primary.visible=main();primary.active=main();primary.setMessage(tr(s.running()?"pause":"start"));primary.setIcon(s.running()?UiIcon.PAUSE:UiIcon.START);}
        if(s.status().equals("requires_research")||s.status().equals("requires_field"))primary.active=repeat.active=false;
        primary.setWidth(s.networkJobs()||handPreparator()||menu.kind().generator()?138:58);
        buildHint.setIcon(UiIcon.LOCATE);
        buildHint.visible=process&&industrial()&&menu.structureHint().present();buildHint.active=buildHint.visible;
        if(buildHint.visible)primary.visible=repeat.visible=pause.visible=false;
        for(var row:stockRows)row.visible=row.active=false;
        stockScroll.setVisibleRows(stockCount());
        if(ready&&storageList()){
            var values=entries();stockScroll.setSize(values.size());
            for(int i=0;i<stockCount();i++){var row=stockRows.get(i);int index=stockScroll.offset()+i;row.show(index<values.size()?values.get(index):null);row.setX(leftPos+stockX());row.setY(topPos+stockY()+18*i);row.setWidth(stockWidth());row.visible=row.active=row.entry!=null;}
            stockBar.setBounds(leftPos+183,topPos+stockY(),5,stockCount()*18);
        }
        if(ready&&machines()){
            String query=search.getValue().strip().toLowerCase(Locale.ROOT);
            shownDevices=menu.terminalDevices().devices().stream().filter(d->(Component.translatable("block.rematter."+d.kind().id()).getString()+" "+SubstanceNames.choice(d.kind(),d.recipeId()).getString()+" "+d.position().toShortString()).toLowerCase(Locale.ROOT).contains(query)).toList();
            deviceScroll.setSize(shownDevices.size());deviceBar.setBounds(leftPos+183,topPos+42,5,150);
        }
        for(int i=0;i<deviceRows.size();i++){var row=deviceRows.get(i);int index=deviceScroll.offset()+i;row.show(ready&&machines()&&index<shownDevices.size()?shownDevices.get(index):null,topPos+42+i*30);}
    }
    @Override protected void containerTick(){super.containerTick();refresh();}
    @Override public void onClose(){if(observing)send(MachineMenu.CLOSE_TERMINAL_DEVICES);super.onClose();}
    @Override public boolean keyPressed(KeyEvent event){
        if(researching()&&researchPanel.handlesKey(event))return true;
        if(main()&&converter()&&converterPanel.handlesKey(event))return true;
        if(configuration()&&settingsPanel.handlesKey(event))return true;
        if(search!=null&&search.visible&&search.isFocused()&&event.key()!=GLFW.GLFW_KEY_ESCAPE&&event.key()!=GLFW.GLFW_KEY_TAB){
            var model=machines()?deviceScroll:stockScroll;
            if(event.key()==GLFW.GLFW_KEY_UP||event.key()==GLFW.GLFW_KEY_DOWN||event.key()==GLFW.GLFW_KEY_PAGE_UP||event.key()==GLFW.GLFW_KEY_PAGE_DOWN){model.key(event.key());refresh();return true;}
            search.keyPressed(event);return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(researching()&&researchPanel.mouseScrolled(x,y,sx,sy))return true;if(main()&&converter()&&converterPanel.mouseScrolled(x,y,sx,sy))return true;if((storageList()||machines())&&inside(x-leftPos,y-topPos,8,40,180,terminal()?154:72)){(machines()?deviceScroll:stockScroll).wheel(sy);refresh();return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(event.button()==1){var hovered=substanceAt(event.x(),event.y());if(hovered.isPresent()){SubstanceDetails.open(hovered.get(),accent());return true;}}if(researching()&&researchPanel.mouseClicked(event,doubleClick))return true;var bar=machines()?deviceBar:stockBar;if((machines()||storageList())&&bar.mouseClicked(event.x(),event.y(),event.button())){refresh();return true;}return super.mouseClicked(event,doubleClick);}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(researching()&&researchPanel.mouseDragged(event,dx,dy))return true;var bar=machines()?deviceBar:stockBar;if((machines()||storageList())&&bar.mouseDragged(event.x(),event.y())){refresh();return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(researching()&&researchPanel.mouseReleased(event))return true;if(stockBar.mouseReleased()||deviceBar.mouseReleased())return true;return super.mouseReleased(event);}

    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractBackground(g,mx,my,partial);
        ScreenChrome.panel(g,leftPos,topPos,W,H);
        g.fill(leftPos+8,topPos+17,leftPos+188,topPos+18,BORDER);g.fill(leftPos+8,topPos+17,leftPos+56,topPos+18,machineAccent(menu.kind()));
        if(search!=null&&search.visible)ScreenChrome.inset(g,leftPos+8,topPos+20,machines()?180:104,18);
        if(machines())for(var row:deviceRows)if(row.device!=null)ScreenChrome.inset(g,leftPos+8,row.y,174,28);
        if(main()&&!passive()&&!converter()&&!menu.kind().generator()){
            if(handPreparator())ScreenChrome.icon(g,UiIcon.CRANK,leftPos+6,topPos+40,MUTED);
            else{ScreenChrome.inset(g,leftPos+8,topPos+20,12,58);int fill=(int)(54L*menu.progress().energy()/MachineBlockEntity.ENERGY_CAPACITY);g.fill(leftPos+10,topPos+76-fill,leftPos+18,topPos+76,menu.progress().energy()==0?WARNING:accent());}
            arrow(g,leftPos+94,topPos+52);
        }
        for(int i=0;i<menu.slots.size();i++){
            var slot=menu.slots.get(i);if(!slot.isActive())continue;
            int edge=i>=MachineMenu.FIRST_PLAYER_SLOT?SLOT_EDGE:i==2||i==10||i==11||i==13?UPGRADE:i==12?PRIMAL:i==1?accent():SLOT_EDGE;
            ScreenChrome.slot(g,leftPos+slot.x-1,topPos+slot.y-1);
            g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y,edge);
            if(i<MachineMenu.FIRST_PLAYER_SLOT&&!slot.hasItem()) {
                var ghost=slotGhost(i);
                if(ghost!=null)ScreenChrome.icon(g,ghost,leftPos+slot.x,topPos+slot.y,
                        i==12?PRIMAL_GHOST:i==2&&ghost==UiIcon.SLOT_PRESSURE?PRESSURE_GHOST:GHOST);
            }
        }
    }
    private UiIcon slotGhost(int slot) {
        if(disk())return UiIcon.SLOT_CELL;
        return switch(slot) {
            case 0 -> menu.kind()==MachineKind.ANALYZER?UiIcon.SLOT_SAMPLE:menu.kind().generator()?UiIcon.SLOT_FUEL:converter()?UiIcon.SLOT_SHARD:UiIcon.SLOT_INPUT;
            case 1 -> UiIcon.SLOT_OUTPUT;
            case 2 -> industrial()?UiIcon.SLOT_SPEED:menu.snapshot().status().equals("requires_pressure")?UiIcon.SLOT_PRESSURE:UiIcon.SLOT_UPGRADE;
            case 3 -> UiIcon.SLOT_MIX; case 4 -> UiIcon.SLOT_ADDITIVE;
            case 10 -> UiIcon.SLOT_EFFICIENCY; case 11 -> UiIcon.SLOT_PARALLEL;
            case 12 -> UiIcon.SLOT_PRIMAL_CELL; case 13 -> UiIcon.SLOT_LENS;
            default -> null;
        };
    }
    private static final Identifier PROGRESS_ARROW=Identifier.fromNamespaceAndPath("rematter","progress_arrow");
    private void arrow(GuiGraphicsExtractor g,int x,int y){
        var p=menu.progress();int fill=p.duration()>0?(int)Math.clamp(24L*p.progress()/p.duration(),0,24):0;
        g.blitSprite(RenderPipelines.GUI_TEXTURED,PROGRESS_ARROW,x,y,24,16,HIGHLIGHT);
        if(fill>0)g.blitSprite(RenderPipelines.GUI_TEXTURED,PROGRESS_ARROW,24,16,0,0,x,y,fill,16,machineAccent(menu.kind()));
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        text(g,title.getString(),8,6,back.visible?155:200,TEXT);
        if(configuration()){
            if(driveRecovery()) {ScreenChrome.icon(g,UiIcon.WARNING,8,20,WARNING);g.text(font,ui("preserved_items"),24,24,WARNING,false);int line=0;for(var part:font.split(ui("preserved_items_hint"),154)){if(line==4)break;g.text(font,part,8,76+line++*10,MUTED,false);}return;}
            ScreenChrome.inset(g,8,130,180,78);ScreenChrome.icon(g,UiIcon.INFO,11,132,MUTED);g.text(font,ui("details"),29,136,MUTED,false);
            var details=industrial()?structureDetails(menu.snapshot()):storageDetails(menu.snapshot());
            // This is an overview; the existing panel hover exposes every detail. Keep each shown entry whole
            // and at the normal font size instead of shrinking a long route explanation into a thin line.
            int row=0;
            for(var detail:details){
                var wrapped=font.split(detail,170);if(wrapped.size()>6-row)continue;
                for(var line:wrapped)g.text(font,line,13,147+row++*10,TEXT,false);
            }
            return;
        }
        if(researching())return;
        if(!terminal())g.text(font,tr("inventory"),18,123,MUTED,false);
        if(machines()){drawDevices(g);return;}
        if(!main())return;
        if(passive()){drawStorage(g);return;}
        if(converter())return;
        if(menu.kind().generator()){
            ScreenChrome.icon(g,UiIcon.ENERGY,22,62,accent());text(g,ui("generator_rate",extraNumber("generationRate")).getString(),38,66,150,accent());
            text(g,Quantities.energy(Quantities.compact(menu.progress().energy())).getString(),38,78,150,TEXT);
            ScreenChrome.icon(g,UiIcon.FUEL,22,86,MUTED);text(g,ui("generator_remaining",extraNumber("fuelRemaining")).getString(),38,90,150,MUTED);return;
        }
        var s=menu.snapshot();var p=s.preview();
        if(!menu.inputRole())chips(g,p.inputs(),24,42);
        if(!menu.slots.get(1).isActive())chips(g,p.outputs(),134,42);
        if(menu.kind()==MachineKind.ANALYZER)text(g,tr("sample_kept").getString(),24,66,90,MUTED);
        ItemStack preview=outputIcon(p);
        if(!preview.isEmpty()){
            ScreenChrome.inset(g,170,42,18,18);g.fakeItem(preview,171,43);ScreenChrome.icon(g,UiIcon.PREVIEW,171,61,MUTED);
        }
        ScreenChrome.icon(g,handPreparator()?UiIcon.CRANK:UiIcon.ENERGY,22,76,MUTED);
        String energy=handPreparator()?tr("hand_powered").getString():Quantities.energy(Quantities.compact(p.id().isEmpty()?menu.progress().energy():p.energy())).getString();
        text(g,energy,38,80,144,MUTED);
        if(!handPreparator()&&!p.id().isEmpty()) {
            int xx=40+font.width(energy);
            ScreenChrome.icon(g,UiIcon.TIME,xx,76,MUTED);text(g,Quantities.seconds(p.ticks()),xx+16,80,188-xx-16,MUTED);
        }
        String status=statusText().getString();
        if(s.queuedBatches()>0)status+=" · "+s.queuedBatches()+" "+ui("queued").getString();else if(s.repeat())status+=" · "+ui("repeating").getString();
        int color=working(s.status())?accent():List.of("ready","idle","analyzed","analysis_complete","awaiting_request","paused").contains(s.status())?MUTED:WARNING;
        ScreenChrome.icon(g,StatusText.icon(s.status(),menu.kind()),22,88,-1);text(g,status,38,92,150,color);
    }
    private void chips(GuiGraphicsExtractor g,List<MachineSnapshot.Amount> values,int x,int y){
        for(int i=0;i<Math.min(6,values.size());i++){var a=values.get(i);int xx=x+i%3*18,yy=y+i/3*18;ScreenChrome.inset(g,xx,yy,18,18);substanceIcon(g,key(a.id(),a.phase()),xx+1,yy+1);if(a.available()<a.required())g.fill(xx,yy,xx+18,yy+1,WARNING);}
    }
    private void drawStorage(GuiGraphicsExtractor g){
        var s=menu.snapshot();
        if(disk()){
            for(int bay=0;bay<s.cells().size();bay++){var cell=s.cells().get(bay);var slot=menu.slots.get(menu.diskMenuSlot(bay));g.text(font,Integer.toString(bay+1),slot.x-8,slot.y+5,MUTED,false);int fill=cell.capacity()>0?(int)Math.clamp(16L*cell.used()/cell.capacity(),0,16):0;g.fill(slot.x,slot.y+17,slot.x+16,slot.y+19,HIGHLIGHT);g.fill(slot.x,slot.y+17,slot.x+fill,slot.y+19,cell.readable()?accent():WARNING);}
            if(extraNumber("primalCapacity")>0){ScreenChrome.icon(g,UiIcon.PRIMAL,6,96,PRIMAL);text(g,Quantities.compact(extraNumber("primal")),22,100,53,PRIMAL_LIGHT);};
            text(g,Quantities.compact(s.used())+" / "+Quantities.compact(s.capacity()),8,110,67,MUTED);
        }
        if(terminal()){text(g,ui("substance").getString(),8,40,105,MUTED);text(g,ui("amount").getString(),126,40,48,MUTED);}
        if(shownEntries.isEmpty()){
            if(!menu.stock().readable())ScreenChrome.icon(g,UiIcon.WARNING,stockX(),stockY()+1,WARNING);
            text(g,tr(menu.stock().readable()?"contents_empty":"contents_unavailable").getString(),stockX()+(menu.stock().readable()?2:18),stockY()+5,stockWidth()-(menu.stock().readable()?4:20),MUTED);
        }
        String footer=driveRecovery()?ui("extra_items").getString():ui("types",menu.stock().totalTypes()).getString();
        if(menu.stock().truncated())footer+=" · "+ui("partial").getString();
        if(terminal())footer+=" · "+Quantities.compact(s.used())+" / "+Quantities.compact(s.capacity())+" u";
        ScreenChrome.icon(g,menu.stock().truncated()?UiIcon.WARNING:UiIcon.STOCK,stockX()-2,(terminal()?200:112)-4,menu.stock().truncated()?WARNING:MUTED);text(g,footer,stockX()+14,terminal()?200:112,166-stockX(),menu.stock().truncated()?WARNING:MUTED);

    }
    private void drawDevices(GuiGraphicsExtractor g){
        if(shownDevices.isEmpty())text(g,ui("no_machines").getString(),8,48,175,MUTED);
        for(var row:deviceRows)row.draw(g);
        ScreenChrome.icon(g,menu.snapshot().network().status().equals("ready")?UiIcon.NETWORK_ONLINE:UiIcon.NETWORK_OFFLINE,6,196,-1);text(g,ui("devices_count",shownDevices.size(),menu.terminalDevices().total()).getString(),22,200,164,MUTED);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractRenderState(g,mx,my,partial);int x=mx-leftPos,y=my-topPos;
        if(researching()){researchPanel.extractRenderState(g,mx,my);return;}
        if(main()&&converter())converterPanel.extractRenderState(g,mx,my);
        if(configuration()){settingsPanel.extractRenderState(g,mx,my);if(inside(x,y,8,130,180,78))tooltip(g,industrial()?structureDetails(menu.snapshot()):storageDetails(menu.snapshot()),mx,my);return;}
        if(search!=null&&search.visible)ScreenChrome.icon(g,UiIcon.SEARCH,leftPos+9,topPos+21,MUTED);
        if(storageList())stockBar.extractRenderState(g);else if(machines())deviceBar.extractRenderState(g);
        if(main()&&!passive()&&!converter()&&!menu.kind().generator()){
            if(inside(x,y,8,20,12,58)){var lines=new ArrayList<Component>();lines.add(energyLine());addRouteDetails(lines,menu.snapshot());tooltip(g,lines,mx,my);}
            if(inside(x,y,94,52,24,16))tooltip(g,recipeDetails(menu.snapshot()),mx,my);
            if(inside(x,y,24,90,164,11)){var lines=new ArrayList<Component>();lines.add(statusText());lines.addAll(industrial()?structureDetails(menu.snapshot()):storageDetails(menu.snapshot()));tooltip(g,lines,mx,my);}
            if(inside(x,y,170,42,18,34)&&!outputIcon(menu.snapshot().preview()).isEmpty())tooltip(g,List.of(tr("result_preview_detail",menu.snapshot().preview().outputCount(),outputIcon(menu.snapshot().preview()).getHoverName()),tr("result_preview_hint")),mx,my);
            tooltipChips(g,menu.snapshot().preview().inputs(),24,42,x,y,mx,my);
            if(!menu.slots.get(1).isActive())tooltipChips(g,menu.snapshot().preview().outputs(),134,42,x,y,mx,my);
        }
        if(main())for(int i=0;i<MachineMenu.FIRST_PLAYER_SLOT;i++){var slot=menu.slots.get(i);if(!slot.isActive()||slot.hasItem()||!inside(x,y,slot.x,slot.y,16,16))continue;
            List<Component> lines=i>=10?List.of(ui(i==10?"efficiency":i==11?"parallel":i==12?"primal_cell":"discovery_lens")):i==2?upgradeDetails():List.of(tr(i==1?"output":i==3?"mix_slot":i==4?"additive_slot":disk()?"disk_slot":menu.kind()==MachineKind.ANALYZER?"sample":menu.kind().generator()?"fuel_slot":converter()?"primal_source_slot":"input"));tooltip(g,lines,mx,my);}
        if(main()&&disk()&&inside(x,y,8,103,68,15))tooltip(g,storageDetails(menu.snapshot()),mx,my);
    }
    private boolean extraFlag(String key){return menu.extras().has(key)&&menu.extras().get(key).getAsBoolean();}
    private long extraNumber(String key){return menu.extras().has(key)?menu.extras().get(key).getAsLong():0;}
    private void action(MachineActionPayload.Action action,String target,long amount){ClientPacketDistributor.sendToServer(new MachineActionPayload(menu.containerId,action,target,amount));}
    private void tooltipChips(GuiGraphicsExtractor g,List<MachineSnapshot.Amount> values,int xx,int yy,int x,int y,int mx,int my){for(int i=0;i<Math.min(6,values.size());i++)if(inside(x,y,xx+i%3*18,yy+i/3*18,18,18)){var a=values.get(i);var key=key(a.id(),a.phase());var lines=key==null?new ArrayList<Component>():new ArrayList<>(SubstanceDetails.tooltip(new SubstanceDetails.Hover(key,a.required()),true));lines.add(tr("preview_input",name(a),SubstanceNames.phase(a.phase()),number(a.required()),number(a.available())));tooltip(g,lines,mx,my);}}
    @Override public Optional<SubstanceDetails.Hover> substanceAt(double x,double y){
        if(storageList())for(var row:stockRows)if(row.visible&&row.entry!=null&&inside(x,y,row.getX(),row.getY(),row.getWidth(),row.getHeight())){var key=key(row.entry.id(),row.entry.phase());if(key!=null)return Optional.of(new SubstanceDetails.Hover(key,row.entry.amount()));}
        if(main()&&!passive()&&!converter()&&!menu.kind().generator()){
            var input=chipAt(menu.snapshot().preview().inputs(),24,42,x-leftPos,y-topPos);if(input.isPresent())return input;
            if(!menu.slots.get(1).isActive())return chipAt(menu.snapshot().preview().outputs(),134,42,x-leftPos,y-topPos);
        }
        return Optional.empty();
    }
    private static Optional<SubstanceDetails.Hover> chipAt(List<MachineSnapshot.Amount> values,int left,int top,double x,double y){for(int i=0;i<Math.min(6,values.size());i++)if(inside(x,y,left+i%3*18,top+i/3*18,18,18)){var a=values.get(i);var key=key(a.id(),a.phase());if(key!=null)return Optional.of(new SubstanceDetails.Hover(key,a.required()));}return Optional.empty();}
    @Override public Rect2i substancePanelBounds(){return new Rect2i(leftPos,topPos,imageWidth,imageHeight);}
    @Override public int substanceAccent(){return accent();}
    private List<Component> upgradeDetails(){var lines=new ArrayList<Component>();lines.add(industrial()?ui("speed"):tr("upgrade_hint"));lines.add(tr("upgrade_limit"));lines.add(tr("speed_upgrade_hint"));lines.add(tr("efficiency_upgrade_hint"));if(menu.kind().synthesis())lines.add(tr("pressure_upgrade_hint"));if(industrial())lines.add(tr("parallel_upgrade_hint"));return lines;}
    private Component statusText(){var s=menu.snapshot();return s.status().equals("structure")&&industrial()?Component.translatable("status.rematter."+s.structure().diagnostic()):StatusText.of(s.status(),s.orders().stale(),s.orders().quarantined());}
    private static Component name(MachineSnapshot.Entry e){return SubstanceNames.substance(e.id(),e.phase(),e.known());}
    private static Component name(MachineSnapshot.Amount e){return SubstanceNames.substance(e.id(),e.phase(),e.known());}
    private static String label(MachineSnapshot.Amount a){return a.known()?SubstanceLabels.shortLabel(a.id(),name(a).getString()):name(a).getString();}
    private static boolean inside(double x,double y,int xx,int yy,int w,int h){return x>=xx&&x<xx+w&&y>=yy&&y<yy+h;}
    private void tooltip(GuiGraphicsExtractor g,List<Component> lines,int x,int y){if(!lines.isEmpty())g.setComponentTooltipForNextFrame(font,lines,x,y);}
    private List<Component> recipeDetails(MachineSnapshot s) {
        var p=s.preview();var lines=new ArrayList<Component>();lines.add(previewTitle(s));
        if(menu.inputRole()&&!preparator()&&s.sampleBatchSize()>1)lines.add(Component.translatable("screen.rematter.composition_batch",s.sampleBatchSize()));
        lines.add(handPreparator()?Component.translatable("screen.rematter.crank_count",(p.ticks()+dev.latrox.rematter.content.preparation.PreparationWork.CRANK_WORK-1)/dev.latrox.rematter.content.preparation.PreparationWork.CRANK_WORK)
                :Component.translatable("screen.rematter.preview_cost",number(p.energy()),Quantities.seconds(p.ticks())));
        for(var a:p.inputs())lines.add(Component.translatable("screen.rematter.preview_input",label(a),SubstanceNames.phase(a.phase()),number(a.required()),number(a.available())));
        for(var a:p.outputs())lines.add(Component.translatable("screen.rematter.preview_output",label(a),SubstanceNames.phase(a.phase()),number(a.required())));
        if(p.inputs().stream().anyMatch(a->!a.known())||p.outputs().stream().anyMatch(a->!a.known()))lines.add(tr("unknown_substance_hint"));
        if(!p.complete())lines.add(tr("preview_partial"));return lines;
    }
    /** Every title is resolved from IDs in the player's own language. */
    private Component previewTitle(MachineSnapshot s) {
        var p = s.preview();
        if (p.id().isEmpty()) return s.selectedId().isEmpty() ? tr(selecting() ? "choose_recipe" : "add_input") : SubstanceNames.choice(menu.kind(), s.selectedId());
        if (s.networkJobs()) return orderTitle(p);
        if (p.id().startsWith(MachineSnapshot.ITEM_SOURCE)) return SubstanceNames.item(p.id().substring(MachineSnapshot.ITEM_SOURCE.length()));
        if (preparator()) return SubstanceNames.item(p.outputId());
        if (!selecting() && p.id().startsWith(MachineChoices.FLUID_PREFIX)) return SubstanceNames.fluid(p.id().substring(MachineChoices.FLUID_PREFIX.length()));
        return SubstanceNames.choice(menu.kind(), p.id());
    }
    private Component orderTitle(MachineSnapshot.Preview p) {
        var title = Component.empty();
        for (var resource : p.resources()) {
            if (!title.getSiblings().isEmpty()) title.append(" + ");
            title.append(Component.translatable("screen.rematter.order_amount", number(resource.amount()),
                    resource.kind().equals("fluid") ? SubstanceNames.fluid(resource.id()) : SubstanceNames.item(resource.id())));
        }
        if (!p.resources().isEmpty()) title.append(" → ");
        Component output = switch (p.outputKind()) {
            case "item" -> Component.translatable("screen.rematter.order_amount", number(p.outputCount()), SubstanceNames.item(p.outputId()));
            case "fluid" -> Component.translatable("screen.rematter.order_amount", number(p.outputCount()), SubstanceNames.fluid(p.outputId()));
            default -> p.outputs().isEmpty() ? Component.literal(p.outputId()) : name(p.outputs().getFirst());
        };
        return title.append(output);
    }
    private List<Component> structureDetails(MachineSnapshot s) {
        var lines=new ArrayList<Component>();if(!industrial())return lines;var v=s.structure();
        if(v.width()>0)lines.add(Component.translatable("screen.rematter.structure_size",v.width(),v.depth(),v.height()));
        if(!v.formed()){lines.add(Component.translatable("status.rematter."+v.diagnostic()));lines.add(Component.translatable("screen.rematter.help_"+v.diagnostic()));lines.add(tr(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR?"preparation_stack":"reactor_stack"));}
        else {
            lines.add(Component.translatable("screen.rematter.structure_speed",v.workRate()));
            if(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR)lines.add(tr("structure_precision",v.separation()));
            else {lines.add(tr("structure_field",v.coils()));lines.add(tr("structure_room",number(16_000L*v.chambers())));}
            addRouteDetails(lines,s);
            if(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR) {
                moduleDetail(lines,"grinding_unit",v.grinding(),"module_capacity");
                moduleDetail(lines,"mixing_basin",v.mixing(),"module_capacity");
                moduleDetail(lines,"separation_unit",v.separation(),"module_precision");
            } else {
                moduleDetail(lines,"reaction_chamber",v.chambers(),"module_room");
                moduleDetail(lines,"field_coil",v.coils(),"module_field");
            }
            lines.add(tr("structure_scaling_hint"));
        }return lines;
    }
    private static void moduleDetail(List<Component> lines,String id,int count,String benefit) {
        lines.add(Component.translatable("screen.rematter.module_detail",count,Component.translatable("block.rematter."+id),tr(benefit)));
    }
    private void addRouteDetails(List<Component> lines,MachineSnapshot s){
        if(s.network().status().equals("ready")&&menu.extras().has("matterRate")){
            long rate=extraNumber("matterRate");
            String tier=rate>=8192?"Resonant":rate>=1024?"Reinforced":"Basic";
            lines.add(tr("matter_route",tier,number(rate)));
            lines.add(tr("matter_route_hint"));
        }
    }
    private List<Component> storageDetails(MachineSnapshot s) {
        var lines=new ArrayList<Component>();
        if(handPreparator()){lines.add(tr("hand_powered"));lines.add(tr("crank_hint"));return lines;}
        if(menu.kind()==MachineKind.NETWORK_INTERFACE)lines.add(statusText());
        if(disk()&&extraNumber("primalCapacity")>0)lines.add(ui("primal_reserve",number(extraNumber("primal")),number(extraNumber("primalCapacity"))));
        lines.add(tr("source_"+s.storageSource()));lines.add(capacity(number(s.used()),number(s.capacity())));
        if(menu.kind().processes())lines.add(energyLine());
        if(disk()) {
            lines.add(Component.translatable("screen.rematter.drive_cells",s.cells().stream().filter(MachineSnapshot.CellView::installed).count()));
            if(s.cells().stream().anyMatch(c->c.installed()&&!c.readable()))lines.add(tr("drive_blocked"));
            for(int bay=0;bay<s.cells().size();bay++) {
                var cell=s.cells().get(bay);if(!cell.installed())continue;
                lines.add(Component.translatable("screen.rematter.drive_bay_detail",Component.translatable("screen.rematter.drive_bay",bay+1),SubstanceNames.item(cell.itemId()),
                        cell.readable()?Component.translatable("screen.rematter.cell_types",cell.types(),cell.maxTypes()):tr("cell_unreadable")));
            }
        }
        addRouteDetails(lines,s);
        var n=s.network();
        if(!n.status().equals("none") && (configuration() || !disk())) {
            lines.add(Component.translatable("screen.rematter.network_summary",n.nodes(),n.storages(),n.cells()));
            if(n.status().equals("ready") && n.storages()>1 && !s.storageSource().equals("network"))lines.add(Component.translatable("screen.rematter.network_capacity",number(n.used()),number(n.capacity()),n.types()));
            else if(!n.status().equals("ready"))lines.add(Component.translatable("status.rematter."+n.status()));
        }
        if(s.fluidAmount()>0)lines.add(Component.translatable("screen.rematter.fluid_amount",SubstanceNames.fluid(s.fluidId()),number(s.fluidAmount())));
        var stock=menu.stock();
        if(stock.truncated()){lines.add(Component.translatable("screen.rematter.partial_contents_hint",stock.entries().size(),stock.totalTypes()));lines.add(tr("truncated_hint"));}
        else if(!stock.readable())lines.add(tr("contents_unavailable"));
        return lines;
    }
    private static Component stockTooltip(MachineSnapshot.Entry e, String label) {
        var key=key(e.id(),e.phase());
        return key==null?Component.literal(label+"\n"+e.id()):SubstanceDetails.joined(SubstanceDetails.tooltip(new SubstanceDetails.Hover(key,e.amount()),true));
    }
    private static SubstanceKey key(String id,String phase) {
        try { return new SubstanceKey(SubstanceId.parse(id),Phase.valueOf(phase.toUpperCase(Locale.ROOT))); }
        catch(IllegalArgumentException invalid) { return null; }
    }
    private static void substanceIcon(GuiGraphicsExtractor g,SubstanceKey key,int x,int y) {
        if(key!=null)SubstanceIcon.draw(g,x,y,key);
        else ScreenChrome.icon(g,UiIcon.UNKNOWN,x,y,MUTED);
    }
    private static int phaseColor(String phase) {return switch(phase){case "liquid"->LIQUID;case "gas"->GAS;default->WARNING;};}
    private void text(GuiGraphicsExtractor g,String v,int x,int y,int width,int color){ScreenChrome.text(g,font,v,x,y,width,color);}
    private ItemStack outputIcon(MachineSnapshot.Preview p){try{Identifier id=Identifier.parse(p.outputId());if(p.outputKind().equals("item")&&BuiltInRegistries.ITEM.containsKey(id))return new ItemStack(BuiltInRegistries.ITEM.getValue(id));if(p.outputKind().equals("fluid")&&BuiltInRegistries.FLUID.containsKey(id))return new ItemStack(BuiltInRegistries.FLUID.getValue(id).getBucket());}catch(RuntimeException ignored){}return ItemStack.EMPTY;}
    private static String number(long v){return Quantities.number(v);}
    private static String matter(long v){return Quantities.matter(Quantities.compact(v)).getString();}
    private static Component capacity(String used,String capacity){return Component.translatable("screen.rematter.capacity",used,capacity);}
    private static MutableComponent joined(Component first,Component second){return Component.translatable("screen.rematter.joined",first,second);}
    private Component energyLine(){return Component.translatable("screen.rematter.energy_buffer",number(menu.progress().energy()),number(MachineBlockEntity.ENERGY_CAPACITY));}

    private final class StockRow extends Button {
        private MachineSnapshot.Entry entry;
        StockRow(){super(Button.builder(Component.empty(),b->{var row=(StockRow)b;if(row.entry!=null)toggleFavorite(row.entry);}).bounds(leftPos+8,topPos+50,174,18));}
        void show(MachineSnapshot.Entry value){entry=value;if(value==null)return;setMessage(name(value));setTooltip(Tooltip.create(stockTooltip(value,name(value).getString()).copy().append("\n").append(ui("favorite_toggle"))));}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){if(entry==null)return;ScreenChrome.raised(g,getX(),getY(),getWidth(),18,isHoveredOrFocused(),true);substanceIcon(g,key(entry.id(),entry.phase()),getX()+1,getY()+1);int amountWidth=Math.min(43,getWidth()/3);String amount=Quantities.compact(entry.amount());int right=getX()+getWidth()-17;g.text(font,amount,right-font.width(amount),getY()+5,accent(),false);if(font.width(name(entry))<=getWidth()-amountWidth-40)text(g,name(entry).getString(),getX()+21,getY()+5,getWidth()-amountWidth-40,entry.known()?TEXT:WARNING);ScreenChrome.icon(g,favorite(entry.key())?UiIcon.STAR:UiIcon.STAR_OUTLINE,getX()+getWidth()-16,getY()+1,favorite(entry.key())?accent():MUTED);}
    }
    private final class DeviceRow {
        private TerminalDevicesPayload.Device device;
        private final DarkButton open=DarkButton.icon(UiIcon.OPEN,ui("open"),tr("terminal.return_hint"),leftPos+161,topPos+42,18,18,b->deviceAction(device,TerminalDeviceActionPayload.Action.OPEN));
        private final DarkButton queue=DarkButton.icon(UiIcon.QUEUE_ADD,ui("queue_one"),tr("add_batch_hint"),leftPos+141,topPos+42,18,18,b->deviceAction(device,TerminalDeviceActionPayload.Action.QUEUE_BATCH));
        private int y;
        void show(TerminalDevicesPayload.Device value,int y){device=value;this.y=y;open.visible=open.active=value!=null;queue.visible=value!=null;queue.active=value!=null&&value.canQueue();open.setY(y+3);queue.setY(y+3);if(value!=null)open.setTooltip(Tooltip.create(Component.translatable("block.rematter."+value.kind().id()).copy().append("\n").append(Component.literal(value.position().toShortString())).append("\n").append(ui("open_hint"))));}
        void draw(GuiGraphicsExtractor g){if(device==null)return;int yy=y-topPos;String title=Component.translatable("block.rematter."+device.kind().id()).getString();ScreenChrome.icon(g,StatusText.icon(device.status(),device.kind()),9,yy+6,-1);text(g,title,27,yy+3,110,TEXT);String status=StatusText.of(device.status(),0,0).getString();if(device.queuedBatches()>0)status+=" · "+device.queuedBatches()+" "+ui("queued").getString();text(g,status,27,yy+15,110,working(device.status())?machineAccent(device.kind()):MUTED);if(device.duration()>0)g.fill(11,yy+25,11+(int)(126L*device.progress()/device.duration()),yy+26,machineAccent(device.kind()));}
    }
}
