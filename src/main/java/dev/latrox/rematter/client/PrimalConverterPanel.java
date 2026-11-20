package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.PrimalConverterView.*;
import com.google.gson.JsonObject;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.network.MachineActionPayload;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A finite conversion, with a searchable target, visible payment source and real destination Cell. */
final class PrimalConverterPanel {
    private final MachineMenu menu;
    private final Minecraft minecraft;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final int left,top,width,height;
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private List<Target> targets=List.of();
    private JsonObject extras=new JsonObject(),lastSource;
    private DarkButton target,load,minus,plus,action;
    private boolean visible=true,confirming,awaiting,selectionRejected;
    private String selected="",requestedTarget="",pendingAction="";
    private long amount=1_000,requestedAmount=1_000,pendingOutput;
    private int awaitingTicks;
    private double wheel;

    PrimalConverterPanel(MachineMenu menu,Minecraft minecraft,Font font,Consumer<AbstractWidget> addWidget,
                         int left,int top,int width,int height){
        this.menu=menu;this.minecraft=minecraft;this.font=font;this.addWidget=addWidget;this.left=left;this.top=top;
        this.width=Math.min(180,width);this.height=height;
        if(this.width<176||height<100)throw new IllegalArgumentException("Converter content needs 176 x 100 pixels");
    }
    void initWidgets(){
        widgets.clear();
        target=button(tr("choose"),tr("target_hint"),left,top,width,18,b->minecraft.pushGuiLayer(new PrimalTargetScreen(menu,id->select(id,amount))));
        // Source is the real slot at window25/43. The separate payment Cell remains at window195/43.
        load=icon(UiIcon.LOAD,tr("load"),tr("load_hint"),left+42,top+23,18,18,b->send(MachineActionPayload.Action.LOAD_PRIMAL,"",0,"LOAD"));
        minus=icon(UiIcon.MINUS,tr("less_name"),tr("less"),left,top+80,18,18,b->changeAmount(-250));
        plus=icon(UiIcon.PLUS,tr("more_name"),tr("more"),left+width-82,top+80,18,18,b->changeAmount(250));
        action=button(tr("start"),tr("start_hint"),left+width-60,top+80,60,18,b->{
            if(converting()){confirming=false;send(MachineActionPayload.Action.CONVERT_CANCEL,"",0,"CANCEL");return;}
            if(!confirming){confirming=true;updateWidgets();return;}
            confirming=false;send(MachineActionPayload.Action.CONVERT_START,"",0,"START");
        });
        action.setIcon(UiIcon.START);target.setIcon(UiIcon.SEARCH);tick();
    }
    private DarkButton button(Component name,Component hint,int x,int y,int width,int height,net.minecraft.client.gui.components.Button.OnPress press){
        var button=DarkButton.create(name,hint,x,y,width,height,press);button.setAccent(UiPalette.machineAccent(menu.kind()));widgets.add(button);addWidget.accept(button);return button;
    }
    private DarkButton icon(UiIcon icon,Component name,Component hint,int x,int y,int width,int height,net.minecraft.client.gui.components.Button.OnPress press){
        var button=DarkButton.icon(icon,name,hint,x,y,width,height,press);button.setAccent(UiPalette.machineAccent(menu.kind()));widgets.add(button);addWidget.accept(button);return button;
    }
    void setVisible(boolean value){visible=value;if(!value)confirming=false;updateWidgets();}
    void tick(){
        var source=menu.extras();
        if(source!=lastSource){
            extras=source;lastSource=source;targets=PrimalConverterView.targets(source);
            selected=text(source,"conversionTarget","");amount=Math.clamp(number(source,"conversionAmount",1_000),250,8_000);
            pendingOutput=0;
            for(var entry:array(source,"conversionClaims"))if(entry.isJsonObject())pendingOutput=Math.addExact(pendingOutput,number(entry.getAsJsonObject(),"remaining",0));
            if(awaiting){
                boolean answered=switch(pendingAction){
                    case "SELECT"->selected.equals(requestedTarget)&&amount==requestedAmount;
                    case "START"->converting()||pendingOutput>0;
                    case "CANCEL"->!converting();
                    default->true;
                };
                if(answered){awaiting=false;pendingAction="";}
                else if(pendingAction.equals("SELECT")&&targets.stream().noneMatch(value->value.id().equals(requestedTarget)&&value.known())){awaiting=false;selectionRejected=true;pendingAction="";}
            }
            if(converting()||pendingOutput>0)confirming=false;
        }
        // A rejected or disconnected request may not change the server snapshot. Keep the panel usable.
        if(awaiting&&++awaitingTicks>200){awaiting=false;selectionRejected=true;pendingAction="";}
        updateWidgets();
    }
    private Target selectedTarget(){return targets.stream().filter(value->value.id().equals(selected)&&value.known()).findFirst().orElse(null);}
    private boolean converting(){return flag(extras,"converting",false);}
    private void changeAmount(long delta){
        if(awaiting||converting()||pendingOutput>0)return;
        String id=selected.isEmpty()?targets.stream().filter(Target::known).map(Target::id).findFirst().orElse(""):selected;
        if(id.isEmpty())return;long next=Math.clamp(amount+delta,250,8_000);if(next!=amount)select(id,next);
    }
    private void select(String id,long output){
        if(awaiting||converting()||pendingOutput>0)return;
        confirming=false;selectionRejected=false;requestedTarget=id;requestedAmount=output;
        if(id.equals(selected)&&output==amount){updateWidgets();return;}
        send(MachineActionPayload.Action.CONVERT_SELECT,id,output,"SELECT");
    }
    private void send(MachineActionPayload.Action type,String id,long amount,String pending){
        awaiting=!pending.equals("LOAD");awaitingTicks=0;pendingAction=awaiting?pending:"";
        ClientPacketDistributor.sendToServer(new MachineActionPayload(menu.containerId,type,id,amount));updateWidgets();
    }
    private void updateWidgets(){
        if(load==null)return;var quote=selectedTarget();var destination=destination(extras);boolean idle=!awaiting&&!converting()&&pendingOutput==0;
        set(target,idle&&!targets.isEmpty());
        target.setMessage(awaiting&&pendingAction.equals("SELECT")?Component.literal(PrimalTargetScreen.label(requestedTarget)):quote==null?tr("choose"):Component.literal(PrimalTargetScreen.label(quote.id())));
        target.setTooltip(Tooltip.create(quote==null?tr("target_hint"):quoteTooltip(quote).copy().append("\n").append(tr("target_hint"))));
        set(load,!awaiting&&menu.getSlot(0).hasItem()&&number(extras,"primalCapacity",0)>number(extras,"primal",0));
        set(minus,idle&&amount>250&&quote!=null);set(plus,idle&&amount<8_000&&quote!=null);
        boolean affordable=quote!=null&&number(extras,"primal",0)>=quote.primal()&&menu.progress().energy()>=quote.energy();
        set(action,!awaiting&&(converting()||idle&&affordable&&destination.ready()));
        action.setMessage(converting()?tr("cancel"):confirming?tr("confirm"):pendingOutput>0?tr("pending_button"):tr("start"));
        action.setIcon(converting()?UiIcon.STOP:confirming?UiIcon.CONFIRM:pendingOutput>0?UiIcon.TIME:UiIcon.START);
        action.setSelected(confirming);action.setAccent(UiPalette.PRIMAL);
        Component hint=converting()?tr("cancel_hint"):quote==null?tr("target_hint"):quoteTooltip(quote).copy().append("\n").append(status(quote,destination));
        action.setTooltip(Tooltip.create(hint));
    }
    private void set(AbstractWidget widget,boolean active){widget.visible=visible;widget.active=visible&&active;if(!visible)widget.setFocused(false);}
    void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY){
        if(!visible)return;boolean hasCell=number(extras,"primalCapacity",0)>0;var destination=destination(extras);var quote=selectedTarget();

        label(g,tr("payment_cell"),left+64,top+24,width-64,UiPalette.MUTED);
        label(g,!hasCell?tr("insert_cell"):tr("reserve",compact(number(extras,"primal",0)),compact(number(extras,"primalCapacity",0))),left+64,top+34,width-64,hasCell?UiPalette.PRIMAL_LIGHT:UiPalette.WARNING);
        // Fixed left-to-right vocabulary. Actual installed source/payment slots remain visible above.
        ScreenChrome.icon(g,UiIcon.SLOT_PRIMAL_CELL,left,top+45,hasCell?UiPalette.PRIMAL:UiPalette.MUTED);
        ScreenChrome.icon(g,UiIcon.ARROW_RIGHT,left+21,top+45,UiPalette.MUTED);
        ScreenChrome.icon(g,UiIcon.MACHINE,left+42,top+45,UiPalette.PRIMAL);
        ScreenChrome.icon(g,UiIcon.ARROW_RIGHT,left+63,top+45,UiPalette.MUTED);
        ScreenChrome.icon(g,destination.ready()?UiIcon.STOCK:UiIcon.NETWORK_OFFLINE,left+84,top+45,destination.ready()?UiPalette.machineAccent(dev.latrox.rematter.content.MachineKind.DRIVE):UiPalette.WARNING);
        if(destination.ready()){
            label(g,tr("drive_at",destination.x(),destination.y(),destination.z()),left+102,top+45,width-102,UiPalette.TEXT);
            label(g,tr("bay_free",destination.bay(),compact(destination.free())),left+102,top+55,width-102,UiPalette.MUTED);
        }else ScreenChrome.twoLines(g,font,destinationMessage(destination).getString(),left+102,top+45,width-102,UiPalette.WARNING);
        if(pendingOutput>0)label(g,tr("pending_output",exact(pendingOutput)),left,top+67,width-18,UiPalette.WARNING);
        else if(awaiting&&pendingAction.equals("SELECT"))label(g,tr("awaiting_quote"),left,top+67,width-18,UiPalette.MUTED);
        else if(quote!=null){
            UiIcon[] icons={UiIcon.PRIMAL,UiIcon.ENERGY,UiIcon.TIME};String[] values={compact(quote.primal()),compact(quote.energy()),time(quote.ticks())};
            for(int i=0;i<3;i++){int x=left+i*54;ScreenChrome.icon(g,icons[i],x-2,top+63,i==0?UiPalette.PRIMAL:UiPalette.TEXT);label(g,Component.literal(values[i]),x+14,top+67,36,i==0?UiPalette.PRIMAL_LIGHT:UiPalette.TEXT);}
        }else label(g,tr("choose_hint"),left,top+67,width-18,UiPalette.MUTED);
        UiIcon lamp=pendingOutput>0?UiIcon.STATUS_WARNING:awaiting||confirming?UiIcon.STATUS_WAITING:converting()?UiIcon.STATUS_WORKING:quote==null?UiIcon.STATUS_IDLE:!destination.ready()||number(extras,"primal",0)<quote.primal()||menu.progress().energy()<quote.energy()?UiIcon.STATUS_BLOCKED:UiIcon.STATUS_IDLE;
        ScreenChrome.icon(g,lamp,left+width-18,top+63,-1);
        label(g,tr("output",exact(awaiting&&pendingAction.equals("SELECT")?requestedAmount:amount)),left+22,top+85,width-106,UiPalette.TEXT);
        if(converting()){
            int progress=Math.max(0,menu.progress().progress()),duration=Math.max(1,menu.progress().duration());ScreenChrome.inset(g,left,top+99,width,3);
            int fill=(int)Math.min(width-2,(long)(width-2)*progress/duration);if(fill>0)g.fill(left+1,top+100,left+1+fill,top+101,UiPalette.PRIMAL);
        }
        if(inside(mouseX,mouseY,left,top+43,width,20)){
            var lines=new ArrayList<Component>();lines.add(tr("flow_hint"));
            if(destination.ready()){
                lines.add(tr("drive_at",destination.x(),destination.y(),destination.z()));lines.add(tr("destination_cell",destination.bay(),SubstanceNames.item(destination.item())));
                lines.add(tr("destination_space",exact(destination.used()),exact(destination.capacity()),exact(destination.free())));lines.add(tr("destination_hint"));
            }else{lines.add(destinationMessage(destination));lines.add(tr("destination_help"));}
            g.setComponentTooltipForNextFrame(font,lines,mouseX,mouseY);
        }
        if(inside(mouseX,mouseY,left,top+63,width,16)){
            var lines=new ArrayList<Component>();if(quote!=null&&!awaiting)lines.add(quoteTooltip(quote));lines.add(status(quote,destination));if(pendingOutput>0)lines.add(tr("pending_hint"));
            g.setComponentTooltipForNextFrame(font,lines,mouseX,mouseY);
        }
    }
    private Component status(Target quote,Destination destination){
        return pendingOutput>0?tr("pending_output",exact(pendingOutput)):awaiting?tr("awaiting"):converting()?tr("running"):confirming?tr("confirm_output",exact(amount),quote==null?"":PrimalTargetScreen.name(quote.id())):selectionRejected?tr("unavailable"):quote==null?tr("choose_hint"):!destination.ready()?destinationMessage(destination):number(extras,"primal",0)<quote.primal()?tr("need_primal"):menu.progress().energy()<quote.energy()?tr("need_energy"):tr("finite");
    }
    private Component quoteTooltip(Target quote){return Component.literal(PrimalTargetScreen.label(quote.id())).append("\n").append(PrimalTargetScreen.ratio(quote)).append("\n").append(tr("exact_price",exact(quote.primal()),exact(amount),exact(quote.energy()),time(quote.ticks()))).append("\n").append(tr("energy_reserve",exact(menu.progress().energy()))).append("\n").append(tr("start_hint"));}
    private static Component destinationMessage(Destination destination){return switch(destination.status()){
        case "ready"->tr("destination_ready");case "choose_target"->tr("destination_choose");case "no_compatible_cell"->tr("destination_full");case "unknown_schema"->tr("destination_locked");case "missing_cell"->tr("destination_missing_cell");default->tr("destination_none");};}
    boolean mouseScrolled(double x,double y,double dx,double dy){
        if(!visible||awaiting||converting()||pendingOutput>0||!inside(x,y,left,top+80,width-60,18)||!Double.isFinite(dy))return false;
        wheel+=dy;int steps=(int)wheel;if(steps!=0){wheel-=steps;changeAmount(Math.clamp((long)steps*250,-8_000,8_000));}return true;
    }
    boolean handlesKey(KeyEvent event){if(visible&&confirming&&event.isEscape()){confirming=false;updateWidgets();return true;}return false;}
    private void label(GuiGraphicsExtractor g,Component text,int x,int y,int width,int color){ScreenChrome.text(g,font,text.getString(),x,y,width,color);}
    private static String exact(long value){return String.format(Locale.US,"%,d",value);}
    private static String compact(long value){if(value>=1_000_000)return decimal(value,1_000_000)+"M";if(value>=1_000)return decimal(value,1_000)+"k";return Long.toString(value);}
    private static String decimal(long value,long unit){long tenths=value/(unit/10);return tenths%10==0?Long.toString(tenths/10):tenths/10+"."+tenths%10;}
    private static String time(int ticks){long seconds=((long)Math.max(0,ticks)+19)/20;return seconds>=60?seconds/60+"m"+(seconds%60==0?"":seconds%60+"s"):seconds+"s";}
    private static boolean inside(double x,double y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    private static Component tr(String key,Object...args){return Component.translatable("screen.rematter.converter."+key,args);}
}
