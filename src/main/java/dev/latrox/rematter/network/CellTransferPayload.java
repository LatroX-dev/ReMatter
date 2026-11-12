package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferSelection;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.CellPartitionData;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Exact Cell quantities and selections are sent only to the currently authorized station viewer. */
public record CellTransferPayload(int containerId,long revision,String status,boolean canTransfer,boolean locked,boolean partial,
                                  CellTransferSelection selection,List<SlotView> slots,List<Entry> available,
                                  long movableOrdinary,long movablePrimal,long movedOrdinaryLast,long movedPrimalLast) implements CustomPacketPayload {
    public static final Type<CellTransferPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"cell_transfer_station"));
    public static final int MAX_ROWS=2048;
    public static final Set<String> STATUSES=Set.of("unavailable","insert_cells","type_mismatch","locked","empty_source","target_full","same_cell","no_selection","filtered","ready","transferred");
    public record SlotView(int kind,boolean readable,long amount,long capacity,int types){
        public static final SlotView EMPTY=new SlotView(0,true,0,0,0);
        public SlotView{if(kind<0||kind>3||amount<0||capacity<0||amount>capacity||types<0||types>512||kind==0&&(amount!=0||capacity!=0||types!=0))throw new IllegalArgumentException("Invalid Cell slot view");}
    }
    public record Entry(SubstanceKey key,long amount){public Entry{Objects.requireNonNull(key);if(amount<0||key.id().toString().length()>CellPartitionData.MAX_ID_LENGTH||ReservedSubstances.reserved(key))throw new IllegalArgumentException("Invalid station row");}}
    public CellTransferPayload {
        slots=List.copyOf(slots);available=List.copyOf(available);Objects.requireNonNull(selection);
        if(containerId<0||revision<0||!STATUSES.contains(status)||slots.size()!=6||available.size()>MAX_ROWS||movableOrdinary<0||movablePrimal<0||movedOrdinaryLast<0||movedPrimalLast<0||canTransfer&&locked)
            throw new IllegalArgumentException("Invalid Cell Transfer snapshot");
        if(available.stream().map(Entry::key).distinct().count()!=available.size())throw new IllegalArgumentException("Duplicate station row");
    }
    public static void writeKey(RegistryFriendlyByteBuf buffer,SubstanceKey key){buffer.writeUtf(key.id().toString(),CellPartitionData.MAX_ID_LENGTH);buffer.writeEnum(key.phase());}
    public static SubstanceKey readKey(RegistryFriendlyByteBuf buffer){return new SubstanceKey(SubstanceId.parse(buffer.readUtf(CellPartitionData.MAX_ID_LENGTH)),buffer.readEnum(Phase.class));}
    public static final StreamCodec<RegistryFriendlyByteBuf,CellTransferPayload> CODEC=new StreamCodec<>(){
        public CellTransferPayload decode(RegistryFriendlyByteBuf b){
            int id=b.readVarInt();long revision=b.readVarLong();String status=b.readUtf(32);boolean can=b.readBoolean(),locked=b.readBoolean(),partial=b.readBoolean(),all=b.readBoolean(),primal=b.readBoolean();
            int count=b.readVarInt();if(count<0||count>CellPartitionData.MAX_KEYS)throw new IllegalArgumentException("Too many selected substances");
            var selected=new HashSet<SubstanceKey>();for(int i=0;i<count;i++)if(!selected.add(readKey(b)))throw new IllegalArgumentException("Duplicate selection");
            var slots=new ArrayList<SlotView>();for(int i=0;i<6;i++)slots.add(new SlotView(b.readVarInt(),b.readBoolean(),b.readVarLong(),b.readVarLong(),b.readVarInt()));
            count=b.readVarInt();if(count<0||count>MAX_ROWS)throw new IllegalArgumentException("Too many station rows");
            var rows=new ArrayList<Entry>();for(int i=0;i<count;i++)rows.add(new Entry(readKey(b),b.readVarLong()));
            return new CellTransferPayload(id,revision,status,can,locked,partial,new CellTransferSelection(all,primal,selected),slots,rows,b.readVarLong(),b.readVarLong(),b.readVarLong(),b.readVarLong());
        }
        public void encode(RegistryFriendlyByteBuf b,CellTransferPayload p){
            b.writeVarInt(p.containerId);b.writeVarLong(p.revision);b.writeUtf(p.status,32);b.writeBoolean(p.canTransfer);b.writeBoolean(p.locked);b.writeBoolean(p.partial);b.writeBoolean(p.selection.all());b.writeBoolean(p.selection.primalSelected());
            b.writeVarInt(p.selection.keys().size());for(var key:new TreeSet<>(p.selection.keys()))writeKey(b,key);
            for(var slot:p.slots){b.writeVarInt(slot.kind);b.writeBoolean(slot.readable);b.writeVarLong(slot.amount);b.writeVarLong(slot.capacity);b.writeVarInt(slot.types);}
            b.writeVarInt(p.available.size());for(var row:p.available){writeKey(b,row.key);b.writeVarLong(row.amount);}
            b.writeVarLong(p.movableOrdinary);b.writeVarLong(p.movablePrimal);b.writeVarLong(p.movedOrdinaryLast);b.writeVarLong(p.movedPrimalLast);
        }
    };
    @Override public Type<CellTransferPayload> type(){return TYPE;}
}
