package dev.latrox.rematter.celltransfer;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.*;
import dev.latrox.rematter.network.*;
import dev.latrox.rematter.primal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Six explicitly owned Cell slots. Readouts use detached stores; only the Transfer action changes quantities. */
public final class CellTransferStationBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    public static final int SOURCE_SLOTS=3,SLOTS=6;
    private final NonNullList<ItemStack> items=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
    private final PrimalCells.Journal[] primalJournals=new PrimalCells.Journal[SLOTS];
    private @Nullable UUID owner;
    private @Nullable CompoundTag unsupportedData;
    private CellTransferSelection selection=CellTransferSelection.ALL;
    private long revision,movedRevision=-1,movedOrdinary,movedPrimal;
    private boolean transferring,inspecting;
    private Observation[] observed;
    private int visualMask,visualTiers,visualStatuses,publishedMask=-1,publishedTiers=-1,publishedStatuses=-1;
    private record Observation(ItemStack reference,ItemStack snapshot){}
    private record Cell(CellTransferPayload.SlotView view,@Nullable MatterInventory ordinary,@Nullable UUID identity){}
    private record Visual(int mask,int tiers,int statuses){}
    private record Preview(String status,boolean locked,boolean partial,List<CellTransferPayload.SlotView> slots,List<CellTransferPayload.Entry> rows,long ordinary,long primal){}
    public CellTransferStationBlockEntity(BlockPos pos,BlockState state){
        super(ModContent.CELL_TRANSFER_STATION_ENTITY.get(),pos,state);
        for(int i=0;i<SLOTS;i++){final int slot=i;primalJournals[i]=new PrimalCells.Journal(()->items.get(slot));}
    }
    private boolean installed(){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||isRemoved())return false;
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        return chunk!=null&&chunk.getBlockEntity(worldPosition)==this;
    }
    public boolean locked(){return unsupportedData!=null;}
    public @Nullable UUID owner(){return owner;}
    public boolean canRemove(Player player){return player instanceof FakePlayer?player.getUUID().equals(owner):owner==null||owner.equals(player.getUUID())||player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);}
    public boolean canAccess(Player player){return canRemove(player)||!(player instanceof FakePlayer)&&dev.latrox.rematter.compat.TeamAccess.shared(owner,player.getUUID());}
    private boolean canAct(Player player){return installed()&&!locked()&&!player.isSpectator()&&canAccess(player)&&Container.stillValidBlockEntity(this,player);}
    public void placedBy(@Nullable LivingEntity placer){owner=placer instanceof Player player?player.getUUID():null;advanceRevision();setChanged();}
    public boolean interact(Player player){if(owner==null&&!player.isSpectator()&&!(player instanceof FakePlayer)){owner=player.getUUID();advanceRevision();setChanged();}return canAccess(player);}
    private void advanceRevision(){revision=Math.incrementExact(revision);}
    private void refreshRevision(){
        boolean changed=observed==null;
        if(!changed)for(int i=0;i<SLOTS;i++)if(items.get(i)!=observed[i].reference||items.get(i).getCount()!=observed[i].snapshot.getCount()||!ItemStack.isSameItemSameComponents(items.get(i),observed[i].snapshot)){changed=true;break;}
        if(changed){observed=new Observation[SLOTS];for(int i=0;i<SLOTS;i++)observed[i]=new Observation(items.get(i),items.get(i).copy());advanceRevision();}
    }
    public long revision(){refreshRevision();return revision;}
    public CellTransferSelection selection(){return selection;}
    private Cell readCell(ItemStack stack){
        if(stack.isEmpty())return new Cell(CellTransferPayload.SlotView.EMPTY,null,null);
        if(stack.getItem() instanceof MatterCellItem item){
            var policy=CellPartitionData.read(stack);var inventory=item.open(stack);
            if(policy.isEmpty()||inventory.isEmpty())return new Cell(new CellTransferPayload.SlotView(1,false,0,item.capacity(),0),null,null);
            var data=stack.get(MatterCellData.TYPE.get());
            UUID id=data==null?null:UUID.fromString(data.copyTag().getString("cell").orElseThrow());
            var store=inventory.get();return new Cell(new CellTransferPayload.SlotView(1,true,store.total(),item.capacity(),store.typeCount()),store,id);
        }
        if(stack.getItem() instanceof PrimalCellItem item){
            var state=PrimalCells.state(stack);
            if(state.isEmpty())return new Cell(new CellTransferPayload.SlotView(2,false,0,item.capacity(),0),null,null);
            var value=state.get();var id=value.cellId();if(id.equals(new UUID(0,0))&&value.amount()==0)id=null;
            return new Cell(new CellTransferPayload.SlotView(2,true,value.amount(),item.capacity(),value.amount()>0?1:0),null,id);
        }
        return new Cell(new CellTransferPayload.SlotView(3,false,0,0,0),null,null);
    }
    /** Fixed partitions reserve their matching resource before a general-purpose target can take it. */
    private List<Integer> targetOrder(){
        var order=new ArrayList<Integer>();for(int i=SOURCE_SLOTS;i<SLOTS;i++)order.add(i);
        order.sort(Comparator.<Integer>comparingInt(i->CellPartitionData.read(items.get(i))
                .filter(CellPartitionData.Spec::restricted).map(spec->spec.allowed().size()).orElse(Integer.MAX_VALUE)).thenComparingInt(i->i));
        return order;
    }
    private Preview preview(){
        boolean previous=inspecting;inspecting=true;
        try{return previewCells();}finally{inspecting=previous;}
    }
    private Preview previewCells(){
        var cells=new Cell[SLOTS];var slots=new ArrayList<CellTransferPayload.SlotView>();var rows=new TreeMap<SubstanceKey,Long>();
        boolean unreadable=locked(),duplicate=false,partial=false,sources=false,targets=false;long sourceAmount=0;
        var references=Collections.newSetFromMap(new IdentityHashMap<ItemStack,Boolean>());
        var ordinaryIds=new HashSet<UUID>();var primalIds=new HashSet<UUID>();
        for(int i=0;i<SLOTS;i++){
            if(!items.get(i).isEmpty()&&!references.add(items.get(i)))duplicate=true;
            var cell=cells[i]=readCell(items.get(i));slots.add(cell.view);unreadable|=!cell.view.readable();
            if(cell.identity!=null&&!(cell.view.kind()==1?ordinaryIds:primalIds).add(cell.identity))duplicate=true;
            if(i<SOURCE_SLOTS){sources|=cell.view.kind()!=0;sourceAmount=Math.addExact(sourceAmount,cell.view.amount());
                if(cell.ordinary!=null)for(var row:cell.ordinary.snapshot().contents())if(row.key().id().toString().length()<=CellPartitionData.MAX_ID_LENGTH)rows.merge(row.key(),row.quantity(),Math::addExact);else partial=true;
            }else targets|=cell.view.kind()!=0;
        }
        for(var key:selection.keys())rows.putIfAbsent(key,0L);
        var available=rows.entrySet().stream().map(e->new CellTransferPayload.Entry(e.getKey(),e.getValue())).toList();
        if(unreadable)return new Preview("locked",true,partial,slots,available,0,0);
        if(duplicate)return new Preview("same_cell",false,partial,slots,available,0,0);
        if(!sources||!targets)return new Preview("insert_cells",false,partial,slots,available,0,0);
        if(sourceAmount==0)return new Preview("empty_source",false,partial,slots,available,0,0);
        if(!selection.all()&&selection.keys().isEmpty()&&!selection.primalSelected())return new Preview("no_selection",false,partial,slots,available,0,0);
        long ordinary=0,primal=0;boolean eligible=false,compatible=false,space=false;var primalAmounts=new long[SLOTS];for(int i=0;i<SLOTS;i++)primalAmounts[i]=cells[i].view.amount();
        var targetOrder=targetOrder();
        for(int source=0;source<SOURCE_SLOTS;source++){
            var from=cells[source];
            if(from.ordinary!=null){
                eligible|=from.ordinary.snapshot().contents().stream().anyMatch(row->selection.test(row.key()));
                for(int target:targetOrder)if(cells[target].ordinary!=null){
                    compatible=true;space|=cells[target].ordinary.freeSpace()>0;
                    for(var row:from.ordinary.snapshot().contents())if(selection.test(row.key()))ordinary=Math.addExact(ordinary,from.ordinary.transferTo(cells[target].ordinary,row.key(),row.quantity()));
                }
            }else if(from.view.kind()==2&&selection.includesPrimal()&&primalAmounts[source]>0){
                eligible=true;
                for(int target:targetOrder)if(cells[target].view.kind()==2){
                    compatible=true;long room=cells[target].view.capacity()-primalAmounts[target];space|=room>0;
                    long moved=Math.min(primalAmounts[source],room);primalAmounts[source]-=moved;primalAmounts[target]+=moved;primal=Math.addExact(primal,moved);
                }
            }
        }
        String status=ordinary>0||primal>0?"ready":!eligible?"filtered":!compatible?"type_mismatch":!space?"target_full":"filtered";
        return new Preview(status,false,partial,slots,available,ordinary,primal);
    }
    public CellTransferPayload snapshot(int containerId){
        long revision=revision();var view=preview();if(revision()!=revision){revision=revision();view=preview();}boolean last=movedRevision==revision;
        String status=last&&(movedOrdinary>0||movedPrimal>0)?"transferred":view.status;
        return new CellTransferPayload(containerId,revision,status,view.ordinary>0||view.primal>0,view.locked,view.partial,selection,view.slots,view.rows,view.ordinary,view.primal,last?movedOrdinary:0,last?movedPrimal:0);
    }
    public boolean configure(Player player,CellTransferConfigPayload action){
        if(transferring||inspecting||Transaction.getCurrentOpenedTransaction()!=null||!canAct(player)||revision()!=action.revision())return false;
        if(action.action()==CellTransferConfigPayload.Action.TRANSFER)return transfer(player,action.revision())>0;
        try {
            switch(action.action()){
                case GLOBAL_ALL -> selection=new CellTransferSelection(action.value(),selection.primalSelected(),selection.keys());
                case GLOBAL_PRIMAL -> selection=new CellTransferSelection(selection.all(),action.value(),selection.keys());
                case GLOBAL_CLEAR -> selection=new CellTransferSelection(false,false,Set.of());
                case GLOBAL_TOGGLE -> selection=selection.toggle(Objects.requireNonNull(action.key()));
                default -> {
                    var stack=items.get(action.slot());var before=stack.copy();
                    if(!(stack.getItem() instanceof MatterCellItem cell))return false;
                    var policy=CellPartitionData.read(stack);var opened=cell.open(stack);
                    if(policy.isEmpty()||opened.isEmpty())return false;var spec=policy.get();
                    if(revision()!=action.revision()||items.get(action.slot())!=stack||!ItemStack.isSameItemSameComponents(before,stack))return false;
                    switch(action.action()){
                        case CELL_ANY -> stack.remove(CellPartitionData.TYPE.get());
                        case CELL_CLEAR -> stack.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(Set.of()));
                        case CELL_TOGGLE -> {var key=Objects.requireNonNull(action.key());var next=spec.withKey(key,!spec.allowed().contains(key));stack.set(CellPartitionData.TYPE.get(),CellPartitionData.from(next));}
                        case CELL_TO_CONTENTS -> stack.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(opened.orElseThrow().snapshot().contents().stream().map(SubstanceStack::key).toList()));
                        default -> throw new IllegalArgumentException("Unknown Cell configuration");
                    }
                }
            }
            advanceRevision();setChanged();return true;
        }catch(IllegalArgumentException invalid){return false;}
    }
    public long transfer(Player player,long expectedRevision){
        if(transferring||inspecting||Transaction.getCurrentOpenedTransaction()!=null||!canAct(player)||revision()!=expectedRevision)return 0;
        transferring=true;
        try {
            var initial=preview();if(initial.locked||initial.ordinary==0&&initial.primal==0||revision()!=expectedRevision)return 0;
            var captured=items.toArray(ItemStack[]::new);var targetOrder=targetOrder();long ordinary=0,primal=0;
            try(var tx=Transaction.openRoot()){
                for(int source=0;source<SOURCE_SLOTS;source++)for(int target:targetOrder){
                    final int from=source,to=target;var a=items.get(from);var b=items.get(to);
                    if(a.getItem() instanceof MatterCellItem&&b.getItem() instanceof MatterCellItem)
                        ordinary=Math.addExact(ordinary,MatterCellTransfers.move((ServerLevel)level,a,b,()->items.get(from),()->items.get(to),()->sameReferences(captured)&&canAct(player),selection,tx));
                    else if(a.getItem() instanceof PrimalCellItem&&b.getItem() instanceof PrimalCellItem&&selection.includesPrimal())
                        primal=Math.addExact(primal,PrimalCells.transfer(primalJournals[from],primalJournals[to],Long.MAX_VALUE,tx));
                }
                if(!sameReferences(captured)||!canAct(player)||ordinary==0&&primal==0||ordinary!=initial.ordinary||primal!=initial.primal)return 0;
                tx.commit();
            }
            setChanged();movedRevision=revision();movedOrdinary=ordinary;movedPrimal=primal;return Math.addExact(ordinary,primal);
        }finally{transferring=false;publishVisuals();}
    }
    private boolean sameReferences(ItemStack[] captured){for(int i=0;i<SLOTS;i++)if(items.get(i)!=captured[i]||!items.get(i).isEmpty()&&items.get(i).getCount()!=1)return false;return true;}
    private Visual visuals(){
        int mask=0,tiers=0,statuses=0;
        for(int i=0;i<SLOTS;i++)if(!items.get(i).isEmpty()){
            mask|=1<<i;var cell=readCell(items.get(i));int tier=4;
            if(cell.view.readable()){
                long capacity=cell.view.capacity();tier=cell.view.kind()==1?(capacity==MatterLimits.CELL_64K?1:capacity==MatterLimits.CELL_256K?2:capacity==MatterLimits.CELL_1M?3:4):capacity==16_000?5:capacity==64_000?6:capacity==256_000?7:4;
            }
            var status=CellStatus.of(true,cell.view.readable(),cell.view.amount(),cell.view.capacity(),cell.view.kind()==2?0:cell.view.types(),cell.view.kind()==2?1:MatterCellData.MAX_TYPES);
            tiers|=tier<<(3*i);statuses|=status.ordinal()<<(3*i);
        }
        return new Visual(mask,tiers,statuses);
    }
    private void publishVisuals(){
        if(!installed()||transferring||Transaction.getCurrentOpenedTransaction()!=null)return;
        var next=visuals();if(next.mask==publishedMask&&next.tiers==publishedTiers&&next.statuses==publishedStatuses)return;
        publishedMask=next.mask;publishedTiers=next.tiers;publishedStatuses=next.statuses;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    public int visibleCellMask(){return level!=null&&level.isClientSide()?visualMask:visuals().mask;}
    public int visibleCellTier(int bay){return bay<0||bay>=SLOTS?0:((level!=null&&level.isClientSide()?visualTiers:visuals().tiers)>>>(bay*3))&7;}
    public CellStatus visibleCellStatus(int bay){return bay<0||bay>=SLOTS?CellStatus.ABSENT:CellStatus.decode(((level!=null&&level.isClientSide()?visualStatuses:visuals().statuses)>>>(bay*3))&7);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();var value=Transaction.getCurrentOpenedTransaction()==null?visuals():new Visual(Math.max(0,publishedMask),Math.max(0,publishedTiers),Math.max(0,publishedStatuses));tag.putInt("cell_mask",value.mask);tag.putInt("cell_tiers",value.tiers);tag.putInt("cell_statuses",value.statuses);return tag;}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(ValueInput input){visualMask=input.getIntOr("cell_mask",0)&63;visualTiers=input.getIntOr("cell_tiers",0)&262143;visualStatuses=input.getIntOr("cell_statuses",0)&262143;}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput input){handleUpdateTag(input);}
    @Override public void setChanged(){super.setChanged();if(items!=null){refreshRevision();publishVisuals();}}
    @Override protected void saveAdditional(ValueOutput output){
        super.saveAdditional(output);if(locked()){output.store(CellTransferData.RAW,unsupportedData);return;}
        output.putInt("schema",CellTransferData.SCHEMA);if(owner!=null)output.putString("owner",owner.toString());
        output.store("items",ItemStack.OPTIONAL_CODEC.listOf(),List.copyOf(items));output.putBoolean("all",selection.all());output.putBoolean("primal_selected",selection.primalSelected());output.store("selection",CellTransferData.TAG,CellTransferData.keys(selection));
    }
    @Override protected void loadAdditional(ValueInput input){
        super.loadAdditional(input);var raw=input.read(CellTransferData.RAW).orElseGet(CompoundTag::new);var decoded=CellTransferData.read(raw,input.lookup());
        items.replaceAll(s->ItemStack.EMPTY);selection=CellTransferSelection.ALL;owner=raw.getString("owner").map(CellTransferData::owner).orElse(null);unsupportedData=decoded.isPresent()?null:raw;
        if(decoded.isPresent()){var state=decoded.get();for(int i=0;i<SLOTS;i++)items.set(i,state.items().get(i));selection=state.selection();owner=state.owner();}
        observed=null;advanceRevision();publishedMask=publishedTiers=publishedStatuses=-1;
    }
    public boolean hasStoredContents(){return locked()||items.stream().anyMatch(s->!s.isEmpty())||!selection.equals(CellTransferSelection.ALL);}
    @Override public Component getDisplayName(){return Component.translatable("block.rematter.cell_transfer_station");}
    @Override public @Nullable AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return canAccess(player)?new CellTransferMenu(id,inventory,this):null;}
    @Override public int getContainerSize(){return SLOTS;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int index){return items.get(index);}
    @Override public boolean canPlaceItem(int index,ItemStack stack){return !locked()&&stack.getCount()==1&&CellTransferData.isCell(stack);}
    @Override public int getMaxStackSize(){return 1;}
    @Override public void setItem(int index,ItemStack stack){if(!locked()&&!transferring&&!inspecting&&Transaction.getCurrentOpenedTransaction()==null&&(stack.isEmpty()||stack.getCount()==1&&CellTransferData.isCell(stack))){if(!stack.isEmpty())for(int i=0;i<SLOTS;i++)if(i!=index&&items.get(i)==stack)return;items.set(index,stack);setChanged();}}
    @Override public ItemStack removeItem(int index,int amount){if(locked()||transferring||inspecting||Transaction.getCurrentOpenedTransaction()!=null)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(items,index,amount);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int index){return locked()||transferring||inspecting||Transaction.getCurrentOpenedTransaction()!=null?ItemStack.EMPTY:ContainerHelper.takeItem(items,index);}
    @Override public void clearContent(){if(!locked()&&!transferring&&!inspecting&&Transaction.getCurrentOpenedTransaction()==null){items.replaceAll(s->ItemStack.EMPTY);setChanged();}}
    @Override public boolean stillValid(Player player){return canAccess(player)&&Container.stillValidBlockEntity(this,player);}
    @Override public int[] getSlotsForFace(Direction side){return new int[0];}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction side){return false;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
}
