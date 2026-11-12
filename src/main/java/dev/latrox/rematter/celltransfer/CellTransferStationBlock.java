package dev.latrox.rematter.celltransfer;

import com.mojang.serialization.MapCodec;
import dev.latrox.rematter.content.ModContent;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** A manual workshop device; installed Cells and their formatting travel intact with the block. */
public final class CellTransferStationBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<CellTransferStationBlock> CODEC=simpleCodec(CellTransferStationBlock::new);
    public CellTransferStationBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CellTransferStationBlockEntity(pos,state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station)station.placedBy(placer);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station){
            if(player instanceof ServerPlayer server&&station.interact(player))server.openMenu(station,b->b.writeBlockPos(pos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    public ItemStack storedDrop(CellTransferStationBlockEntity station,HolderLookup.Provider registries){
        var drop=new ItemStack(this);drop.applyComponents(station.collectComponents());var raw=station.saveCustomOnly(registries);raw.remove("owner");
        drop.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(station.getType(),raw));return drop;
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){return builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CellTransferStationBlockEntity station?List.of(storedDrop(station,builder.getLevel().registryAccess())):List.of(new ItemStack(this));}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData,Player player){return includeData?ItemStack.EMPTY:new ItemStack(this);}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.isCreative()&&level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station&&station.hasStoredContents())popResource(level,pos,storedDrop(station,level.registryAccess()));
        return super.playerWillDestroy(level,pos,state,player);
    }
}
