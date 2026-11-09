package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSettings;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.network.MachineConfigurationPayload;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Stock targets and explicit gas disposal on the existing authorized menu. */
final class MachineSettingsScreen extends Screen {
    private final MachineMenu menu;
    private final List<MachineSnapshot.Amount> products;
    private final Set<String> vented=new HashSet<>();
    private final List<Button> gasButtons=new ArrayList<>();
    private final List<String> gases;
    private EditBox target;
    private Button apply,product;
    private int selected,left,top,panelWidth,panelHeight,gasPage;
    MachineSettingsScreen(MachineMenu menu) {
        super(tr("production_settings"));this.menu=menu;
        products=menu.snapshot().preview().outputs();
        vented.addAll(menu.settings().settings().ventedGases());
        var gasIds=new LinkedHashSet<String>(vented);
        products.stream().filter(p->p.phase().equals("gas")).forEach(p->gasIds.add(p.id()));
        gases=List.copyOf(gasIds);
        var stored=menu.settings().settings();
        for(int i=0;i<products.size();i++)if(products.get(i).id().equals(stored.targetSubstance())&&products.get(i).phase().equals(stored.targetPhase()))selected=i;
    }
    private static Component tr(String key,Object...args) {return Component.translatable("screen.rematter."+key,args);}
    private boolean physical() {return menu.kind().synthesis()||dev.latrox.rematter.content.preparation.PreparationTier.isPreparation(menu.kind());}
    @Override protected void init() {
        panelWidth=Math.min(340,width-12);panelHeight=Math.min(262,height-12);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        String draft=target==null?Long.toString(menu.settings().settings().target()):target.getValue();
        target=addRenderableWidget(new EditBox(font,left+12,top+58,panelWidth-112,20,tr("stock_target")));
        target.setMaxLength(13);target.setValue(draft);
        target.setFilter(s->s.matches("[0-9]*"));
        apply=addRenderableWidget(Button.builder(tr("apply"),b->save()).bounds(left+panelWidth-92,top+58,80,20).build());
        target.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("target_limit",Quantities.number(physicalLimit()))));
        target.setResponder(value->apply.setMessage(tr("apply")));
        product=addRenderableWidget(Button.builder(productLabel(),b->{if(!products.isEmpty())selected=(selected+1)%products.size();product.setMessage(productLabel());})
                .bounds(left+12,top+84,panelWidth-24,20).build());
        product.active=!physical()&&!products.isEmpty();
        gasButtons.clear();
        int perPage=Math.max(1,Math.min(3,(panelHeight-188)/22));
        gasPage=Math.clamp(gasPage,0,Math.max(0,(gases.size()-1)/perPage));
        for(int i=0;i<perPage&&gasPage*perPage+i<gases.size();i++) {
            String gas=gases.get(gasPage*perPage+i);
            var button=addRenderableWidget(Button.builder(ventLabel(gas),b-> {
                if(vented.contains(gas)) {vented.remove(gas);b.setMessage(ventLabel(gas));return;}
                minecraft.pushGuiLayer(new ConfirmScreen(accepted->{
                    minecraft.popGuiLayer();if(accepted)vented.add(gas);b.setMessage(ventLabel(gas));
                },tr("vent_title"),tr("vent_warning"),CommonComponents.GUI_YES,CommonComponents.GUI_CANCEL));
            }).bounds(left+12,top+122+i*22,panelWidth-24,20).build());
            button.active=menu.settings().mayVent();gasButtons.add(button);
        }
        if(gases.size()>perPage) {
            int pagerY=top+122+perPage*22;
            addRenderableWidget(Button.builder(Component.translatable("screen.rematter.recipes.previous"),b->{gasPage--;rebuildWidgets();})
                    .bounds(left+12,pagerY,70,18).build()).active=gasPage>0;
            addRenderableWidget(Button.builder(Component.translatable("screen.rematter.recipes.next"),b->{gasPage++;rebuildWidgets();})
                    .bounds(left+panelWidth-82,pagerY,70,18).build()).active=(gasPage+1)*perPage<gases.size();
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK,b->onClose()).bounds(left+(panelWidth-80)/2,top+panelHeight-26,80,20).build());
    }
    private Component productLabel() {
        if(physical())return tr(menu.snapshot().preview().outputKind().equals("fluid")?"target_local_fluid":"target_local_items");
        if(products.isEmpty())return tr("target_choose_recipe");
        var p=products.get(selected);return tr("target_network",SubstanceNames.substance(p.id(),p.phase(),p.known()).copy().append(" · ").append(SubstanceNames.phase(p.phase())));
    }
    private Component ventLabel(String gas) {return tr("vent_setting",SubstanceNames.substance(gas,"gas",true),tr(vented.contains(gas)?"on":"off"));}
    private void save() {
        try {
            long amount=Long.parseLong(target.getValue());String id="",phase="";
            if(amount>physicalLimit())throw new IllegalArgumentException("Target does not fit this scope");
            if(!physical()&&!products.isEmpty()) {id=products.get(selected).id();phase=products.get(selected).phase();}
            var settings=new MachineSettings(amount,id,phase,vented);
            ClientPacketDistributor.sendToServer(new MachineConfigurationPayload(menu.containerId,settings));onClose();
        } catch(IllegalArgumentException invalid) {apply.setMessage(tr("invalid_target"));}
    }
    private long physicalLimit() {
        if(!physical())return MachineSettings.MAX_TARGET;
        return menu.snapshot().preview().outputKind().equals("fluid")?dev.latrox.rematter.content.MatterLimits.MACHINE_FLUID:64;
    }
    @Override public void tick() {if(minecraft.player==null||minecraft.player.containerMenu!=menu)onClose();}
    @Override public boolean isPauseScreen() {return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial) {
        ScreenChrome.panel(g,left,top,panelWidth,panelHeight);
        g.text(font,title,left+12,top+12,UiPalette.TEXT,false);
        ScreenChrome.twoLines(g,font,tr("target_help").getString(),left+12,top+29,panelWidth-24,UiPalette.MUTED);
        if(gases.isEmpty())g.text(font,tr("no_gas_outputs"),left+12,top+126,UiPalette.MUTED,false);
        var settings=menu.settings();
        if(settings.settings().target()>0) {
            String count=settings.targetStock()<0?"?":Quantities.number(settings.targetStock());
            g.text(font,ScreenChrome.fit(font,tr(physical()?"target_local_count":"target_network_count",count,Quantities.number(settings.settings().target())).getString(),panelWidth-24),
                    left+12,top+108,UiPalette.ACCENT,false);
        }
        g.text(font,tr("parallel_state",settings.running(),settings.capacity()),left+12,top+panelHeight-51,UiPalette.MUTED,false);
        g.text(font,tr("vented_total",Quantities.number(settings.ventedTotal())),left+12,top+panelHeight-40,UiPalette.MUTED,false);
        super.extractRenderState(g,mx,my,partial);
    }
}
