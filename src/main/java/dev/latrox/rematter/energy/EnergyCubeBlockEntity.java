package dev.latrox.rematter.energy;

import dev.latrox.rematter.content.*;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.*;
import org.jspecify.annotations.Nullable;

/** A server-owned FE buffer. Its contents and every item-component change share native transactions. */
public final class EnergyCubeBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    private final EnergyCubeTier tier;
    private final NonNullList<ItemStack> items;
    private final ResourceHandler<ItemResource> itemHandler;
    private final SimpleEnergyHandler energy;
    private final EnergyCubeBudget inputBudget,outputBudget;
    private final EnergyCubeFlow flow=new EnergyCubeFlow();
    private @Nullable UUID owner;
    private @Nullable CompoundTag unsupportedData;
    // FRONT is ordinal zero. Every other face starts as an input.
    private int sideModes=EnergyCubeSideMode.OUTPUT.ordinal();

    public EnergyCubeBlockEntity(BlockPos pos,BlockState state){
        super(ModContent.ENERGY_CUBE_ENTITY.get(),pos,state);
        tier=((EnergyCubeBlock)state.getBlock()).tier();
        items=NonNullList.withSize(tier.chargingSlots(),ItemStack.EMPTY);
        inputBudget=new EnergyCubeBudget(tier.transferRate());outputBudget=new EnergyCubeBudget(tier.transferRate());
        energy=new SimpleEnergyHandler(tier.capacity(),tier.capacity(),tier.capacity()){
            @Override protected void onEnergyChanged(int previous){setChanged();publishCharge();}
        };
        // The native stack handler copies its constructor list. A Container wrapper keeps one authoritative
        // inventory and journals component changes together with charging, pickup and automation.
        itemHandler=VanillaContainerWrapper.of(this);
    }
    public EnergyCubeTier tier(){return tier;}
    public int energy(){return (int)energy.getAmountAsLong();}
    public boolean locked(){return unsupportedData!=null;}
    public @Nullable UUID owner(){return owner;}
    public int packedSideModes(){return sideModes;}
    public EnergyCubeSideMode sideMode(MachinePortMode.Side side){return EnergyCubeSideMode.values()[(sideModes>>>(side.ordinal()*2))&3];}
    public EnergyCubeSideMode sideMode(Direction side){return sideMode(local(side));}
    private MachinePortMode.Side local(Direction side){
        var front=getBlockState().getValue(EnergyCubeBlock.FACING);
        for(var candidate:MachinePortMode.Side.values())if(candidate.world(front)==side)return candidate;
        throw new IllegalArgumentException("Unknown cube face");
    }
    private boolean installed(){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||isRemoved())return false;
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        return chunk!=null&&chunk.getBlockEntity(worldPosition)==this;
    }
    private boolean live(){return installed()&&!locked()&&owner!=null;}
    public boolean canRemove(Player player){
        if(player instanceof FakePlayer)return player.getUUID().equals(owner);
        return owner==null||player.getUUID().equals(owner)||player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
    public boolean canAccess(Player player){return canRemove(player)||!(player instanceof FakePlayer)&&dev.latrox.rematter.compat.TeamAccess.shared(owner,player.getUUID());}
    public void placedBy(@Nullable LivingEntity placer){assignOwner(placer instanceof Player player?player.getUUID():null);publishCharge();}
    public boolean interact(Player player){
        if(owner==null&&!(player instanceof FakePlayer)&&!player.isSpectator())assignOwner(player.getUUID());
        return canAccess(player);
    }
    private void assignOwner(@Nullable UUID next){if(Objects.equals(owner,next))return;owner=next;setChanged();touchTopology();}
    public boolean cycleSide(Player player,MachinePortMode.Side side){return cycleSide(player,side,true);}
    public boolean cycleSide(Player player,MachinePortMode.Side side,boolean forward){
        if(!live()||player.isSpectator()||!canAccess(player)||!Container.stillValidBlockEntity(this,player)||!PortSides.energyCube(side))return false;
        var next=forward?sideMode(side).next():sideMode(side).previous();
        int shift=side.ordinal()*2;sideModes=(sideModes&~(3<<shift))|next.ordinal()<<shift;
        setChanged();touchTopology();
        // Only the side roles are public: the plates of a disabled side disappear on every client.
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        return true;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();tag.putInt("side_modes",sideModes);return tag;}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket(){
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void handleUpdateTag(ValueInput input){
        int shown=sideModes;sideModes=input.getIntOr("side_modes",shown);
        if(sideModes!=shown&&level!=null&&level.isClientSide()){
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_IMMEDIATE);
        }
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput input){handleUpdateTag(input);}
    /** A seat is drawn on every side that is not disabled; input and output share one seat by design. */
    @Override public net.neoforged.neoforge.model.data.ModelData getModelData(){
        return net.neoforged.neoforge.model.data.ModelData.of(PortSides.PLATES,PortSides.pack(side->
                sideMode(side)==EnergyCubeSideMode.DISABLED?MachinePortMode.DISABLED:MachinePortMode.AUTOMATIC));
    }
    private void touchTopology(){
        if(level instanceof ServerLevel server){
            ChunkRevisions.touchLoaded(server,worldPosition);server.invalidateCapabilities(worldPosition);
            MatterCableBlock.refreshLoadedAround(server,worldPosition);
        }
    }
    public @Nullable EnergyHandler sidedEnergy(@Nullable Direction side){
        if(side==null||sideMode(side)==EnergyCubeSideMode.DISABLED||!(level instanceof ServerLevel))return null;
        return new EnergyHandler(){
            public long getAmountAsLong(){return live()&&sideMode(side)!=EnergyCubeSideMode.DISABLED?energy.getAmountAsLong():0;}
            public long getCapacityAsLong(){return live()&&sideMode(side)!=EnergyCubeSideMode.DISABLED?tier.capacity():0;}
            public int insert(int amount,TransactionContext tx){
                TransferPreconditions.checkNonNegative(amount);
                return live()&&sideMode(side)==EnergyCubeSideMode.INPUT?inputBudget.transfer(level.getGameTime(),amount,tx,n->energy.insert(n,tx)):0;
            }
            public int extract(int amount,TransactionContext tx){
                TransferPreconditions.checkNonNegative(amount);
                return live()&&sideMode(side)==EnergyCubeSideMode.OUTPUT?drain(amount,tx):0;
            }
        };
    }
    private int drain(int amount,TransactionContext tx){return live()?outputBudget.transfer(level.getGameTime(),amount,tx,n->energy.extract(n,tx)):0;}
    private final EnergyHandler chargingSource=new EnergyHandler(){
        public long getAmountAsLong(){return energy();}
        public long getCapacityAsLong(){return tier.capacity();}
        public int insert(int amount,TransactionContext tx){TransferPreconditions.checkNonNegative(amount);return 0;}
        public int extract(int amount,TransactionContext tx){TransferPreconditions.checkNonNegative(amount);return drain(amount,tx);}
    };
    /** Automation follows face roles; only fully charged items leave through an output. */
    public @Nullable ResourceHandler<ItemResource> sidedItems(@Nullable Direction side){
        if(side==null||sideMode(side)==EnergyCubeSideMode.DISABLED||!(level instanceof ServerLevel))return null;
        return new ResourceHandler<>(){
            private boolean available(){return live()&&sideMode(side)!=EnergyCubeSideMode.DISABLED;}
            public int size(){return itemHandler.size();}
            public ItemResource getResource(int index){return available()?itemHandler.getResource(index):ItemResource.EMPTY;}
            public long getAmountAsLong(int index){return available()?itemHandler.getAmountAsLong(index):0;}
            public long getCapacityAsLong(int index,ItemResource resource){return available()?1:0;}
            public boolean isValid(int index,ItemResource resource){return available()&&sideMode(side)==EnergyCubeSideMode.INPUT&&itemHandler.isValid(index,resource);}
            public int insert(int index,ItemResource resource,int amount,TransactionContext tx){
                TransferPreconditions.checkNonEmptyNonNegative(resource,amount);
                return isValid(index,resource)?itemHandler.insert(index,resource,amount,tx):0;
            }
            public int extract(int index,ItemResource resource,int amount,TransactionContext tx){
                TransferPreconditions.checkNonEmptyNonNegative(resource,amount);
                return available()&&sideMode(side)==EnergyCubeSideMode.OUTPUT&&fullyCharged(index)?itemHandler.extract(index,resource,amount,tx):0;
            }
        };
    }
    public static boolean chargeable(ItemStack stack){return !stack.isEmpty()&&ItemAccess.forStack(stack.copy()).getCapability(Capabilities.Energy.ITEM)!=null;}
    private boolean fullyCharged(int index){
        var handler=ItemAccess.forHandlerIndexStrict(itemHandler,index).getCapability(Capabilities.Energy.ITEM);
        return handler!=null&&handler.getCapacityAsLong()>0&&handler.getAmountAsLong()>=handler.getCapacityAsLong();
    }
    public void serverTick(){
        if(!live())return;
        // Power delivery has priority; item charging uses the same remaining output allowance.
        if(energy()>0)for(var side:Direction.values()){
            if(sideMode(side)!=EnergyCubeSideMode.OUTPUT)continue;
            BlockPos target=worldPosition.relative(side);
            if(level.getChunkSource().getChunkNow(target.getX()>>4,target.getZ()>>4)==null)continue;
            if(level.getBlockEntity(target) instanceof EnergyCubeBlockEntity cube&&!Objects.equals(owner,cube.owner))continue;
            var sink=level.getCapability(Capabilities.Energy.BLOCK,target,side.getOpposite());
            if(sink!=null)try(var tx=Transaction.openRoot()){if(EnergyHandlerUtil.move(chargingSource,sink,tier.transferRate(),tx)>0)tx.commit();}
        }
        int count=tier.chargingSlots(),start=Math.floorMod(level.getGameTime(),count);
        var pending=new ArrayList<Integer>();
        for(int i=0;i<count;i++){
            int slot=(start+i)%count;if(items.get(slot).isEmpty())continue;
            var sink=ItemAccess.forHandlerIndexStrict(itemHandler,slot).getCapability(Capabilities.Energy.ITEM);
            if(sink!=null&&sink.getAmountAsLong()<sink.getCapacityAsLong())pending.add(slot);
        }
        for(int i=0;i<pending.size()&&energy()>0&&outputBudget.remaining(level.getGameTime())>0;i++){
            var sink=ItemAccess.forHandlerIndexStrict(itemHandler,pending.get(i)).getCapability(Capabilities.Energy.ITEM);
            int offer=outputBudget.remaining(level.getGameTime())/(pending.size()-i);
            if(sink!=null&&offer>0)try(var tx=Transaction.openRoot()){if(EnergyHandlerUtil.move(chargingSource,sink,offer,tx)>0)tx.commit();}
        }
        publishCharge();
        var shownFlow=flow.sample(level.getGameTime(),energy());
        if(getBlockState().getValue(EnergyCubeBlock.FLOW)!=shownFlow)
            level.setBlock(worldPosition,getBlockState().setValue(EnergyCubeBlock.FLOW,shownFlow),2);
    }
    private void publishCharge(){
        if(!installed())return;
        int charge=locked()?0:(int)(8L*energy()/tier.capacity());
        if(getBlockState().getValue(EnergyCubeBlock.CHARGE)!=charge)level.setBlock(worldPosition,getBlockState().setValue(EnergyCubeBlock.CHARGE,charge),2);
    }
    @Override protected void saveAdditional(ValueOutput output){
        super.saveAdditional(output);
        if(locked()){output.store(EnergyCubeData.RAW,unsupportedData);return;}
        output.putInt("schema",EnergyCubeData.SCHEMA);output.putInt("energy",energy());output.putInt("side_modes",sideModes);
        if(owner!=null)output.putString("owner",owner.toString());
        output.store("items",ItemStack.OPTIONAL_CODEC.listOf(),List.copyOf(items));
    }
    @Override protected void loadAdditional(ValueInput input){
        super.loadAdditional(input);var raw=input.read(EnergyCubeData.RAW).orElseGet(CompoundTag::new);
        var decoded=EnergyCubeData.items(raw,input.lookup());
        String rejection=EnergyCubeData.rejection(raw,tier,decoded.isPresent(),decoded.map(List::size).orElse(-1));
        unsupportedData=rejection==null?null:raw;
        owner=raw.getString("owner").map(EnergyCubeData::owner).orElse(null);
        if(!locked()){
            energy.set(raw.getIntOr("energy",0));sideModes=raw.getIntOr("side_modes",0);
            for(int i=0;i<items.size();i++)items.set(i,decoded.orElseThrow().get(i));
        }else{energy.set(0);items.replaceAll(stack->ItemStack.EMPTY);}
        if(level instanceof ServerLevel)touchTopology();
    }
    public boolean hasStoredContents(){return locked()||energy()>0||items.stream().anyMatch(stack->!stack.isEmpty());}
    @Override public void setRemoved(){if(level instanceof ServerLevel server)ChunkRevisions.touchLoaded(server,worldPosition);super.setRemoved();}
    @Override public Component getDisplayName(){return Component.translatable("block.rematter."+tier.id());}
    @Override public @Nullable AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return canAccess(player)?new EnergyCubeMenu(id,inventory,this):null;}
    @Override public int getContainerSize(){return items.size();}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int amount){if(locked()||Transaction.getCurrentOpenedTransaction()!=null)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(items,slot,amount);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return locked()||Transaction.getCurrentOpenedTransaction()!=null?ItemStack.EMPTY:ContainerHelper.takeItem(items,slot);}
    @Override public void setItem(int slot,ItemStack stack){
        if(locked()||Transaction.getCurrentOpenedTransaction()!=null||!stack.isEmpty()&&(stack.getCount()!=1||!chargeable(stack)))return;
        items.set(slot,stack);setChanged();
    }
    @Override public void setItem(int slot,ItemStack stack,boolean insideTransaction){if(insideTransaction&&!locked())items.set(slot,stack);else setItem(slot,stack);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return !locked()&&chargeable(stack);}
    /** Vanilla hoppers reach WorldlyContainer directly, without the NeoForge capability gate. */
    @Override public int[] getSlotsForFace(Direction side){
        if(!live()||sideMode(side)==EnergyCubeSideMode.DISABLED)return new int[0];
        return java.util.stream.IntStream.range(0,items.size()).toArray();
    }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){
        return live()&&sideMode(side)==EnergyCubeSideMode.INPUT&&canPlaceItem(slot,stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){
        return live()&&sideMode(side)==EnergyCubeSideMode.OUTPUT&&fullyCharged(slot);
    }
    @Override public int getMaxStackSize(){return 1;}
    @Override public void clearContent(){if(!locked()&&Transaction.getCurrentOpenedTransaction()==null){items.replaceAll(stack->ItemStack.EMPTY);setChanged();}}
    @Override public boolean stillValid(Player player){return canAccess(player)&&Container.stillValidBlockEntity(this,player);}
}
