package dev.latrox.rematter.client;

import com.google.gson.*;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Finite, explicitly confirmed Primal conversion. Source and payment slots remain real container slots. */
final class PrimalConverterPanel {
    private record Target(String id,long primal,long energy,int ticks) {}
    private final MachineMenu menu;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final int left,top,width,height;
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private List<Target> targets=List.of();
    private JsonObject extras=new JsonObject(),lastSource;
    private DarkButton previous,next,target,load,minus,plus,action;
    private boolean visible=true,confirming,awaiting,selectionRejected;
    private String selected="",requestedTarget="",pendingAction="";
    private long amount=1_000,requestedAmount=1_000,pendingOutput;
    private double wheel;

    PrimalConverterPanel(MachineMenu menu,Minecraft minecraft,Font font,Consumer<AbstractWidget> addWidget,
                         int left,int top,int width,int height){
        this.menu=menu;this.font=font;this.addWidget=addWidget;this.left=left;this.top=top;
        this.width=Math.min(180,width);this.height=height;
        if(this.width<176||height<100)throw new IllegalArgumentException("Converter content needs 176 x 100 pixels");
    }
    void initWidgets(){
        widgets.clear();
        previous=icon(UiIcon.PREVIOUS,tr("previous_name"),tr("target_hint"),left,top,18,18,b->cycle(-1));
        target=button(tr("choose"),tr("target_hint"),left+22,top,width-44,18,b->cycle(1));
        next=icon(UiIcon.NEXT,tr("next_name"),tr("target_hint"),left+width-18,top,18,18,b->cycle(1));
        // Source slot is at window25/43, i.e. panel17/23. Never paint or place a widget over it.
        load=icon(UiIcon.LOAD,tr("load"),tr("load_hint"),left+42,top+23,18,18,b->send(MachineActionPayload.Action.LOAD_PRIMAL,"",0,"LOAD"));
        minus=icon(UiIcon.MINUS,tr("less_name"),tr("less"),left,top+height-54,18,18,b->changeAmount(-250));
        plus=icon(UiIcon.PLUS,tr("more_name"),tr("more"),left+width-82,top+height-54,18,18,b->changeAmount(250));
        action=button(tr("start"),tr("start_hint"),left+width-60,top+height-54,60,18,b->{
            if(converting()){confirming=false;send(MachineActionPayload.Action.CONVERT_CANCEL,"",0,"CANCEL");return;}
            if(!confirming){confirming=true;updateWidgets();return;}
            confirming=false;send(MachineActionPayload.Action.CONVERT_START,"",0,"START");
        });
        load.setIcon(UiIcon.LOAD);action.setIcon(UiIcon.START);target.setIcon(UiIcon.TARGET);
        tick();
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
            extras=source;lastSource=source;
            var values=new ArrayList<Target>();
            for(var entry:array(source,"targets"))if(entry.isJsonObject()){
                var row=entry.getAsJsonObject();String id=text(row,"id","");
                if(!id.isEmpty())values.add(new Target(id,number(row,"primal",0),number(row,"energy",0),integer(row,"ticks",0)));
            }
            values.sort(Comparator.comparing(value->name(value.id).getString(),String.CASE_INSENSITIVE_ORDER));targets=List.copyOf(values);
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
                else if(pendingAction.equals("SELECT")&&targets.stream().noneMatch(value->value.id.equals(requestedTarget))){awaiting=false;selectionRejected=true;pendingAction="";}
            }
            if(converting()||pendingOutput>0)confirming=false;
        }
        updateWidgets();
    }
    private Target selectedTarget(){return targets.stream().filter(value->value.id.equals(selected)).findFirst().orElse(null);}
    private boolean converting(){return flag(extras,"converting",false);}
    private void cycle(int direction){
        if(targets.isEmpty()||awaiting||converting()||pendingOutput>0)return;
        int current=-1;for(int i=0;i<targets.size();i++)if(targets.get(i).id.equals(selected)){current=i;break;}
        int next=current<0?(direction<0?targets.size()-1:0):Math.floorMod(current+direction,targets.size());
        select(targets.get(next).id,amount);
    }
    private void changeAmount(long delta){
        if(awaiting||converting()||pendingOutput>0)return;
        String id=selected.isEmpty()?(targets.isEmpty()?"":targets.getFirst().id):selected;
        if(id.isEmpty())return;
        long next=Math.clamp(amount+delta,250,8_000);if(next!=amount)select(id,next);
    }
    private void select(String id,long output){
        confirming=false;selectionRejected=false;requestedTarget=id;requestedAmount=output;
        if(id.equals(selected)&&output==amount){updateWidgets();return;}
        send(MachineActionPayload.Action.CONVERT_SELECT,id,output,"SELECT");
    }
    private void send(MachineActionPayload.Action type,String id,long amount,String pending){
        awaiting=!pending.equals("LOAD");pendingAction=awaiting?pending:"";
        ClientPacketDistributor.sendToServer(new MachineActionPayload(menu.containerId,type,id,amount));updateWidgets();
    }
    private void updateWidgets(){
        if(load==null)return;
        var quote=selectedTarget();boolean idle=!awaiting&&!converting()&&pendingOutput==0;
        set(previous,idle&&targets.size()>1);set(next,idle&&targets.size()>1);set(target,idle&&!targets.isEmpty());
        target.setMessage(awaiting&&pendingAction.equals("SELECT")?name(requestedTarget):quote==null?tr(targets.isEmpty()?"no_targets":"choose"):name(quote.id));
        target.setTooltip(Tooltip.create(quote==null?tr("target_hint"):name(quote.id).copy().append("\n").append(tr("known_target"))));
        set(load,!awaiting&&menu.getSlot(0).hasItem()&&number(extras,"primalCapacity",0)>number(extras,"primal",0));
        set(minus,idle&&amount>250&&!targets.isEmpty());set(plus,idle&&amount<8_000&&!targets.isEmpty());
        boolean affordable=quote!=null&&quote.primal>0&&quote.energy>0&&quote.ticks>0&&number(extras,"primal",0)>=quote.primal;
        set(action,!awaiting&&(converting()||idle&&affordable));
        action.setMessage(converting()?tr("cancel"):confirming?tr("confirm"):pendingOutput>0?tr("pending_button"):tr("start"));
        action.setIcon(converting()?UiIcon.STOP:confirming?UiIcon.CONFIRM:pendingOutput>0?UiIcon.TIME:UiIcon.START);
        action.setSelected(confirming);action.setAccent(UiPalette.PRIMAL);
        Component hint=converting()?tr("cancel_hint"):quote==null?tr("target_hint"):quoteTooltip(quote);
        action.setTooltip(Tooltip.create(hint));
    }
    private void set(AbstractWidget widget,boolean active){widget.visible=visible;widget.active=visible&&active;if(!visible)widget.setFocused(false);}
    void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY){
        if(!visible)return;
        boolean hasCell=number(extras,"primalCapacity",0)>0;
        ScreenChrome.icon(g,UiIcon.PRIMAL,left+64,top+24,hasCell?UiPalette.PRIMAL:UiPalette.MUTED);
        label(g,!hasCell?tr("insert_cell"):tr("reserve",compact(number(extras,"primal",0)),compact(number(extras,"primalCapacity",0))),left+80,top+28,width-80,hasCell?UiPalette.PRIMAL_LIGHT:UiPalette.MUTED);
        label(g,tr("output",exact(awaiting&&pendingAction.equals("SELECT")?requestedAmount:amount)),left+22,top+height-49,width-106,UiPalette.TEXT);
        var quote=selectedTarget();
        if(awaiting&&pendingAction.equals("SELECT"))label(g,tr("awaiting_quote"),left,top+height-30,width,UiPalette.MUTED);
        else if(quote!=null){
            int x=left,y=top+height-30;UiIcon[] icons={UiIcon.PRIMAL,UiIcon.ENERGY,UiIcon.TIME};String[] values={compact(quote.primal),compact(quote.energy),time(quote.ticks)};
            for(int i=0;i<icons.length;i++){ScreenChrome.icon(g,icons[i],x-2,y-4,i==0?UiPalette.PRIMAL:UiPalette.TEXT);g.text(font,values[i],x+14,y,i==0?UiPalette.PRIMAL_LIGHT:UiPalette.TEXT,false);x+=18+font.width(values[i]);}
        }
        else label(g,tr(targets.isEmpty()?"no_targets_hint":"choose_hint"),left,top+height-30,width,UiPalette.MUTED);
        if(converting()){
            int progress=Math.max(0,menu.progress().progress()),duration=Math.max(1,menu.progress().duration());
            ScreenChrome.inset(g,left,top+height-18,width,4);int fill=(int)Math.min(width-2,(long)(width-2)*progress/duration);
            if(fill>0)g.fill(left+1,top+height-17,left+1+fill,top+height-15,UiPalette.PRIMAL);
        }
        Component state=pendingOutput>0?tr("pending_output",exact(pendingOutput)):awaiting?tr("awaiting"):converting()?tr("running"):confirming?tr("confirm_output",exact(amount),quote==null?"":name(quote.id)):selectionRejected?tr("unavailable"):quote!=null&&number(extras,"primal",0)<quote.primal?tr("need_primal"):tr("finite");
        UiIcon lamp=pendingOutput>0?UiIcon.STATUS_WARNING:awaiting||confirming?UiIcon.STATUS_WAITING:converting()?UiIcon.STATUS_WORKING:selectionRejected||quote!=null&&number(extras,"primal",0)<quote.primal?UiIcon.STATUS_BLOCKED:UiIcon.STATUS_IDLE;
        ScreenChrome.icon(g,lamp,left-2,top+height-15,-1);
        label(g,state,left+16,top+height-11,width-16,pendingOutput>0||selectionRejected?UiPalette.WARNING:UiPalette.MUTED);
        if(inside(mouseX,mouseY,left,top+height-32,width,32)){
            var lines=new ArrayList<Component>();if(quote!=null&&!awaiting)lines.add(quoteTooltip(quote));lines.add(state);
            if(pendingOutput>0)lines.add(tr("pending_hint"));g.setComponentTooltipForNextFrame(font,lines,mouseX,mouseY);
        }
    }
    private Component quoteTooltip(Target quote){return name(quote.id).copy().append("\n").append(tr("exact_price",exact(quote.primal),exact(amount),exact(quote.energy),time(quote.ticks))).append("\n").append(tr("start_hint"));}
    boolean mouseScrolled(double x,double y,double dx,double dy){
        if(!visible||awaiting||converting()||pendingOutput>0||!inside(x,y,left,top+height-54,width-50,18)||!Double.isFinite(dy))return false;
        wheel+=dy;int steps=(int)wheel;if(steps!=0){wheel-=steps;changeAmount(Math.clamp((long)steps*250,-8_000,8_000));}return true;
    }
    boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){return false;}
    boolean mouseDragged(MouseButtonEvent event,double dx,double dy){return false;}
    boolean mouseReleased(MouseButtonEvent event){return false;}
    boolean handlesKey(KeyEvent event){if(visible&&confirming&&event.isEscape()){confirming=false;updateWidgets();return true;}return false;}
    private void label(GuiGraphicsExtractor g,Component text,int x,int y,int width,int color){ScreenChrome.text(g,font,text.getString(),x,y,width,color);}
    private static Component name(String id){return SubstanceNames.substance(id,"solid",true);}
    private static String exact(long value){return String.format(Locale.US,"%,d",value);}
    private static String compact(long value){if(value>=1_000_000)return decimal(value,1_000_000)+"M";if(value>=1_000)return decimal(value,1_000)+"k";return Long.toString(value);}
    private static String decimal(long value,long unit){long tenths=value/(unit/10);return tenths%10==0?Long.toString(tenths/10):tenths/10+"."+tenths%10;}
    private static String time(int ticks){long seconds=((long)Math.max(0,ticks)+19)/20;return seconds>=60?seconds/60+"m"+(seconds%60==0?"":seconds%60+"s"):seconds+"s";}
    private static JsonArray array(JsonObject value,String key){return value.has(key)&&value.get(key).isJsonArray()?value.getAsJsonArray(key):new JsonArray();}
    private static String text(JsonObject value,String key,String fallback){try{return value.has(key)&&value.get(key).isJsonPrimitive()?value.get(key).getAsString():fallback;}catch(RuntimeException invalid){return fallback;}}
    private static long number(JsonObject value,String key,long fallback){try{return value.has(key)&&value.get(key).isJsonPrimitive()?Math.max(0,value.get(key).getAsLong()):fallback;}catch(RuntimeException invalid){return fallback;}}
    private static int integer(JsonObject value,String key,int fallback){return (int)Math.min(Integer.MAX_VALUE,number(value,key,fallback));}
    private static boolean flag(JsonObject value,String key,boolean fallback){return value.has(key)&&value.get(key).isJsonPrimitive()&&value.get(key).getAsJsonPrimitive().isBoolean()?value.get(key).getAsBoolean():fallback;}
    private static boolean inside(double x,double y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    private static Component tr(String key,Object...args){return Component.translatable("screen.rematter.converter."+key,args);}
}
