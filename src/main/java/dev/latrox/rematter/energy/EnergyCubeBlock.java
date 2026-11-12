package dev.latrox.rematter.energy;

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
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Portable FE buffers retain their entire stored state when broken, without copying it through creative picks. */
public final class EnergyCubeBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CHARGE=IntegerProperty.create("charge",0,8);
    private final EnergyCubeTier tier;
    private final MapCodec<EnergyCubeBlock> codec;
    public EnergyCubeBlock(EnergyCubeTier tier,Properties properties){
        super(properties);this.tier=tier;codec=simpleCodec(p->new EnergyCubeBlock(tier,p));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(CHARGE,0));
    }
    public EnergyCubeTier tier(){return tier;}
    @Override protected MapCodec<? extends Block> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,CHARGE);}
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new EnergyCubeBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()||type!=ModContent.ENERGY_CUBE_ENTITY.get()?null:(world,pos,blockState,entity)->((EnergyCubeBlockEntity)entity).serverTick();
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof EnergyCubeBlockEntity cube)cube.placedBy(placer);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.getBlockEntity(pos) instanceof EnergyCubeBlockEntity cube){
            if(player instanceof ServerPlayer server&&cube.interact(player))server.openMenu(cube,buffer->{buffer.writeBlockPos(pos);buffer.writeVarInt(tier.ordinal());});
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    public ItemStack storedDrop(EnergyCubeBlockEntity cube,HolderLookup.Provider registries){
        var result=new ItemStack(this);result.applyComponents(cube.collectComponents());
        var data=cube.saveCustomOnly(registries);data.remove("owner");
        result.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(cube.getType(),data));
        return result;
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){
        return builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof EnergyCubeBlockEntity cube
                ?List.of(storedDrop(cube,builder.getLevel().registryAccess())):List.of(new ItemStack(this));
    }
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData,Player player){
        // Vanilla appends BE data after this hook. A data pick must stop before that append, even for the owner.
        return includeData?ItemStack.EMPTY:new ItemStack(this);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.isCreative()&&level.getBlockEntity(pos) instanceof EnergyCubeBlockEntity cube&&cube.hasStoredContents())
            popResource(level,pos,storedDrop(cube,level.registryAccess()));
        return super.playerWillDestroy(level,pos,state,player);
    }
}
