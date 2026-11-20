package dev.latrox.rematter.client;

import com.google.gson.*;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
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

/** The Analyzer's public research view. Discovery targets are never inferred or exposed before completion. */
final class ResearchPanel {
    enum Section { FAMILIES, DISCOVERY, CURRENT }
    private record Substance(String id,String phase,boolean known) {}
    private record Family(String id,String name,List<String> probes,List<Substance> substances,boolean known,
                          boolean canDerive,String learningPath,long primal,int discoveryTier) {}
    private final MachineMenu menu;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final int left,top,width,height;
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final EnumMap<Section,DarkButton> tabs=new EnumMap<>(Section.class);
    private final List<DarkButton> rows=new ArrayList<>(),tiers=new ArrayList<>();
    private final ScrollModel scroll=new ScrollModel(3);
    private final ScrollBar bar=new ScrollBar(scroll);
    private List<Family> families=List.of(),filtered=List.of();
    private JsonObject extras=new JsonObject(),session=new JsonObject(),lastSource;
    private DarkButton known,unknown,load,derive,discover,pause,refund;
    private Section section=Section.FAMILIES;
    private boolean showKnown,visible=true,awaiting;
    private String selected="",lastSession="";
    private int tier=1;
    private final int[] candidates=new int[3];

    ResearchPanel(MachineMenu menu,Minecraft minecraft,Font font,Consumer<AbstractWidget> addWidget,
                  int left,int top,int width,int height) {
        this.menu=menu;this.font=font;this.addWidget=addWidget;this.left=left;this.top=top;this.width=width;this.height=height;
        if(width<180||height<188)throw new IllegalArgumentException("Research content needs at least 180 x 188 pixels");
    }
    void initWidgets() {
        widgets.clear();tabs.clear();rows.clear();tiers.clear();
        int tabWidth=(width-8)/3;
        for(var value:Section.values()){
            var icon=switch(value){case FAMILIES->UiIcon.FAMILIES;case DISCOVERY->UiIcon.DISCOVER;case CURRENT->UiIcon.PROGRESS;};
            tabs.put(value,icon(icon,tr("tab."+value.name().toLowerCase(Locale.ROOT)),tr("tab_hint."+value.name().toLowerCase(Locale.ROOT)),left+value.ordinal()*28,top,24,18,b->setSection(value)));
        }
        load=icon(UiIcon.LOAD,tr("load"),tr("load_hint"),left+width-18,top+22,18,16,b->send(MachineActionPayload.Action.LOAD_PRIMAL,"",0,false));
        known=icon(UiIcon.KNOWN,tr("known"),tr("known_hint"),left,top+42,18,16,b->{showKnown=true;selected="";scroll.setOffset(0);refilter();});
        unknown=icon(UiIcon.UNKNOWN,tr("unknown"),tr("unknown_hint"),left+22,top+42,18,16,b->{showKnown=false;selected="";scroll.setOffset(0);refilter();});
        for(int i=0;i<3;i++){
            final int row=i;
            rows.add(button(tr("empty"),tr("family_hint"),left,top+62+i*18,width-10,16,b->{int index=scroll.offset()+row;if(index<filtered.size()){selected=filtered.get(index).id;scroll.select(index);updateWidgets();}}));
        }
        bar.setBounds(left+width-6,top+62,6,52);
        derive=button(tr("derive"),tr("derive_hint"),left,top+height-18,width,18,b->{var family=selectedFamily();if(family!=null)send(MachineActionPayload.Action.DERIVE,family.id,0,true);});
        for(int i=0;i<3;i++){
            final int chosen=i+1;
            int x=left+i*(tabWidth+4);int w=i==2?left+width-x:tabWidth;
            tiers.add(button(tr("tier",roman(chosen)),tr("tier_hint",roman(chosen)),x,top+42,w,18,b->{tier=chosen;updateWidgets();}));
        }
        discover=button(tr("start_discovery"),tr("discovery_hint"),left,top+height-18,width,18,b->send(MachineActionPayload.Action.DISCOVER,"",tier,true));
        pause=icon(UiIcon.PAUSE,tr("pause"),tr("pause_hint"),left,top+height-18,18,18,b->{
            var action=text(session,"state","").equals("ACTIVE")?MachineActionPayload.Action.RESEARCH_PAUSE:MachineActionPayload.Action.RESEARCH_RESUME;
            send(action,text(session,"id",""),0,false);
        });
        refund=icon(UiIcon.REFUND,tr("refund"),tr("refund_hint"),left+22,top+height-18,18,18,b->send(MachineActionPayload.Action.RESEARCH_REFUND,text(session,"id",""),0,false));
        load.setIcon(UiIcon.LOAD);known.setIcon(UiIcon.KNOWN);unknown.setIcon(UiIcon.UNKNOWN);
        derive.setIcon(UiIcon.DERIVE);discover.setIcon(UiIcon.DISCOVER);pause.setIcon(UiIcon.PAUSE);refund.setIcon(UiIcon.REFUND);
        tick();
    }
    private DarkButton button(Component label,Component hint,int x,int y,int w,int h,net.minecraft.client.gui.components.Button.OnPress action){
        var value=DarkButton.create(label,hint,x,y,w,h,action);value.setAccent(UiPalette.machineAccent(menu.kind()));widgets.add(value);addWidget.accept(value);return value;
    }
    private DarkButton icon(UiIcon icon,Component label,Component hint,int x,int y,int w,int h,net.minecraft.client.gui.components.Button.OnPress action){
        var value=DarkButton.icon(icon,label,hint,x,y,w,h,action);value.setAccent(UiPalette.machineAccent(menu.kind()));widgets.add(value);addWidget.accept(value);return value;
    }
    void setVisible(boolean value){visible=value;bar.mouseReleased();updateWidgets();}
    void setSection(Section value){section=value;bar.mouseReleased();updateWidgets();}
    Section section(){return section;}
    void tick(){
        var source=menu.extras();
        if(source!=lastSource){
            extras=source;lastSource=source;awaiting=false;session=object(source,"research");
            var values=new ArrayList<Family>();
            for(var entry:array(source,"materials"))if(entry.isJsonObject()){
                var json=entry.getAsJsonObject();var substances=new ArrayList<Substance>();
                for(var part:array(json,"substances")){
                    if(part.isJsonPrimitive())substances.add(new Substance(part.getAsString(),"",false));
                    else if(part.isJsonObject()){
                        var substance=part.getAsJsonObject();substances.add(new Substance(identifier(substance.get("id")),text(substance,"phase",""),flag(substance,"known",false)));
                    }
                }
                String id=text(json,"id","");if(id.isEmpty())continue;
                values.add(new Family(id,text(json,"name",id),strings(json,"probeIds"),List.copyOf(substances),flag(json,"known",false),flag(json,"canDerive",false),text(json,"learningPath",""),number(json,"derivePrimal",0),integer(json,"discoveryTier",0)));
            }
            values.sort(Comparator.comparing(Family::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Family::id));families=List.copyOf(values);
            var counts=array(source,"discovery");for(int i=0;i<3;i++)candidates[i]=i<counts.size()&&counts.get(i).isJsonPrimitive()?Math.max(0,counts.get(i).getAsInt()):0;
            String id=text(session,"id","");if(!id.isEmpty()&&!id.equals(lastSession)){lastSession=id;section=Section.CURRENT;}
            refilter();
        }
        updateWidgets();
    }
    private void refilter(){
        filtered=families.stream().filter(value->value.known==showKnown).toList();scroll.setSize(filtered.size());
        if(filtered.stream().noneMatch(value->value.id.equals(selected)))selected=filtered.isEmpty()?"":filtered.getFirst().id;
        updateWidgets();
    }
    private Family selectedFamily(){return filtered.stream().filter(value->value.id.equals(selected)).findFirst().orElse(null);}
    private void updateWidgets(){
        if(load==null)return;
        for(var widget:widgets){widget.visible=false;widget.active=false;}
        for(var entry:tabs.entrySet()){show(entry.getValue(),true,true);entry.getValue().setSelected(entry.getKey()==section);}
        show(load,true,number(extras,"primalCapacity",0)>number(extras,"primal",0));
        if(section==Section.FAMILIES){
            long n=families.stream().filter(Family::known).count();known.setMessage(tr("known_count",n));unknown.setMessage(tr("unknown_count",families.size()-n));
            known.setTooltip(Tooltip.create(known.getMessage().copy().append("\n").append(tr("known_hint"))));
            unknown.setTooltip(Tooltip.create(unknown.getMessage().copy().append("\n").append(tr("unknown_hint"))));
            show(known,true,true);show(unknown,true,true);known.setSelected(showKnown);unknown.setSelected(!showKnown);
            for(int i=0;i<rows.size();i++){
                int index=scroll.offset()+i;var row=rows.get(i);
                if(index<filtered.size()){var family=filtered.get(index);row.setMessage(Component.literal(family.name));row.setIcon(family.known?UiIcon.KNOWN:family.canDerive?UiIcon.DERIVE:UiIcon.UNKNOWN);row.setSelected(family.id.equals(selected));row.setTooltip(Tooltip.create(familyTooltip(family)));show(row,true,true);}
            }
            var family=selectedFamily();
            show(derive,true,family!=null&&!family.known&&family.canDerive&&flag(extras,"deriveReady",false)&&number(extras,"primal",0)>=family.primal&&family.primal>0);
            derive.setMessage(family!=null&&family.known?tr("already_known"):tr("derive"));
            derive.setIcon(family!=null&&family.known?UiIcon.KNOWN:UiIcon.DERIVE);
            if(family!=null)derive.setTooltip(Tooltip.create(family.known?tr("already_known"):!family.canDerive?tr("missing_ingredients"):!flag(extras,"deriveReady",false)?tr("derive_network"):number(extras,"primal",0)<family.primal?tr("need_primal",compact(family.primal)):tr("derive_hint")));
        } else if(section==Section.DISCOVERY){
            for(int i=0;i<tiers.size();i++){show(tiers.get(i),true,true);tiers.get(i).setSelected(tier==i+1);}
            show(discover,true,candidates[tier-1]>0&&flag(extras,"discoveryAllowed",false)&&number(extras,"primal",0)>=4_000);
            discover.setMessage(tr("start_tier",roman(tier)));
            discover.setTooltip(Tooltip.create(discoveryReason()));
        } else {
            String state=text(session,"state","");boolean present=!text(session,"id","").isEmpty();
            show(pause,true,present&&(state.equals("ACTIVE")||state.equals("PAUSED")));
            pause.setMessage(state.equals("ACTIVE")?tr("pause"):tr("resume"));
            pause.setIcon(state.equals("ACTIVE")?UiIcon.PAUSE:UiIcon.RESUME);
            pause.setTooltip(Tooltip.create(pause.getMessage().copy().append("\n").append(tr(state.equals("ACTIVE")?"pause_hint":"resume_hint"))));
            show(refund,true,number(session,"refundDue",number(extras,"refundDue",0))>0);
        }
    }
    private void show(AbstractWidget widget,boolean value,boolean active){widget.visible=visible&&value;widget.active=visible&&active;if(!widget.visible)widget.setFocused(false);}
    private void send(MachineActionPayload.Action action,String target,long amount,boolean current){
        ClientPacketDistributor.sendToServer(new MachineActionPayload(menu.containerId,action,target,amount));
        awaiting=true;if(current)section=Section.CURRENT;updateWidgets();
    }
    void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY){
        if(!visible)return;
        label(g,tr("tab."+section.name().toLowerCase(Locale.ROOT)),left+88,top+5,width-88,UiPalette.TEXT);
        boolean hasCell=number(extras,"primalCapacity",0)>0;
        marked(g,UiIcon.PRIMAL,hasCell?tr("primal_reserve",compact(number(extras,"primal",0)),compact(number(extras,"primalCapacity",0))):tr("no_cell"),left,top+26,width-22,hasCell?UiPalette.PRIMAL_LIGHT:UiPalette.MUTED);
        if(section==Section.FAMILIES)renderFamilies(g,mouseX,mouseY);else if(section==Section.DISCOVERY)renderDiscovery(g,mouseX,mouseY);else renderCurrent(g,mouseX,mouseY);
    }
    private void renderFamilies(GuiGraphicsExtractor g,int mouseX,int mouseY){
        bar.extractRenderState(g);
        if(filtered.isEmpty())label(g,showKnown?tr("no_known"):tr("all_known"),left+4,top+78,width-16,UiPalette.MUTED);
        ScreenChrome.inset(g,left,top+120,width,height-142);
        var family=selectedFamily();if(family==null)return;
        label(g,Component.literal(family.name),left+4,top+122,width-8,UiPalette.TEXT);
        marked(g,UiIcon.SLOT_SAMPLE,tr("probe",family.probes.isEmpty()?Component.literal("—"):SubstanceNames.item(family.probes.getFirst())),left+4,top+132,width-8,UiPalette.MUTED);
        long known=family.substances.stream().filter(Substance::known).count();
        marked(g,family.canDerive||family.known?UiIcon.KNOWN:UiIcon.WARNING,tr("ingredients",known,family.substances.size()),left+4,top+144,width-8,family.canDerive||family.known?UiPalette.ACCENT:UiPalette.WARNING);
        var price=family.known?tr("learned_by",learningName(family.learningPath)):tr(extras.has("deriveEnergy")?"price":"base_price",compact(family.primal),compact(number(extras,"deriveEnergy",256_000)),time(integer(extras,"deriveTicks",1_800)));
        if(family.known)label(g,price,left+4,top+156,width-8,UiPalette.MUTED);
        else price(g,left+4,top+156,compact(family.primal),compact(number(extras,"deriveEnergy",256_000)),time(integer(extras,"deriveTicks",1_800)));
        if(inside(mouseX,mouseY,left,top+120,width,height-142))g.setComponentTooltipForNextFrame(font,List.of(familyTooltip(family)),mouseX,mouseY);
    }
    private void renderDiscovery(GuiGraphicsExtractor g,int mouseX,int mouseY){
        int steps=4<<(tier-1),count=candidates[tier-1];
        ScreenChrome.inset(g,left,top+64,width,height-86);
        marked(g,UiIcon.UNKNOWN,tr("possible",count),left+5,top+69,width-10,count>0?UiPalette.TEXT:UiPalette.WARNING);
        marked(g,UiIcon.PRIMAL,tr("primal_cost",compact(4_000L*steps)),left+5,top+84,width-10,UiPalette.PRIMAL_LIGHT);
        values(g,left+5,top+98,new UiIcon[]{UiIcon.ENERGY,UiIcon.TIME},new String[]{compact(number(extras,"discoveryStepEnergy",600_000)*steps),time(1_500*steps)},UiPalette.TEXT);
        ScreenChrome.twoLines(g,font,tr("surprise").getString(),left+5,top+112,width-10,UiPalette.MUTED);
        marked(g,UiIcon.KNOWN,tr("known_total",number(extras,"knownCount",0)),left+5,top+138,width-10,UiPalette.MUTED);
        boolean warning=count==0||!flag(extras,"discoveryAllowed",false);
        marked(g,warning?UiIcon.WARNING:UiIcon.INFO,discoveryReason(),left+5,top+152,width-10,warning?UiPalette.WARNING:UiPalette.MUTED);
        if(inside(mouseX,mouseY,left,top+64,width,height-86))g.setComponentTooltipForNextFrame(font,List.of(tr("discovery_hint"),tr(extras.has("discoveryStepEnergy")?"energy_time":"base_energy_time",compact(number(extras,"discoveryStepEnergy",600_000)*steps),time(1_500*steps)),discoveryReason(),tr("step_payment")),mouseX,mouseY);
    }
    private Component discoveryReason(){
        if(candidates[tier-1]==0)return tr("pool_empty");
        if(extras.has("discoveryReason")&&!text(extras,"discoveryReason","").isEmpty())return Component.translatable(text(extras,"discoveryReason",""));
        if(!flag(extras,"discoveryAllowed",false))return tr("discovery_requirements");
        if(number(extras,"primal",0)<4_000)return tr("need_primal",compact(4_000));
        return tr("step_payment");
    }
    private void renderCurrent(GuiGraphicsExtractor g,int mouseX,int mouseY){
        ScreenChrome.inset(g,left,top+42,width,height-64);
        String id=text(session,"id","");
        if(id.isEmpty()){
            ScreenChrome.icon(g,UiIcon.HELP,left+3,top+48,UiPalette.MUTED);
            ScreenChrome.twoLines(g,font,tr(awaiting?"waiting":"no_session").getString(),left+21,top+52,width-26,UiPalette.MUTED);return;
        }
        String mode=text(session,"mode","");String state=text(session,"state","");
        marked(g,mode.equals("DISCOVERY")?UiIcon.DISCOVER:UiIcon.DERIVE,mode.equals("DISCOVERY")?tr("discovery_tier",roman(integer(session,"tier",1))):tr("derivation"),left+5,top+47,width-10,UiPalette.TEXT);
        String target=state.equals("COMPLETE")?text(session,"resultFamily",""):mode.equals("DERIVE")?text(session,"deriveTarget",""):"";
        marked(g,state.equals("COMPLETE")?UiIcon.KNOWN:UiIcon.UNKNOWN,target.isEmpty()?tr("hidden_result"):Component.literal(familyName(target)),left+5,top+61,width-10,state.equals("COMPLETE")?UiPalette.ACCENT:UiPalette.MUTED);
        int completed=integer(session,"completedSteps",0),total=Math.max(1,integer(session,"totalSteps",1)),elapsed=integer(session,"elapsedTicks",0),stepTicks=Math.max(1,integer(session,"stepTicks",1));
        long done=(long)completed*stepTicks+elapsed,all=(long)total*stepTicks;
        ScreenChrome.inset(g,left+5,top+77,width-10,6);int fill=(int)Math.min(width-12,(width-12)*Math.min(all,done)/all);
        if(fill>0)g.fill(left+6,top+78,left+6+fill,top+82,UiPalette.ACCENT);
        marked(g,UiIcon.TIME,tr("step_progress",completed,total,time(elapsed),time(stepTicks)),left+5,top+88,width-10,UiPalette.TEXT);
        marked(g,UiIcon.PRIMAL,tr("paid",compact(number(session,"paidSteps",0)*number(session,"primalPerStep",0)),compact((long)total*number(session,"primalPerStep",0))),left+5,top+102,width-10,UiPalette.PRIMAL_LIGHT);
        UiIcon lamp=switch(state){case "ACTIVE"->UiIcon.STATUS_WORKING;case "PAUSED"->UiIcon.STATUS_IDLE;case "INELIGIBLE"->UiIcon.STATUS_BLOCKED;case "COMPLETE"->UiIcon.STATUS_DONE;default->UiIcon.STATUS_WARNING;};
        ScreenChrome.icon(g,lamp,left+3,top+112,-1);
        label(g,tr("state."+state.toLowerCase(Locale.ROOT)),left+21,top+116,width-26,state.equals("COMPLETE")?UiPalette.ACCENT:state.equals("ACTIVE")?UiPalette.TEXT:UiPalette.WARNING);
        long due=number(session,"refundDue",number(extras,"refundDue",0));
        Component help=due>0?tr("refund_due",compact(due)):tr("help."+state.toLowerCase(Locale.ROOT));
        ScreenChrome.twoLines(g,font,help.getString(),left+5,top+130,width-10,UiPalette.MUTED);
        if(inside(mouseX,mouseY,left,top+42,width,height-64))g.setComponentTooltipForNextFrame(font,List.of(help,tr("resume_hint")),mouseX,mouseY);
    }
    private Component familyTooltip(Family family){
        var text=Component.literal(family.name).append("\n").append(tr("probes",String.join(", ",family.probes)));
        for(var substance:family.substances){
            var line=Component.literal("\n"+(substance.known?"✓ ":"○ "));
            String key="substance."+substance.id.replace(':','.').replace('/','.');
            line.append(Component.translatable(key)).append(substance.phase.isEmpty()?"":" ("+substance.phase+")");
            text.append(line.withColor((substance.known?UiPalette.ACCENT:UiPalette.WARNING)&0xffffff));
        }
        return text;
    }
    private String familyName(String id){return families.stream().filter(family->family.id.equals(id)).map(Family::name).findFirst().orElse(id);}
    private Component learningName(String method){return method.isEmpty()?tr("learning.unknown"):tr("learning."+method.toLowerCase(Locale.ROOT));}
    private void marked(GuiGraphicsExtractor g,UiIcon icon,Component text,int x,int y,int max,int color){
        ScreenChrome.icon(g,icon,x-2,y-4,icon==UiIcon.PRIMAL&&color!=UiPalette.MUTED?UiPalette.PRIMAL:color);label(g,text,x+16,y,max-16,color);
    }
    private void price(GuiGraphicsExtractor g,int x,int y,String primal,String energy,String time){
        values(g,x,y,new UiIcon[]{UiIcon.PRIMAL,UiIcon.ENERGY,UiIcon.TIME},new String[]{primal,energy,time},UiPalette.MUTED);
    }
    private void values(GuiGraphicsExtractor g,int x,int y,UiIcon[] icons,String[] values,int color){
        for(int i=0;i<icons.length;i++){ScreenChrome.icon(g,icons[i],x-2,y-4,icons[i]==UiIcon.PRIMAL?UiPalette.PRIMAL:color);g.text(font,values[i],x+14,y,icons[i]==UiIcon.PRIMAL?UiPalette.PRIMAL_LIGHT:color,false);x+=18+font.width(values[i]);}
    }
    private void label(GuiGraphicsExtractor g,Component text,int x,int y,int max,int color){ScreenChrome.text(g,font,text.getString(),x,y,max,color);}
    boolean mouseScrolled(double x,double y,double dx,double dy){if(!visible||section!=Section.FAMILIES||!inside(x,y,left,top+62,width,54))return false;scroll.wheel(dy);updateWidgets();return true;}
    boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){return visible&&section==Section.FAMILIES&&bar.mouseClicked(event.x(),event.y(),event.button());}
    boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(!visible||section!=Section.FAMILIES)return false;boolean changed=bar.mouseDragged(event.x(),event.y());if(changed)updateWidgets();return changed;}
    boolean mouseReleased(MouseButtonEvent event){return bar.mouseReleased();}
    boolean handlesKey(KeyEvent event){
        if(!visible||section!=Section.FAMILIES||!scroll.key(event.key()))return false;
        if(scroll.selected()>=0&&scroll.selected()<filtered.size())selected=filtered.get(scroll.selected()).id;
        updateWidgets();return true;
    }
    private static boolean inside(double x,double y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    private static JsonArray array(JsonObject value,String key){return value.has(key)&&value.get(key).isJsonArray()?value.getAsJsonArray(key):new JsonArray();}
    private static JsonObject object(JsonObject value,String key){return value.has(key)&&value.get(key).isJsonObject()?value.getAsJsonObject(key):new JsonObject();}
    private static String text(JsonObject value,String key,String fallback){try{return value.has(key)&&value.get(key).isJsonPrimitive()?value.get(key).getAsString():fallback;}catch(RuntimeException invalid){return fallback;}}
    private static long number(JsonObject value,String key,long fallback){try{return value.has(key)&&value.get(key).isJsonPrimitive()?Math.max(0,value.get(key).getAsLong()):fallback;}catch(RuntimeException invalid){return fallback;}}
    private static int integer(JsonObject value,String key,int fallback){return (int)Math.min(Integer.MAX_VALUE,number(value,key,fallback));}
    private static boolean flag(JsonObject value,String key,boolean fallback){try{return value.has(key)&&value.get(key).isJsonPrimitive()&&value.get(key).getAsJsonPrimitive().isBoolean()?value.get(key).getAsBoolean():fallback;}catch(RuntimeException invalid){return fallback;}}
    private static List<String> strings(JsonObject value,String key){var result=new ArrayList<String>();for(var entry:array(value,key))if(entry.isJsonPrimitive())result.add(entry.getAsString());return List.copyOf(result);}
    private static String identifier(JsonElement value){if(value==null)return "";if(value.isJsonPrimitive())return value.getAsString();if(value.isJsonObject()){var json=value.getAsJsonObject();return text(json,"namespace","")+":"+text(json,"path","");}return "";}
    private static String roman(int value){return switch(value){case 2->"II";case 3->"III";default->"I";};}
    private static String compact(long value){if(value>=1_000_000)return decimal(value,1_000_000)+"M";if(value>=1_000)return decimal(value,1_000)+"k";return Long.toString(value);}
    private static String decimal(long value,long unit){long tenths=value/(unit/10);return tenths%10==0?Long.toString(tenths/10):tenths/10+"."+tenths%10;}
    private static String time(int ticks){long seconds=((long)Math.max(0,ticks)+19)/20;return seconds>=60?seconds/60+"m"+(seconds%60==0?"":seconds%60+"s"):seconds+"s";}
    private static Component tr(String key,Object...args){return Component.translatable("screen.rematter.research."+key,args);}
}
