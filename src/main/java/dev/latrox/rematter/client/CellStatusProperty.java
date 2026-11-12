package dev.latrox.rematter.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.latrox.rematter.content.*;
import dev.latrox.rematter.primal.PrimalCellItem;
import dev.latrox.rematter.primal.PrimalCells;
import java.util.Locale;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/** Inventory and held cells read their own component. Mounted cells are drawn by the Drive, not as items. */
@EventBusSubscriber(modid="rematter",value=Dist.CLIENT)
public final class CellStatusProperty implements SelectItemModelProperty<String> {
    private static final CellStatusProperty INSTANCE=new CellStatusProperty();
    private static final Type<CellStatusProperty,String> TYPE=Type.create(MapCodec.unit(INSTANCE),Codec.STRING);
    private record Cached(@Nullable MatterCellData data,int count,CellStatus status) {}
    private final WeakHashMap<ItemStack,Cached> cache=new WeakHashMap<>();

    @SubscribeEvent public static void register(RegisterSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath("rematter","cell_status"),TYPE);
    }
    @Override public String get(ItemStack stack,@Nullable ClientLevel level,@Nullable LivingEntity owner,int seed,ItemDisplayContext context) {
        if(stack.getItem() instanceof PrimalCellItem) {
            var view=PrimalCells.view(stack);
            return name(CellStatus.of(view.installed(),view.readable(),view.amount(),view.capacity(),0,1));
        }
        if(!(stack.getItem() instanceof MatterCellItem))return "unlit";
        var data=stack.get(MatterCellData.TYPE.get());
        var previous=cache.get(stack);
        if(previous!=null&&previous.data==data&&previous.count==stack.getCount())return name(previous.status);
        CellStatus status=CellStatus.LOCKED;
        if(stack.getItem() instanceof MatterCellItem item) {
            var inventory=item.open(stack);
            if(inventory.isPresent()) {
                var value=inventory.get();
                status=CellStatus.of(true,true,value.total(),value.capacity(),value.typeCount(),value.maximumTypes());
            }
        }
        cache.put(stack,new Cached(data,stack.getCount(),status));
        return name(status);
    }
    private static String name(CellStatus status) { return status.name().toLowerCase(Locale.ROOT); }
    @Override public Codec<String> valueCodec() { return Codec.STRING; }
    @Override public Type<CellStatusProperty,String> type() { return TYPE; }
}
