package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Familiar machine slots and a separate disk/quantity browser; all actions retain server validation. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int INK = 0xff243639, MUTED = 0xff586b6c, CYAN = 0xff247c78, COPPER = 0xffa15d38;
    private static final int PAPER = 0xffe7e1d3, PANEL = 0xffd6d4ca, EDGE = 0xff8b9590, HIGHLIGHT = 0xfff7f2e6;
    private static final int RECESS = 0xff33464a, SLOT_LIGHT = 0xff758785, SLOT_INK = 0xffedf2e9;
    private static final int TILE = 28, GRID_ROWS = 3;
    private final List<StockButton> stockButtons = new ArrayList<>();
    private EditBox search;
    private Button primary, choose, automation, repeat, addBatch, migrate, previous, next;
    private TabButton processTab, upgradesTab, configurationTab;
    private int page;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 256, 240); }
    @Override protected void init() {
        super.init();
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, leftPos + 17, topPos + 31, 222, 14, tr("search")));
        search.setHint(tr("search")); search.setMaxLength(128); search.setBordered(false); search.setTextColor(INK);
        search.setValue(query); search.setResponder(value -> page = 0);
        processTab = tab(MachineMenu.View.MAIN, 27, passive() ? "tab_contents" : "tab_process");
        upgradesTab = tab(MachineMenu.View.UPGRADES, 53, "tab_upgrades");
        configurationTab = tab(MachineMenu.View.CONFIGURATION, 79, "tab_configuration");
        primary = control("start", 91, 120, 74, 18, b -> {
            var decision = MachinePrimaryAction.forSnapshot(menu.snapshot());
            if (!decision.enabled()) return;
            if (decision.opensRecipeSelection()) openRecipes(); else if (decision.menuAction() >= 0) send(decision.menuAction());
        }, "start_once_hint");
        choose = control("recipe", 198, 27, 46, 16, b -> openRecipes(), "choose_hint");
        automation = control("", 12, 94, 232, 18, b -> send(3), "automation_hint");
        repeat = control("", 12, 116, 146, 18, b -> send(5), "continuous_hint");
        addBatch = control("add_batch", 164, 116, 80, 18, b -> send(4), "add_batch_hint");
        migrate = control("move_to_disk", 12, 116, 232, 18, b -> send(7), "move_to_disk_hint");
        previous = control("previous", 12, 132, 18, 14, b -> page = Math.max(0, page - 1), "previous_hint");
        next = control("next", 226, 132, 18, 14, b -> page++, "next_hint");
        stockButtons.clear();
        for (int i = 0; i < 8 * GRID_ROWS; i++) {
            var button = new StockButton(Button.builder(Component.empty(), b -> {
                var stock = (StockButton)b;
                if (stock.entry != null) minecraft.pushGuiLayer(new StockDetailsScreen(stock.entry.id(), stock.entry.phase()));
            }).bounds(leftPos, topPos, TILE - 1, 25));
            stockButtons.add(addRenderableWidget(button));
        }
        updateWidgets();
    }
    private static Component tr(String key) { return Component.translatable("screen.rematter." + key); }
    private Button control(String key, int x, int y, int width, int height, Button.OnPress action, String hint) {
        return addRenderableWidget(Button.builder(key.isEmpty() ? Component.empty() : tr(key), action)
                .bounds(leftPos + x, topPos + y, width, height).tooltip(Tooltip.create(tr(hint))).build(ConsoleButton::new));
    }
    private TabButton tab(MachineMenu.View view, int y, String key) {
        return addRenderableWidget(new TabButton(Button.builder(tr(key), b -> { menu.setView(view); page = 0; })
                .bounds(leftPos - 23, topPos + y, 23, 23).tooltip(Tooltip.create(tr(key))), view));
    }
    private boolean main() { return menu.view() == MachineMenu.View.MAIN; }
    private boolean configuration() { return menu.view() == MachineMenu.View.CONFIGURATION; }
    private boolean passive() { return !menu.kind().processes(); }
    private boolean disk() { return menu.diskRole(); }
    private boolean directCellRecovery() { return (menu.kind() == MachineKind.DECOMPOSER || menu.kind() == MachineKind.SYNTHESIZER) && menu.snapshot().cell().installed(); }
    private boolean handPreparator() { return menu.kind() == MachineKind.HAND_PREPARATOR; }
    private boolean preparator() { return handPreparator() || menu.kind() == MachineKind.PREPARATOR || menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR; }
    private boolean industrial() { return menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR || menu.kind() == MachineKind.INDUSTRIAL_REACTOR; }
    private boolean selecting() { return menu.kind() == MachineKind.SYNTHESIZER || menu.kind() == MachineKind.ELECTROLYZER || menu.kind() == MachineKind.REACTOR || menu.kind() == MachineKind.INDUSTRIAL_REACTOR; }
    private static boolean working(String status) { return List.of("working", "grinding", "sieving", "collecting", "dosing", "mixing", "packing").contains(status); }
    private void openRecipes() { minecraft.pushGuiLayer(new ProcessSelectionScreen(menu)); }
    private void send(int action) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
    private Component statusText() {
        var s = menu.snapshot();
        if (s.status().equals("structure") && industrial()) return Component.translatable("status.rematter." + s.structure().diagnostic());
        if (working(s.status())) return tr("processing");
        return Component.translatable("status.rematter." + s.status());
    }
    private List<MachineSnapshot.Entry> entries() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        return menu.snapshot().entries().stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(query)
                || e.id().toLowerCase(Locale.ROOT).contains(query) || e.phase().contains(query)).toList();
    }
    private int gridColumns() { return disk() ? 5 : 8; }
    private int gridX() { return disk() ? 104 : 16; }
    private int perPage() { return gridColumns() * GRID_ROWS; }
    private int pages() { return Math.max(1, (entries().size() + perPage() - 1) / perPage()); }
    private void updateWidgets() {
        var s = menu.snapshot(); boolean ready = s.containerId() == menu.containerId;
        var decision = MachinePrimaryAction.forSnapshot(s);
        processTab.active = configurationTab.active = ready;
        processTab.setMessage(tr(passive() ? "tab_contents" : "tab_process"));
        processTab.setTooltip(Tooltip.create(processTab.getMessage()));
        upgradesTab.visible = ready && menu.supportsUpgrades();
        upgradesTab.active = ready;
        search.visible = search.active = ready && main() && passive();
        primary.visible = ready && main() && decision.visible(); primary.active = ready && decision.enabled();
        primary.setMessage(tr(decision.labelKey())); primary.setTooltip(Tooltip.create(tr(decision.hintKey())));
        primary.setX(leftPos + (decision.opensRecipeSelection() ? 62 : 91)); primary.setWidth(decision.opensRecipeSelection() ? 132 : 74);
        choose.visible = ready && main() && selecting() && !decision.opensRecipeSelection(); choose.active = ready && s.choiceCount() > 0;
        automation.visible = ready && configuration(); automation.setMessage(tr(s.automation() ? "automation_on" : "automation_off"));
        repeat.visible = addBatch.visible = ready && configuration() && selecting();
        boolean valid = s.selected() >= 0 && !s.preview().id().isEmpty() && s.selectedId().equals(s.preview().id()) && !s.status().equals("selection_unavailable");
        repeat.active = valid; repeat.setMessage(tr(s.repeat() ? "stop_repeat" : "run_repeat"));
        addBatch.active = valid && !s.repeat() && s.queuedBatches() < 64;
        migrate.visible = ready && configuration() && disk() && s.storageSource().equals("legacy");
        migrate.active = migrationBlocker(s) == null;
        migrate.setTooltip(Tooltip.create(migrationBlocker(s) == null ? tr("move_to_disk_hint") : migrationBlocker(s)));
        boolean recoveringUpgrade = configuration() && menu.slots.get(2).isActive();
        automation.setX(leftPos + (recoveringUpgrade ? 94 : 12)); automation.setWidth(recoveringUpgrade ? 150 : 232);
        migrate.setX(leftPos + (recoveringUpgrade ? 94 : 12)); migrate.setWidth(recoveringUpgrade ? 150 : 232);
        page = Math.clamp(page, 0, pages() - 1);
        previous.visible = next.visible = ready && main() && passive(); previous.active = page > 0; next.active = page + 1 < pages();
        previous.setX(leftPos + (disk() ? 104 : 16)); next.setX(leftPos + 226);
        var values = entries();
        for (int i = 0; i < stockButtons.size(); i++) {
            var b = stockButtons.get(i); int index = page * perPage() + i;
            b.entry = i < perPage() && index < values.size() ? values.get(index) : null;
            b.visible = b.active = ready && main() && passive() && b.entry != null;
            b.setX(leftPos + gridX() + i % gridColumns() * TILE); b.setY(topPos + 51 + i / gridColumns() * 26);
            if (b.entry != null) { b.setMessage(Component.literal(b.entry.name() + ", " + b.entry.phase() + ", " + number(b.entry.amount()) + " u")); b.setTooltip(Tooltip.create(stockTooltip(b.entry))); }
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        updateWidgets(); super.extractRenderState(g, mouseX, mouseY, partialTick);
        int mx = mouseX - leftPos, my = mouseY - topPos; var s = menu.snapshot();
        if (s.containerId() != menu.containerId || overActiveSlot(mx, my)) return;
        if (main() && !passive()) {
            if (inside(mx,my,12,45,14,47)) g.setTooltipForNextFrame(handPreparator() ? tr("crank_hint") : Component.literal(number(s.energy()) + " / 1,000,000 FE"), mouseX, mouseY);
            else if (inside(mx,my,12,96,232,18)) {
                var lines = new ArrayList<Component>(); lines.add(statusText());
                if (industrial()) lines.addAll(structureDetails(s));
                if (directCellRecovery()) lines.add(tr("recover_disk_hint"));
                tooltip(g, lines, mouseX, mouseY);
            } else if (!s.preview().id().isEmpty() && (inside(mx,my,12,26,choose.visible ? 180 : 232,16)
                    || !menu.inputRole() && inside(mx,my,32,47,76,43) || !menu.slots.get(1).isActive() && inside(mx,my,177,47,67,43))) tooltip(g, recipeDetails(s), mouseX, mouseY);
            else if (preparator() && inside(mx,my,79,61,18,18)) g.setTooltipForNextFrame(tr("optional_mix_hint"),mouseX,mouseY);
        } else if (main() && passive()) {
            if (!s.entriesComplete() && inside(mx,my,245,51,3,78)) g.setTooltipForNextFrame(tr("partial_contents_hint"),mouseX,mouseY);
            else if (disk() && inside(mx,my,12,83,84,45) || !disk() && inside(mx,my,48,132,158,14)) tooltip(g,storageDetails(s),mouseX,mouseY);
        } else if (configuration()) {
            if (inside(mx,my,12,26,232,18)) tooltip(g, industrial() ? structureDetails(s) : storageDetails(s),mouseX,mouseY);
            else if (inside(mx,my,12,80,232,12) || inside(mx,my,12,136,232,12)) tooltip(g,directCellRecovery() ? List.of(tr("recover_disk_hint")) : s.status().startsWith("migration_") ? List.of(statusText()) : storageDetails(s),mouseX,mouseY);
        }
    }
    private static boolean inside(int x,int y,int left,int top,int width,int height) { return x>=left && x<left+width && y>=top && y<top+height; }
    private boolean overActiveSlot(int x,int y) { return menu.slots.stream().anyMatch(slot -> slot.isActive() && inside(x,y,slot.x,slot.y,16,16)); }
    private void tooltip(GuiGraphicsExtractor g,List<Component> lines,int x,int y) { if(!lines.isEmpty())g.setComponentTooltipForNextFrame(font,lines,x,y); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractBackground(g,mouseX,mouseY,partialTick); int x=leftPos,y=topPos;
        g.fill(x+3,y+3,x+259,y+243,0x66000000); g.fill(x,y,x+256,y+240,EDGE);
        g.fill(x+1,y+1,x+255,y+239,PAPER); g.fill(x+3,y+3,x+253,y+4,HIGHLIGHT);
        g.fill(x+8,y+21,x+248,y+22,EDGE); g.fill(x+8,y+21,x+47,y+22,CYAN);
        g.fill(x+8,y+25,x+248,y+145,PANEL); g.fill(x+8,y+145,x+248,y+146,EDGE);
        var s=menu.snapshot();
        if(main() && passive()) {
            g.fill(x+12,y+27,x+244,y+46,EDGE);g.fill(x+13,y+28,x+243,y+45,HIGHLIGHT);
            if(disk()) {
                g.fill(x+11,y+50,x+97,y+128,EDGE);g.fill(x+12,y+51,x+96,y+127,PAPER);
                g.fill(x+16,y+117,x+92,y+122,EDGE);
                int fill=s.capacity()>0?(int)Math.clamp(74.0*s.used()/s.capacity(),0,74):0;
                g.fill(x+17,y+118,x+17+fill,y+121,CYAN);
            }
        } else if(main()) {
            if(!handPreparator()) {
                g.fill(x+14,y+47,x+25,y+87,EDGE);g.fill(x+15,y+48,x+24,y+86,RECESS);
                int fill=(int)(36L*s.energy()/1_000_000);g.fill(x+16,y+85-fill,x+23,y+85,0xff54ba9d);
                for(int mark=1;mark<4;mark++)g.fill(x+16,y+85-mark*9,x+23,y+86-mark*9,RECESS);
            }
            g.fill(x+110,y+68,x+169,y+74,EDGE);g.fill(x+164,y+64,x+169,y+78,EDGE);g.fill(x+169,y+66,x+173,y+76,EDGE);
            int fill=s.duration()>0?(int)Math.clamp(60L*s.progress()/s.duration(),0,60):0;g.fill(x+110,y+68,x+110+fill,y+74,CYAN);
        }
        for(int i=0;i<menu.slots.size();i++) {
            var slot=menu.slots.get(i);if(!slot.isActive())continue;
            int color=i>=MachineMenu.FIRST_PLAYER_SLOT?SLOT_LIGHT:i==2?0xff84749e:i==1?COPPER:CYAN;
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,color);
            g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,RECESS);
            g.fill(x+slot.x,y+slot.y+15,x+slot.x+16,y+slot.y+16,SLOT_LIGHT);
        }
        if(main()&&!passive()&&!menu.slots.get(1).hasItem()) { var icon=outputIcon(s.preview());if(!icon.isEmpty())g.fakeItem(icon,x+188,y+62); }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        text(g,title.getString().replace(" Controller",""),9,8,238,INK);g.text(font,tr("inventory"),47,147,MUTED,false);
        var s=menu.snapshot();if(s.containerId()!=menu.containerId){g.text(font,tr("loading"),12,31,MUTED,false);return;}
        if(menu.view()==MachineMenu.View.UPGRADES){upgradeLabels(g,s);return;}
        if(configuration()){configurationLabels(g,s);return;}
        if(passive()){storageLabels(g,s);return;}
        var p=s.preview();String name=!p.name().isBlank()?p.name():!s.selectedName().isBlank()?s.selectedName():tr(selecting()?"choose_recipe":"add_input").getString();
        text(g,name,12,30,choose.visible?180:232,INK);
        if(!handPreparator())g.text(font,"FE",13,89,MUTED,false);
        if(menu.inputRole())g.text(font,tr(menu.kind()==MachineKind.ANALYZER?"sample":"input"),40,48,MUTED,false);
        else if(!p.inputs().isEmpty()) {
            for(int i=0;i<Math.min(2,p.inputs().size());i++) {
                var a=p.inputs().get(i);text(g,a.name(),32,49+i*20,73,a.available()<a.required()?COPPER:INK);
                g.text(font,compact(a.required())+" u",32,59+i*20,MUTED,false);
            }
            if(p.inputs().size()>2||!p.complete())g.text(font,"+",98,83,MUTED,false);
        } else if(selecting())text(g,tr("stored_resources").getString(),32,61,73,MUTED);
        if(preparator()&&menu.slots.get(3).isActive())g.text(font,tr("mix_slot"),78,48,MUTED,false);
        if(menu.kind()==MachineKind.ANALYZER) {
            text(g,tr("analysis_result").getString(),177,48,67,MUTED);
            if(!p.outputs().isEmpty()){text(g,Component.translatable("screen.rematter.substance_count",p.outputs().size()).getString(),177,64,67,INK);text(g,tr("sample_kept").getString(),177,80,67,MUTED);}
        } else if(menu.slots.get(1).isActive()||!outputIcon(p).isEmpty()) {
            g.text(font,tr("output"),181,48,MUTED,false);
            if(p.outputCount()>0&&(p.outputKind().equals("fluid")||!menu.slots.get(1).hasItem()))text(g,p.outputCount()+(p.outputKind().equals("fluid")?" mB":""),188,83,56,MUTED);
        } else if(!p.outputs().isEmpty()) {
            var output=p.outputs().getFirst();text(g,output.name(),177,54,67,INK);text(g,compact(output.required())+" u",177,69,67,CYAN);
            if(p.outputs().size()>1||!p.complete())g.text(font,"+",237,82,MUTED,false);
        }
        if(s.duration()>0){String v=Math.clamp(100L*s.progress()/s.duration(),0,100)+"%";g.text(font,v,141-font.width(v)/2,83,MUTED,false);}
        String status=directCellRecovery()?tr("recover_disk_short").getString():statusText().getString();
        if(s.repeat()&&selecting())status+=" · "+tr("continuous").getString();
        text(g,status,12,101,232,working(s.status())?CYAN:INK);
    }
    private void upgradeLabels(GuiGraphicsExtractor g,MachineSnapshot s) {
        text(g,tr("tab_upgrades").getString(),12,30,232,INK);
        text(g,tr("upgrade_hint").getString(),12,51,232,MUTED);
        text(g,tr("upgrade_limit").getString(),12,66,232,MUTED);
        if(menu.slots.get(2).isActive()) {text(g,tr("upgrades").getString(),70,101,168,INK);text(g,tr("upgrade_effect_hint").getString(),70,115,168,MUTED);}
        String cost=s.preview().id().isEmpty()?compact(s.energy())+" FE":compact(s.preview().energy())+" FE · "+String.format(Locale.ROOT,"%.1f s",s.preview().ticks()/20.0);
        text(g,cost,12,137,232,MUTED);
    }
    private void configurationLabels(GuiGraphicsExtractor g,MachineSnapshot s) {
        boolean recovery=directCellRecovery();
        String heading=recovery?tr("recover_disk_short").getString():industrial()?s.structure().formed()?dimensions(s)+" · "+tr("columns_short").getString()+": "+s.structure().moduleCount():Component.translatable("status.rematter."+s.structure().diagnostic()).getString():tr("tab_configuration").getString();
        text(g,heading,12,30,232,recovery?COPPER:INK);
        boolean slots=false;
        for(int i:new int[]{0,1,3}) if(menu.slots.get(i).isActive()) {var slot=menu.slots.get(i);text(g,tr(i==1?"output":i==0&&disk()||i==3&&recovery?"disk_slot":i==3&&preparator()?"mix_slot":"input").getString(),slot.x-4,48,70,MUTED);slots=true;}
        if(!slots) {
            text(g,preparator()?tr("optional_mix_hint").getString():menu.kind()==MachineKind.ANALYZER?tr("sample_kept").getString():tr("source_"+s.storageSource()).getString(),12,52,232,INK);
            if(!preparator()&&menu.kind()!=MachineKind.ANALYZER)text(g,compact(s.used())+" / "+compact(s.capacity())+" u",12,67,232,MUTED);
            else if(industrial()&&s.structure().formed())text(g,s.structure().workRate()+"× · "+tr("columns_short").getString()+": "+s.structure().moduleCount(),12,67,232,MUTED);
        }
        String note = recovery ? tr("recover_disk_hint").getString() : s.status().startsWith("migration_") ? statusText().getString()
                : s.storageSource().equals("legacy") ? tr("legacy_move_hint").getString() : tr("automation_explain").getString();
        text(g,note,12,83,232,s.status().startsWith("migration_") ? COPPER : MUTED);
        if (menu.slots.get(2).isActive()) text(g,tr("upgrades").getString(),12,120,76,MUTED);
        String summary=handPreparator()?tr("hand_powered").getString():compact(s.energy())+" FE";
        if(s.queuedBatches()>0)summary+=" · "+s.queuedBatches()+" "+tr("queued_short").getString();
        if(industrial()&&s.structure().formed())summary+=" · "+s.structure().workRate()+"×";
        text(g,summary,12,137,232,MUTED);
    }
    private void storageLabels(GuiGraphicsExtractor g,MachineSnapshot s) {
        if(disk()) {
            int slot=menu.diskSlot();if(slot>=0&&slot<menu.slots.size()){var bay=menu.slots.get(slot);text(g,tr("disk_slot").getString(),bay.x-5,48,70,MUTED);}
            String name=s.storageSource().equals("legacy")?tr("legacy_storage").getString():s.cell().installed()?s.cell().name():tr("insert_disk").getString();
            text(g,name,16,87,76,INK);
            text(g,s.cell().installed()&&!s.cell().readable()?tr("cell_unreadable").getString():compact(s.used())+" / "+compact(s.capacity()),16,102,76,MUTED);
            if(!s.entriesComplete())text(g,tr("partial_short").getString(),12,134,88,COPPER);
        }
        if(entries().isEmpty())text(g,tr(!s.entriesComplete()?"contents_unavailable":search.getValue().isBlank()?"empty_storage":"no_matches").getString(),gridX()+2,83,244-gridX(),MUTED);
        String footer=disk()?(page+1)+" / "+pages():compact(s.used())+" / "+compact(s.capacity())+" u · "+(page+1)+"/"+pages();
        int start=disk()?125:38,width=disk()?97:184;text(g,footer,start+(width-Math.min(width,font.width(footer)))/2,135,width,CYAN);
        if(!s.entriesComplete())g.fill(245,51,247,129,COPPER);
    }
    private List<Component> recipeDetails(MachineSnapshot s) {
        var p=s.preview();var lines=new ArrayList<Component>();lines.add(Component.literal(p.name().isBlank()?s.selectedName():p.name()));
        if(menu.inputRole()&&!preparator()&&s.sampleBatchSize()>1)lines.add(Component.translatable("screen.rematter.composition_batch",s.sampleBatchSize()));
        lines.add(handPreparator()?Component.translatable("screen.rematter.crank_count",(p.ticks()+19)/20):Component.literal(String.format(Locale.ROOT,"%,d FE · %.1f s",p.energy(),p.ticks()/20.0)));
        for(var a:p.inputs())lines.add(Component.literal(a.name()+" · "+a.phase()+": "+a.required()+" needed / "+a.available()+" available"));
        for(var a:p.outputs())lines.add(Component.literal("→ "+a.name()+" · "+a.phase()+": "+a.required()+" u"));
        if(!p.complete())lines.add(tr("preview_partial"));return lines;
    }
    private List<Component> structureDetails(MachineSnapshot s) {
        var lines=new ArrayList<Component>();if(!industrial())return lines;var v=s.structure();
        if(v.width()>0)lines.add(Component.translatable("screen.rematter.structure_size",v.width(),v.depth(),v.height()));
        if(!v.formed()){lines.add(Component.translatable("status.rematter."+v.diagnostic()));lines.add(Component.translatable("screen.rematter.help_"+v.diagnostic()));lines.add(tr(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR?"preparation_stack":"reactor_stack"));}
        else{lines.add(Component.translatable("screen.rematter.structure_columns",v.moduleCount()));lines.add(Component.translatable("screen.rematter.structure_speed",v.workRate()));lines.add(tr("structure_scaling_hint"));}return lines;
    }
    private List<Component> storageDetails(MachineSnapshot s) {
        var lines=new ArrayList<Component>();lines.add(tr("source_"+s.storageSource()));lines.add(Component.literal(number(s.used())+" / "+number(s.capacity())+" u"));
        if(s.storageSource().equals("legacy"))lines.add(tr("legacy_storage_hint"));
        if(s.cell().installed()){lines.add(Component.literal(s.cell().name()));lines.add(s.cell().readable()?Component.literal(s.cell().types()+" / "+s.cell().maxTypes()+" types"):tr("cell_unreadable"));}
        if(s.fluidAmount()>0)lines.add(Component.literal(s.fluidName()+" · "+s.fluidAmount()+" mB"));
        if(!s.entriesComplete())lines.add(tr("partial_contents_hint"));if(directCellRecovery())lines.add(tr("recover_disk_hint"));return lines;
    }
    /** UI guidance only: the server repeats every migration precondition atomically. */
    private Component migrationBlocker(MachineSnapshot s) {
        if (!s.cell().installed()) return Component.translatable("status.rematter.missing_cell");
        if (!s.cell().readable() || s.status().equals("unknown_schema")) return Component.translatable("status.rematter.unknown_schema");
        if (s.cell().types() > 0) return Component.translatable("status.rematter.migration_disk_not_empty");
        if (s.cell().capacity() < s.used()) return Component.translatable("status.rematter.migration_disk_capacity");
        if (!s.entriesComplete()) return tr("partial_contents_hint");
        if (s.entries().size() > s.cell().maxTypes()) return Component.translatable("status.rematter.migration_disk_types");
        if (s.entries().stream().anyMatch(e -> e.amount() != e.available())) return Component.translatable("status.rematter.migration_busy");
        return null;
    }
    private Component stockTooltip(MachineSnapshot.Entry e) { return Component.literal(e.name()+" · "+e.phase()+"\n"+number(e.amount())+" u stored\n"+number(e.available())+" u available\n"+e.id()+"\n"+tr("substance_details_hint").getString()); }
    private String symbol(MachineSnapshot.Entry e) {try{return ClientChemistry.current().definition(SubstanceId.parse(e.id())).map(d->d.symbol()).orElse("?");}catch(RuntimeException ignored){return "?";}}
    private static int phaseColor(String phase) {return switch(phase){case "liquid"->0xff367ba0;case "gas"->0xff806599;default->COPPER;};}
    private String dimensions(MachineSnapshot s){var v=s.structure();return v.width()+"×"+v.depth()+"×"+v.height();}
    private void text(GuiGraphicsExtractor g,String v,int x,int y,int width,int color){g.text(font,font.plainSubstrByWidth(v,width),x,y,color,false);}
    private ItemStack outputIcon(MachineSnapshot.Preview p){try{Identifier id=Identifier.parse(p.outputId());if(p.outputKind().equals("item")&&BuiltInRegistries.ITEM.containsKey(id))return new ItemStack(BuiltInRegistries.ITEM.getValue(id));if(p.outputKind().equals("fluid")&&BuiltInRegistries.FLUID.containsKey(id))return new ItemStack(BuiltInRegistries.FLUID.getValue(id).getBucket());}catch(RuntimeException ignored){}return ItemStack.EMPTY;}
    private static String number(long v){return String.format(Locale.ROOT,"%,d",v);}
    private static String compact(long v){if(v>=1_000_000_000_000L)return String.format(Locale.ROOT,"%.1e",(double)v);if(v>=1_000_000_000)return String.format(Locale.ROOT,"%.1fB",v/1e9);if(v>=1_000_000)return String.format(Locale.ROOT,"%.1fM",v/1e6);if(v>=10_000)return String.format(Locale.ROOT,"%.1fk",v/1000.0);return Long.toString(v);}
    private class ConsoleButton extends Button {
        ConsoleButton(Builder b){super(b);}
        void background(GuiGraphicsExtractor g,boolean selected){boolean hover=active&&isHoveredOrFocused();g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),selected||hover?CYAN:EDGE);g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,selected||hover?0xffd5e6de:PAPER);g.fill(getX()+2,getY()+1,getX()+getWidth()-2,getY()+2,HIGHLIGHT);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){background(g,false);g.text(font,getMessage(),getX()+(getWidth()-font.width(getMessage()))/2,getY()+(getHeight()-8)/2,active?INK:MUTED,false);}
    }
    private final class TabButton extends ConsoleButton {
        private final MachineMenu.View view;TabButton(Builder b,MachineMenu.View view){super(b);this.view=view;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){background(g,menu.view()==view);int x=getX()+6,y=getY()+6,c=active?INK:MUTED;
            if(view==MachineMenu.View.MAIN){if(passive()){for(int a=0;a<3;a++)for(int b=0;b<3;b++)g.fill(x+a*4,y+b*4,x+a*4+3,y+b*4+3,c);}else{g.fill(x,y+3,x+4,y+9,c);g.fill(x+5,y+5,x+11,y+7,c);g.fill(x+9,y+3,x+11,y+9,c);g.fill(x+11,y+4,x+13,y+8,c);}}
            else if(view==MachineMenu.View.UPGRADES){for(int a=0;a<2;a++){int q=y+a*6;g.fill(x+4,q,x+8,q+2,c);g.fill(x+2,q+2,x+10,q+4,c);g.fill(x,q+4,x+4,q+5,c);g.fill(x+8,q+4,x+12,q+5,c);}}
            else{g.fill(x+3,y,x+9,y+12,c);g.fill(x,y+3,x+12,y+9,c);g.fill(x+4,y+4,x+8,y+8,PAPER);}
            if(menu.view()==view)g.fill(getX(),getY()+3,getX()+2,getY()+getHeight()-3,CYAN);
        }
    }
    private final class StockButton extends ConsoleButton {
        private MachineSnapshot.Entry entry;StockButton(Builder b){super(b);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){if(entry==null)return;background(g,false);int color=phaseColor(entry.phase());g.fill(getX()+2,getY()+3,getX()+4,getY()+12,color);String symbol=font.plainSubstrByWidth(symbol(entry),20);g.text(font,symbol,getX()+(getWidth()-font.width(symbol))/2,getY()+4,color,false);String amount=font.plainSubstrByWidth(compact(entry.amount()),24);g.text(font,amount,getX()+(getWidth()-font.width(amount))/2,getY()+15,INK,false);}
    }
    /** Quantity details are a real accessible screen, never an inventory slot or an implicit withdrawal. */
    private final class StockDetailsScreen extends Screen {
        private final String id,phase;private int x,y;
        StockDetailsScreen(String id,String phase){super(tr("substance_details"));this.id=id;this.phase=phase;}
        @Override protected void init(){x=(width-244)/2;y=(height-170)/2;addRenderableWidget(Button.builder(tr("back_short"),b->onClose()).bounds(x+87,y+141,70,18).build(ConsoleButton::new));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=menu)onClose();}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){g.fill(x,y,x+244,y+170,EDGE);g.fill(x+1,y+1,x+243,y+169,PAPER);var entry=menu.snapshot().entries().stream().filter(e->e.id().equals(id)&&e.phase().equals(phase)).findFirst().orElse(null);String name=entry==null?id:entry.name();g.text(font,font.plainSubstrByWidth(name,222),x+11,y+12,INK,false);g.text(font,phase,x+11,y+29,phaseColor(phase),false);
            String stored=entry==null?(menu.snapshot().entriesComplete()?"0":"?"):number(entry.amount());String available=entry==null?(menu.snapshot().entriesComplete()?"0":"?"):number(entry.available());
            g.text(font,Component.translatable("screen.rematter.stock_stored",stored),x+11,y+51,INK,false);g.text(font,Component.translatable("screen.rematter.stock_available",available),x+11,y+68,INK,false);
            g.text(font,tr("quantity_not_item"),x+11,y+89,MUTED,false);g.text(font,font.plainSubstrByWidth(id,222),x+11,y+106,MUTED,false);if(!menu.snapshot().entriesComplete())g.text(font,tr("partial_contents_hint"),x+11,y+122,COPPER,false);super.extractRenderState(g,mx,my,partial);
        }
    }
}
