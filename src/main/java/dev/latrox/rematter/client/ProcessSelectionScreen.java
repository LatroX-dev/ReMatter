package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.client.ScreenChrome.Icon;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.client.ui.UiMetrics;
import dev.latrox.rematter.content.MachineChoices;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.SelectProcessPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** One searchable, scrollable recipe list and its complete material preview. Selection sends only a recipe ID. */
public final class ProcessSelectionScreen extends Screen {
    private static final int PAPER=UiPalette.TEXT,MUTED=UiPalette.MUTED;
    private static final int RESULT_PITCH=18,MATERIAL_PITCH=22;
    private final MachineMenu menu;
    private final int accent;
    private final ScrollModel results=new ScrollModel(7),materials=new ScrollModel(4);
    private final ScrollBar resultBar=new ScrollBar(results),materialBar=new ScrollBar(materials);
    private final List<ProductButton> resultButtons=new ArrayList<>();
    private final Map<String,ItemStack> icons=new HashMap<>();
    private ChemistryRegistry registry;
    private net.minecraft.locale.Language language;
    private List<ProcessPresentation.Product> products=List.of(),filtered=List.of();
    private List<MaterialRow> rows=List.of();
    private ProcessPresentation.Product rowsProduct,focusedProduct;
    private boolean rowsEffective;
    private MachineSnapshot rowsSnapshot;
    private MachineMenu.Stock rowsStock;
    private boolean materialsFocused;
    private List<ProcessPresentation.Product> focusLookupList;
    private String focusedId="",focusLookupId="",lastClickedId="";
    private EditBox search;
    private DarkButton confirm,clear,knownTab,lockedTab;
    private boolean showLocked;
    private com.google.gson.JsonObject knowledgeView;
    private int left,top,panelWidth,panelHeight,resultsX,resultsWidth,resultsTop,detailX,detailWidth,footerY,ingredientTop;

    public ProcessSelectionScreen(MachineMenu menu) {
        super(Component.translatable("screen.rematter.choose_recipe"));this.menu=menu;this.accent=UiPalette.machineAccent(menu.kind());
    }
    private static Component tr(String key,Object... args){return Component.translatable("screen.rematter.recipes."+key,args);}

    @Override protected void init() {
        panelWidth=Math.min(UiMetrics.PICKER_WIDTH,width-12);
        panelHeight=Math.min(UiMetrics.PICKER_HEIGHT,height-12);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        resultsX=left+8;resultsWidth=(panelWidth-36)/2;resultsTop=top+(menu.kind().synthesis()?62:42);
        detailX=resultsX+resultsWidth+6;detailWidth=left+panelWidth-8-detailX;
        footerY=top+panelHeight-24;ingredientTop=top+55;
        results.setVisibleRows(Math.max(1,(footerY-resultsTop-6)/RESULT_PITCH));
        materials.setVisibleRows(Math.max(1,(footerY-ingredientTop-30)/MATERIAL_PITCH));
        String query=search==null?"":search.getValue();
        search=addRenderableWidget(new EditBox(font,resultsX+4,top+25,resultsWidth-26,12,tr("search")));
        search.setMaxLength(128);search.setBordered(false);search.setTextColor(PAPER);
        search.setHint(Component.literal(ScreenChrome.fit(font,tr("search").getString(),search.getWidth()-2)).withStyle(style->style.withColor(MUTED&0xffffff)));
        search.setValue(query);search.setResponder(value->{results.reset();filter();});
        clear=addRenderableWidget(DarkButton.icon(Icon.CLEAR,tr("clear_search"),tr("clear_search_hint"),
                resultsX+resultsWidth-18,top+20,18,18,b->{search.setValue("");setFocused(search);}));
        if(menu.kind().synthesis()){
            knownTab=addRenderableWidget(DarkButton.create(tr("known"),tr("known_hint"),resultsX,top+42,(resultsWidth-3)/2,16,b->{showLocked=false;results.reset();filter();}));
            lockedTab=addRenderableWidget(DarkButton.create(tr("locked"),tr("locked_hint"),resultsX+(resultsWidth+3)/2,top+42,resultsWidth-(resultsWidth+3)/2,16,b->{showLocked=true;results.reset();filter();}));
        }
        resultButtons.clear();
        for(int row=0;row<results.visibleRows();row++) {
            int index=row;
            resultButtons.add(addRenderableWidget(new ProductButton(Button.builder(Component.empty(),b->focusResult(index))
                    .bounds(resultsX,resultsTop+row*RESULT_PITCH,resultsWidth-7,RESULT_PITCH))));
        }
        confirm=addRenderableWidget(DarkButton.create(tr("use"),tr("use_hint"),detailX,footerY,detailWidth-52,18,b->selectFocused()));
        addRenderableWidget(DarkButton.create(tr("close"),tr("close_hint"),detailX+detailWidth-48,footerY,48,18,b->onClose()));
        confirm.setAccent(accent);clear.setAccent(accent);
        resultBar.setBounds(resultsX+resultsWidth-5,resultsTop,5,results.visibleRows()*RESULT_PITCH);
        materialBar.setBounds(detailX+detailWidth-5,ingredientTop,5,materials.visibleRows()*MATERIAL_PITCH);
        loadChoices();setInitialFocus(search);
    }

