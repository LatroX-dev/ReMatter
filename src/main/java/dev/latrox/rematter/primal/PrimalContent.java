package dev.latrox.rematter.primal;

import dev.latrox.rematter.ReMatter;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Separate registrations keep Primal reserve types outside ordinary chemistry and machine slots. */
public final class PrimalContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ReMatter.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReMatter.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ReMatter.MOD_ID);
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, ReMatter.MOD_ID);
    public static final Supplier<PlacementModifierType<PrimalDimensionFilter>> DIMENSION_FILTER = PLACEMENTS.register("primal_dimension", () -> () -> PrimalDimensionFilter.CODEC);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_PRIMAL_ORE = ore("deepslate_primal_ore", MapColor.DEEPSLATE, SoundType.DEEPSLATE);
    public static final DeferredBlock<DropExperienceBlock> NETHER_PRIMAL_ORE = ore("nether_primal_ore", MapColor.NETHER, SoundType.NETHERRACK);
    public static final DeferredBlock<DropExperienceBlock> END_PRIMAL_ORE = ore("end_primal_ore", MapColor.SAND, SoundType.STONE);
    public static final DeferredItem<BlockItem> DEEPSLATE_PRIMAL_ORE_ITEM = ITEMS.registerSimpleBlockItem(DEEPSLATE_PRIMAL_ORE);
    public static final DeferredItem<BlockItem> NETHER_PRIMAL_ORE_ITEM = ITEMS.registerSimpleBlockItem(NETHER_PRIMAL_ORE);
    public static final DeferredItem<BlockItem> END_PRIMAL_ORE_ITEM = ITEMS.registerSimpleBlockItem(END_PRIMAL_ORE);
    public static final DeferredItem<Item> PRIMAL_SHARD = ITEMS.registerSimpleItem("primal_shard");
    public static final DeferredItem<PrimalCellItem> PRIMAL_CELL_16K = ITEMS.registerItem("primal_cell_16k", p -> new PrimalCellItem(p, 16000));
    public static final DeferredItem<PrimalCellItem> PRIMAL_CELL_64K = ITEMS.registerItem("primal_cell_64k", p -> new PrimalCellItem(p, 64000));
    public static final DeferredItem<PrimalCellItem> PRIMAL_CELL_256K = ITEMS.registerItem("primal_cell_256k", p -> new PrimalCellItem(p, 256000));
    public static final DeferredItem<Item> PRIMAL_CRYSTAL = ITEMS.registerSimpleItem("primal_crystal");
    public static final DeferredItem<Item> DISCOVERY_LENS = ITEMS.registerSimpleItem("discovery_lens", p -> p.stacksTo(1));
    public static final DeferredBlock<EntangledLinkBlock> ENTANGLED_LINK = BLOCKS.registerBlock("entangled_link", EntangledLinkBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(4, 12).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<EntangledLinkItem> ENTANGLED_LINK_ITEM = ITEMS.registerItem("entangled_link", p -> new EntangledLinkItem(ENTANGLED_LINK.get(), p.useBlockDescriptionPrefix()));
    public static final Supplier<BlockEntityType<EntangledLinkBlockEntity>> ENTANGLED_LINK_ENTITY = ENTITIES.register("entangled_link",
            () -> new BlockEntityType<>(EntangledLinkBlockEntity::new, false, ENTANGLED_LINK.get()));
    private static DeferredBlock<DropExperienceBlock> ore(String id, MapColor color, SoundType sound) {
        return BLOCKS.registerBlock(id, p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
                p -> p.mapColor(color).strength(4.5F, 3).sound(sound).requiresCorrectToolForDrops().lightLevel(state -> 3));
    }
    public static List<Item> creativeItems() {
        return List.of(DEEPSLATE_PRIMAL_ORE_ITEM.get(), NETHER_PRIMAL_ORE_ITEM.get(), END_PRIMAL_ORE_ITEM.get(), PRIMAL_SHARD.get(),
                PRIMAL_CELL_16K.get(), PRIMAL_CELL_64K.get(), PRIMAL_CELL_256K.get(), PRIMAL_CRYSTAL.get(), DISCOVERY_LENS.get(), ENTANGLED_LINK_ITEM.get());
    }
    private static void capabilities(RegisterCapabilitiesEvent event){
        event.registerBlock(Capabilities.Energy.BLOCK,(level,pos,state,entity,side)->dev.latrox.rematter.content.MatterCablePower.capability(level,pos,side),ENTANGLED_LINK.get());
    }
    public static void register(IEventBus bus) { bus.addListener(PrimalContent::capabilities); BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); PLACEMENTS.register(bus); PrimalCellData.register(bus); }
    private PrimalContent() {}
}
