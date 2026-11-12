package dev.latrox.rematter.energy;

import dev.latrox.rematter.content.*;
import dev.latrox.rematter.network.EnergyCubePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Fixed tier-aware slot layout; the server validates every side action and transfer. */
public final class EnergyCubeMenu extends AbstractContainerMenu {
    private final EnergyCubeTier tier;
    private final Container inventory;
    private final @Nullable EnergyCubeBlockEntity cube;
    private final Player player;
    private final BlockPos position;
    private @Nullable EnergyCubePayload received,sent;
    private long nextUpdate;
    private boolean settings;
    public void setSettings(boolean value){settings=value;}
    public EnergyCubeMenu(int id,Inventory inventory,RegistryFriendlyByteBuf data){this(id,inventory,data.readBlockPos(),EnergyCubeTier.byIndex(data.readVarInt()));}
    private EnergyCubeMenu(int id,Inventory inventory,BlockPos position,EnergyCubeTier tier){this(id,inventory,new SimpleContainer(tier.chargingSlots()),null,position,tier);}
    public EnergyCubeMenu(int id,Inventory inventory,EnergyCubeBlockEntity cube){this(id,inventory,cube,cube,cube.getBlockPos(),cube.tier());}
    private EnergyCubeMenu(int id,Inventory playerInventory,Container inventory,@Nullable EnergyCubeBlockEntity cube,BlockPos position,EnergyCubeTier tier){
        super(ModContent.ENERGY_CUBE_MENU.get(),id);this.tier=tier;this.inventory=inventory;this.cube=cube;this.position=position;player=playerInventory.player;
        int count=tier.chargingSlots(),x=(216-((count-1)*36+16))/2;
        for(int i=0;i<count;i++)addSlot(new Slot(inventory,i,x+i*36,80){
            @Override public boolean mayPlace(ItemStack stack){return !locked()&&EnergyCubeBlockEntity.chargeable(stack);}
            @Override public boolean mayPickup(Player player){return cube==null||cube.canAccess(player)&&!cube.locked();}
            @Override public int getMaxStackSize(){return 1;}
            @Override public boolean isActive(){return !settings;}
        });
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(playerInventory,column+row*9+9,18+column*18,135+row*18));
        for(int column=0;column<9;column++)addSlot(new Slot(playerInventory,column,18+column*18,193));
    }
    public EnergyCubeTier tier(){return tier;}
    public int energy(){return cube!=null?cube.energy():received==null?0:received.energy();}
    public int capacity(){return tier.capacity();}
    public int transferRate(){return tier.transferRate();}
    public boolean locked(){return cube!=null?cube.locked():received!=null&&received.locked();}
    public BlockPos position(){return position;}
    public EnergyCubeSideMode sideMode(MachinePortMode.Side side){
        int packed=cube!=null?cube.packedSideModes():received==null?EnergyCubeSideMode.OUTPUT.ordinal():received.sideModes();
        return EnergyCubeSideMode.values()[(packed>>>(side.ordinal()*2))&3];
    }
    public void accept(EnergyCubePayload payload){if(payload.containerId()==containerId&&payload.tier()==tier.ordinal())received=payload;}
    @Override public boolean stillValid(Player player){return cube==null||cube.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int action){
        if(cube==null||player.containerMenu!=this||player.isSpectator()||action<0||action>=MachinePortMode.Side.values().length||!stillValid(player))return false;
        boolean changed=cube.cycleSide(player,MachinePortMode.Side.values()[action]);
        if(changed){nextUpdate=0;broadcastChanges();}return changed;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||locked()||cube!=null&&(!cube.canAccess(player)||!stillValid(player)))return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        var stack=slot.getItem();var before=stack.copy();int count=tier.chargingSlots();
        if(index<count){if(!moveItemStackTo(stack,count,slots.size(),true))return ItemStack.EMPTY;}
        else if(!EnergyCubeBlockEntity.chargeable(stack)||!moveItemStackTo(stack,0,count,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);return before;
    }
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(cube==null||!(player instanceof ServerPlayer server)||!stillValid(player))return;
        long time=server.level().getGameTime();if(time<nextUpdate)return;
        nextUpdate=time+4;
        var payload=new EnergyCubePayload(containerId,tier.ordinal(),cube.energy(),cube.packedSideModes(),cube.locked());
        if(!payload.equals(sent)){PacketDistributor.sendToPlayer(server,payload);sent=payload;}
    }
}