    private void loadChoices() {
        language=net.minecraft.locale.Language.getInstance();registry=ClientChemistry.current();
        MachineKind kind=menu.kind();
        products=MachineChoices.forKind(kind,registry).stream()
                .map(choice->ProcessPresentation.resolve(registry,kind,choice.id(),choice.name()))
                .flatMap(java.util.Optional::stream).toList();
        icons.clear();rowsProduct=null;
        if(focusedId.isEmpty())focusedId=menu.snapshot().selectedId();
        filter();
    }
    private void filter() {
        String query=search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered=products.stream().filter(product->!menu.kind().synthesis()||known(product)!=showLocked).filter(product->product.name().toLowerCase(Locale.ROOT).contains(query)
                ||product.id().toLowerCase(Locale.ROOT).contains(query)
                ||Stream.concat(product.inputs().keySet().stream(),product.outputs().keySet().stream())
                    .anyMatch(key->SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(key.id()),query))).toList();
        results.setSize(filtered.size());
        int selected=-1;
        for(int i=0;i<filtered.size();i++)if(filtered.get(i).id().equals(focusedId)){selected=i;break;}
        results.select(Math.max(0,selected));
        focusedId=filtered.isEmpty()?"":filtered.get(results.selected()).id();
        materials.reset();refreshResults();
    }
    private void refreshResults() {
        for(int row=0;row<resultButtons.size();row++) {
            int index=results.offset()+row;
            var button=resultButtons.get(row);button.setProduct(index<filtered.size()?filtered.get(index):null);
            button.visible=button.active=button.product!=null;
        }
        confirm.active=focused()!=null&&(!menu.kind().synthesis()||known(focused()));
        if(knownTab!=null){knownTab.setSelected(!showLocked);lockedTab.setSelected(showLocked);}
        clear.visible=!search.getValue().isEmpty();
    }
    private ProcessPresentation.Product focused() {
        if(filtered!=focusLookupList||!focusedId.equals(focusLookupId)) {
            focusLookupList=filtered;focusLookupId=focusedId;
            focusedProduct=filtered.stream().filter(p->p.id().equals(focusedId)).findFirst().orElse(null);
        }
        return focusedProduct;
    }
    private void focusResult(int row) {
        int index=results.offset()+row;
        if(index>=filtered.size())return;
        materialsFocused=false;
        results.select(index);focusedId=filtered.get(index).id();materials.reset();refreshResults();
    }
    private void selectFocused() {
        if(focused()!=null&&(!menu.kind().synthesis()||known(focused()))&&minecraft.player!=null&&minecraft.player.containerMenu==menu) {
            ClientPacketDistributor.sendToServer(new SelectProcessPayload(menu.containerId,focusedId));onClose();
        }
    }
    @Override public void tick() {
        if(minecraft.player==null||minecraft.player.containerMenu!=menu){onClose();return;}
        if(menu.kind().synthesis()&&knowledgeView!=menu.extras()){knowledgeView=menu.extras();filter();}
        if(registry!=ClientChemistry.current()||language!=net.minecraft.locale.Language.getInstance())loadChoices();
    }
    @Override public boolean isPauseScreen(){return false;}

    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(inside(x,y,resultsX,resultsTop,resultsWidth,results.visibleRows()*RESULT_PITCH)) {
            lastClickedId="";results.wheel(dy);refreshResults();return true;
        }
        if(inside(x,y,detailX,ingredientTop,detailWidth,materials.visibleRows()*MATERIAL_PITCH)) {
            materials.wheel(dy);return true;
        }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(resultBar.mouseClicked(event.x(),event.y(),event.button())){materialsFocused=false;lastClickedId="";refreshResults();return true;}
        if(materialBar.mouseClicked(event.x(),event.y(),event.button())){materialsFocused=true;setFocused(null);lastClickedId="";return true;}
        materialsFocused=inside(event.x(),event.y(),detailX,ingredientTop,detailWidth,materials.visibleRows()*MATERIAL_PITCH);
        String clicked="";
        if(event.button()==0&&inside(event.x(),event.y(),resultsX,resultsTop,resultsWidth-7,results.visibleRows()*RESULT_PITCH)) {
            int index=results.offset()+(int)((event.y()-resultsTop)/RESULT_PITCH);
            if(index<filtered.size())clicked=filtered.get(index).id();
        }
        boolean repeat=!clicked.isEmpty()&&clicked.equals(lastClickedId);
        boolean handled=super.mouseClicked(event,doubleClick);lastClickedId=clicked;
        if(handled&&doubleClick&&repeat)selectFocused();
        if(materialsFocused&&!handled)setFocused(null);
        return handled||materialsFocused;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(resultBar.mouseDragged(event.x(),event.y())){refreshResults();return true;}
        if(materialBar.mouseDragged(event.x(),event.y()))return true;
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        boolean dragged=resultBar.mouseReleased()|materialBar.mouseReleased();
        return super.mouseReleased(event)||dragged;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.isEscape()){onClose();return true;}
        int key=event.key();
        if(key==257||key==335){selectFocused();return true;}
        boolean pageKey=key==266||key==267;
        if(materialsFocused||event.hasShiftDown()&&pageKey) {
            switch(key) {
                case 265 -> materials.scroll(-1);
                case 264 -> materials.scroll(1);
                case 266 -> materials.scroll(-materials.visibleRows());
                case 267 -> materials.scroll(materials.visibleRows());
                case 268 -> materials.setOffset(0);
                case 269 -> materials.setOffset(materials.maxOffset());
                default -> { return super.keyPressed(event); }
            }
            return true;
        }
        boolean listKey=key==264||key==265||key==266||key==267||(!search.isFocused()&&(key==268||key==269));
        if(listKey&&results.key(key)) {
            focusedId=results.selected()<0?"":filtered.get(results.selected()).id();materials.reset();refreshResults();return true;
        }
        if(search.canConsumeInput()) {
            if(search.keyPressed(event))return true;
            if(key!=258)return true; // Character input arrives separately; do not forward inventory/drop hotkeys.
        }
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        if(event.isAllowedChatCharacter()) {materialsFocused=false;setFocused(search);return search.charTyped(event);}
        return super.charTyped(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        refreshResults();
        ScreenChrome.panel(g,left,top,panelWidth,panelHeight);
        g.fill(left+8,top+17,left+panelWidth-8,top+18,UiPalette.BORDER);
        g.fill(left+8,top+17,left+56,top+18,UiPalette.machineAccent(menu.kind()));
        ScreenChrome.icon(g,Icon.RECIPE,left+8,top+3,accent);
        g.text(font,ScreenChrome.fit(font,title.getString(),panelWidth-32),left+24,top+5,PAPER,false);
        ScreenChrome.inset(g,resultsX,top+20,resultsWidth,18);
        ScreenChrome.inset(g,detailX,top+20,detailWidth,footerY-top-24);
        resultBar.extractRenderState(g);
        if(filtered.isEmpty())ScreenChrome.twoLines(g,font,tr(menu.kind().synthesis()&&!showLocked?"no_known":"no_matches").getString(),resultsX+3,resultsTop+5,resultsWidth-10,MUTED);
        String count=(filtered.size()==1?tr("count_one"):tr("count",filtered.size())).getString();
        g.text(font,ScreenChrome.fit(font,count,resultsWidth),resultsX,top+panelHeight-11,MUTED,false);
        drawDetails(g,mouseX,mouseY);
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        if(search.getValue().isEmpty()&&search.isFocused())g.text(font,ScreenChrome.fit(font,tr("search").getString(),search.getWidth()-2),search.getX()+2,search.getY(),MUTED,false);
    }
    private void drawDetails(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        var product=focused();
        if(product==null) {
            ScreenChrome.twoLines(g,font,tr("select_hint").getString(),detailX+5,top+28,detailWidth-10,MUTED);return;
        }
        boolean selected=product.id().equals(menu.snapshot().selectedId());
        var preview=menu.snapshot().preview();boolean effective=selected&&preview.id().equals(product.id());
        long energy=effective?preview.energy():product.energy();int ticks=effective?preview.ticks():product.ticks();
        drawProductIcon(g,product,detailX+4,top+24);
        ScreenChrome.twoLines(g,font,productLabel(product),detailX+24,top+23,detailWidth-28,PAPER);
        g.text(font,ScreenChrome.fit(font,tr("materials").getString(),detailWidth-12),detailX+4,top+44,MUTED,false);
        if(inside(mouseX,mouseY,detailX,top+43,detailWidth,11))
            g.setComponentTooltipForNextFrame(font,List.of(tr("materials_hint"),tr("material_scroll_hint")),mouseX,mouseY);
        if(inside(mouseX,mouseY,detailX,top+20,detailWidth,31)) {
            var tooltip=new ArrayList<Component>(List.of(Component.literal(productLabel(product)),Component.literal(product.id()),perBatch(product),
                    tr("cost",Quantities.energy(Quantities.number(energy)),Quantities.duration(ticks)),tr(effective?"upgraded":"base_values")));
            tooltip.addAll(requirements(product));g.setComponentTooltipForNextFrame(font,tooltip,mouseX,mouseY);
        }
        List<MaterialRow> entries=materialRows(product,effective&&preview.complete());materials.setSize(entries.size());
        for(int row=0;row<materials.visibleRows()&&materials.offset()+row<entries.size();row++)
            drawMaterial(g,entries.get(materials.offset()+row),ingredientTop+row*MATERIAL_PITCH,mouseX,mouseY);
        materialBar.extractRenderState(g);
        int costY=footerY-29;
        String cost=tr("summary",Quantities.energy(Quantities.compact(energy)),Quantities.duration(ticks)).getString();
        g.text(font,ScreenChrome.fit(font,cost,detailWidth-8),detailX+4,costY,PAPER,false);
        var requirements=requirements(product);
        Component note=requirements.isEmpty()?perBatch(product):requirements.getFirst();
        g.text(font,ScreenChrome.fit(font,note.getString(),detailWidth-8),detailX+4,costY+11,requirements.isEmpty()?MUTED:UiPalette.WARNING,false);
        if(inside(mouseX,mouseY,detailX,costY,detailWidth,20)) {
            var tooltip=new ArrayList<Component>(List.of(tr("cost",Quantities.energy(Quantities.number(energy)),Quantities.duration(ticks)),perBatch(product)));
            tooltip.addAll(requirements);g.setComponentTooltipForNextFrame(font,tooltip,mouseX,mouseY);
        }
    }
    private void drawMaterial(GuiGraphicsExtractor g,MaterialRow material,int y,int mouseX,int mouseY) {
        int color=phaseColor(material.phase());
        g.fill(detailX+3,y,detailX+detailWidth-7,y+MATERIAL_PITCH-1,UiPalette.PANEL);
        if(material.key()!=null)SubstanceIcon.draw(g,detailX+4,y+2,material.key());
        else ScreenChrome.icon(g,Icon.INFO,detailX+6,y+4,color);
        String name=(material.output()?"+ ":"")+material.label();
        g.text(font,ScreenChrome.fit(font,name,detailWidth-32),detailX+23,y+1,material.known()?PAPER:UiPalette.WARNING,false);
        String available=material.available().isPresent()?Quantities.compact(material.available().getAsLong()):"?";
        Component quantities=material.output()?tr("yield",Quantities.compact(material.required())):tr("needed_available",Quantities.compact(material.required()),available);
        int amountColor=material.output()?color:material.available().isEmpty()?MUTED:material.available().getAsLong()>=material.required()?accent:UiPalette.WARNING;
        g.text(font,ScreenChrome.fit(font,quantities.getString(),detailWidth-32),detailX+23,y+11,amountColor,false);
        if(inside(mouseX,mouseY,detailX+3,y,detailWidth-10,MATERIAL_PITCH-1)) {
            var tooltip=new ArrayList<Component>();
            tooltip.add(Component.translatable("gui.rematter.viewer.substance_phase",material.label(),SubstanceNames.phase(material.phase())));
            tooltip.add(Component.literal(material.id()));
            tooltip.add(tr(material.output()?"produces":"needs",Quantities.matter(Quantities.number(material.required()))));
            if(!material.output())tooltip.add(material.available().isPresent()?tr("available",Quantities.matter(Quantities.number(material.available().getAsLong()))):tr("available_unknown"));
            if(!material.known())tooltip.add(Component.translatable("screen.rematter.unknown_substance_hint"));
            g.setComponentTooltipForNextFrame(font,tooltip,mouseX,mouseY);
        }
    }
    private static boolean inside(double mx,double my,int x,int y,int width,int height){return mx>=x&&mx<x+width&&my>=y&&my<y+height;}

    private boolean known(ProcessPresentation.Product product){
        if(!menu.kind().synthesis()||menu.extras().has("researchEnabled")&&!menu.extras().get("researchEnabled").getAsBoolean())return true;
        String target=product.outputId();
        return menu.extras().has("knownTargets")&&menu.extras().getAsJsonArray("knownTargets").asList().stream().anyMatch(id->id.getAsString().equals(target));
    }
    private static Component perBatch(ProcessPresentation.Product product) {
        long count = product.outputCount();
        Component value = switch (product.outputKind()) {
            case "fluid" -> Quantities.fluid(Long.toString(count));
            case "item" -> count == 1 ? tr("one_item") : tr("items", count);
            default -> count == 1 ? tr("one_substance") : tr("substances", count);
        };
        return tr("per_batch", value);
    }
    private List<Component> requirements(ProcessPresentation.Product product) {
        var lines=new ArrayList<Component>();String advancement="";
        if(menu.kind().synthesis()) {
            String id=product.id().startsWith(MachineChoices.FLUID_PREFIX)?product.id().substring(MachineChoices.FLUID_PREFIX.length()):product.id();
            var requirement=registry.synthesisPolicy().requirement(id);advancement=requirement.advancement();
            if(requirement.tier()>1)lines.add(Component.translatable("gui.rematter.viewer.pressure_required"));
        } else {
            var process=registry.recipe(dev.latrox.rematter.chemistry.SubstanceId.parse(product.id())).orElse(null);
            if(process!=null)advancement=process.requiresAdvancement();
        }
        if(!advancement.isEmpty())lines.add(Component.translatable("gui.rematter.viewer.advancement_required",advancement));
        return lines;
    }


    /** Rebuilt only when the product, the preview or the synchronized stock changes. */
    private List<MaterialRow> materialRows(ProcessPresentation.Product product, boolean effective) {
        var snapshot = menu.snapshot();
        var stock = menu.stock();
        if (product == rowsProduct && effective == rowsEffective && snapshot == rowsSnapshot && stock == rowsStock) return rows;
        List<MaterialRow> result = new ArrayList<>();
        if (effective) {
            var preview = snapshot.preview();
            for (var amount : preview.inputs()) result.add(row(amount, snapshot.capacity() > 0 ? OptionalLong.of(amount.available()) : OptionalLong.empty(), false));
            for (var amount : preview.outputs()) result.add(row(amount, OptionalLong.empty(), true));
        } else {
            Map<SubstanceKey, Long> available = new HashMap<>();
            for (var entry : stock.entries()) {
                var key = key(entry.id(), entry.phase());
                if (key != null) available.put(key, entry.amount());
            }
            boolean complete = stock.complete() && snapshot.capacity() > 0;
            product.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(row(entry,
                    ProcessPresentation.available(available, complete, entry.getKey()), false)));
            product.outputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(row(entry, OptionalLong.empty(), true)));
        }
        rowsProduct = product;
        rowsEffective = effective;
        rowsSnapshot = snapshot;
        rowsStock = stock;
        rows = List.copyOf(result);
        return rows;
    }

    private static MaterialRow row(MachineSnapshot.Amount amount, OptionalLong available, boolean output) {
        String name = SubstanceNames.substance(amount.id(), amount.phase(), amount.known()).getString();
        return new MaterialRow(key(amount.id(), amount.phase()), amount.id(), amount.known() ? SubstanceLabels.shortLabel(amount.id(), name) : name,
                amount.phase(), amount.known(), amount.required(), available, output);
    }

    private static MaterialRow row(Map.Entry<SubstanceKey, Long> amount, OptionalLong available, boolean output) {
        SubstanceKey key = amount.getKey();
        String name = SubstanceNames.substance(key).getString();
        boolean known = SubstanceNames.known(key);
        return new MaterialRow(key, key.id().toString(), known ? SubstanceLabels.shortLabel(key.id().toString(), name) : name,
                SubstanceNames.phaseId(key.phase()), known, amount.getValue(), available, output);
    }

    private static SubstanceKey key(String id, String phase) {
        try { return new SubstanceKey(SubstanceId.parse(id), Phase.valueOf(phase.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private record MaterialRow(SubstanceKey key, String id, String label, String phase, boolean known, long required,
                               OptionalLong available, boolean output) {}

    private static String productSymbol(ProcessPresentation.Product product) {
        return product.outputs().size() == 1
                ? SubstanceLabels.iconLabel(product.outputs().keySet().iterator().next().id()) : "";
    }

    private static String productLabel(ProcessPresentation.Product product) {
        return SubstanceLabels.prefixed(productSymbol(product), product.name());
    }

    private static ItemStack item(String id) {
        Identifier key = Identifier.parse(id);
        return BuiltInRegistries.ITEM.containsKey(key) ? new ItemStack(BuiltInRegistries.ITEM.getValue(key)) : ItemStack.EMPTY;
    }
    private static ItemStack fluidIcon(String id) {
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.FLUID.containsKey(key)) return ItemStack.EMPTY;
        var bucket = BuiltInRegistries.FLUID.getValue(key).getBucket();
        return new ItemStack(bucket == Items.AIR ? Items.BUCKET : bucket);
    }
    private void drawProductIcon(GuiGraphicsExtractor graphics, ProcessPresentation.Product product, int x, int y) {
        ItemStack icon = icons.computeIfAbsent(product.id(), ignored -> {
            if (product.outputKind().equals("item")) return item(product.outputId());
            if (product.outputKind().equals("fluid")) return fluidIcon(product.outputId());
            return ItemStack.EMPTY;
        });
        if (!icon.isEmpty()) graphics.item(icon, x, y);
        else {
            var key = product.outputs().keySet().stream().sorted().findFirst().orElse(null);
            if(key!=null) SubstanceIcon.draw(graphics,x,y,key);
            else ScreenChrome.icon(graphics,Icon.INFO,x+2,y+2,accent);
        }
    }

    private static int phaseColor(String phase) {
        return switch (phase) { case "gas" -> UiPalette.GAS; case "liquid" -> UiPalette.LIQUID; default -> UiPalette.WARNING; };
    }


    private final class ProductButton extends DarkButton {
        private ProcessPresentation.Product product;
        private String label="";
        ProductButton(Button.Builder builder){super(builder,null,false);}
        void setProduct(ProcessPresentation.Product value) {
            if(product==value)return;product=value;
            if(value==null)return;
            label=productLabel(value);setMessage(Component.literal(label));
            var hint=Component.literal(label+"\n"+value.id()+"\n").append(perBatch(value)).append("\n").append(tr("view_hint"));
            if(menu.kind().synthesis()&&!known(value))hint.append("\n").append(tr("locked_hint"));
            requirements(value).forEach(line->hint.append("\n").append(line));setTooltip(Tooltip.create(hint));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
            if(product==null)return;
            ScreenChrome.raised(g,getX(),getY(),getWidth(),getHeight(),product.id().equals(focusedId)||isHoveredOrFocused(),active);
            drawProductIcon(g,product,getX()+1,getY()+1);
            boolean unlocked=known(product);
            if(!unlocked)ScreenChrome.icon(g,Icon.LOCK,getX()+2,getY()+3,UiPalette.DISABLED);
            boolean chosen=product.id().equals(menu.snapshot().selectedId());
            if(chosen)g.fill(getX()+getWidth()-3,getY()+3,getX()+getWidth()-1,getY()+getHeight()-3,accent);
            g.text(font,ScreenChrome.fit(font,label,getWidth()-23),getX()+20,getY()+5,unlocked?PAPER:UiPalette.DISABLED,false);
        }
    }
}
