package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ReMatter.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReMatter.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ReMatter.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ReMatter.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ReMatter.MOD_ID);
    public static final Map<MachineKind, DeferredBlock<MachineBlock>> MACHINES = new EnumMap<>(MachineKind.class);
    public static final Map<IndustrialModuleKind, DeferredBlock<IndustrialModuleBlock>> INDUSTRIAL_MODULES = new EnumMap<>(IndustrialModuleKind.class);
    static {
        for (MachineKind kind : MachineKind.values()) {
            var block = BLOCKS.registerBlock(kind.id(), p -> new MachineBlock(kind, p),
                    p -> p.mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).noOcclusion());
            MACHINES.put(kind, block);
            ITEMS.registerSimpleBlockItem(block, p -> p.stacksTo(1));
        }
        for (IndustrialModuleKind kind : IndustrialModuleKind.values()) {
            var block = BLOCKS.registerBlock(kind.id(), p -> new IndustrialModuleBlock(kind, p),
                    p -> p.mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).noOcclusion());
            INDUSTRIAL_MODULES.put(kind, block);
            ITEMS.registerSimpleBlockItem(block);
        }
    }
    public static final DeferredBlock<MatterPipeBlock> MATTER_PIPE = BLOCKS.registerBlock("matter_pipe", MatterPipeBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<Block> REACTOR_CASING = BLOCKS.registerSimpleBlock("reactor_casing",
            p -> p.mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> MATTER_PIPE_ITEM = ITEMS.registerSimpleBlockItem(MATTER_PIPE);
    public static final DeferredItem<BlockItem> REACTOR_CASING_ITEM = ITEMS.registerSimpleBlockItem(REACTOR_CASING);
    public static final DeferredBlock<HalfTransparentBlock> REACTOR_GLASS = BLOCKS.registerBlock("reactor_glass", HalfTransparentBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(3.0F, 12.0F).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredBlock<MachinePortBlock> MACHINE_PORT = BLOCKS.registerBlock("machine_port", MachinePortBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> REACTOR_GLASS_ITEM = ITEMS.registerSimpleBlockItem(REACTOR_GLASS);
    public static final DeferredItem<BlockItem> MACHINE_PORT_ITEM = ITEMS.registerSimpleBlockItem(MACHINE_PORT);
    public static final DeferredItem<Item> SPEED_UPGRADE = ITEMS.registerSimpleItem("speed_upgrade", p -> p.stacksTo(4));
    public static final DeferredItem<Item> EFFICIENCY_UPGRADE = ITEMS.registerSimpleItem("efficiency_upgrade", p -> p.stacksTo(4));
    public static final DeferredItem<Item> MATRIX_BLEND = ITEMS.registerSimpleItem("matrix_blend");
    public static final DeferredItem<Item> MATRIX_CRYSTAL = ITEMS.registerSimpleItem("matrix_crystal");
    public static final DeferredItem<Item> MATRIX_CLUSTER = ITEMS.registerSimpleItem("matrix_cluster");
    public static final DeferredItem<Item> SILITH = ITEMS.registerSimpleItem("silith");
    public static final DeferredItem<Item> ARGIL = ITEMS.registerSimpleItem("argil");
    public static final DeferredItem<Item> RHEDON = ITEMS.registerSimpleItem("rhedon");
    public static final DeferredItem<Item> VIREL = ITEMS.registerSimpleItem("virel");
    public static final DeferredItem<Item> LUMIN = ITEMS.registerSimpleItem("lumin");
    public static final DeferredItem<Item> OPTICAL_CIRCUIT = ITEMS.registerSimpleItem("optical_circuit");
    public static final DeferredItem<Item> MACHINE_FRAME = ITEMS.registerSimpleItem("machine_frame");
    public static final DeferredItem<Item> CONTROL_CIRCUIT = ITEMS.registerSimpleItem("control_circuit");
    public static final DeferredItem<MatterCellItem> MATTER_CELL_64K = ITEMS.registerItem("matter_cell_64k", p -> new MatterCellItem(p, 64_000L));
    public static final DeferredItem<MatterCellItem> MATTER_CELL_256K = ITEMS.registerItem("matter_cell_256k", p -> new MatterCellItem(p, 256_000L));
    public static final DeferredItem<MatterCellItem> MATTER_CELL_1M = ITEMS.registerItem("matter_cell_1m", p -> new MatterCellItem(p, 1_000_000L));
    public static final DeferredItem<TutorialBookItem> GUIDE = ITEMS.registerItem("guide", TutorialBookItem::new);
    public static final Supplier<BlockEntityType<MachineBlockEntity>> MACHINE_ENTITY = BLOCK_ENTITIES.register("machine",
            () -> new BlockEntityType<>(MachineBlockEntity::new, false, MACHINES.values().stream().map(Supplier::get).toArray(Block[]::new)));
    public static final Supplier<MenuType<MachineMenu>> MACHINE_MENU = MENUS.register("machine",
            () -> new MenuType<>(MachineMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<BlockEntityType<MachinePortBlockEntity>> MACHINE_PORT_ENTITY = BLOCK_ENTITIES.register("machine_port",
            () -> new BlockEntityType<>(MachinePortBlockEntity::new, false, MACHINE_PORT.get()));
    public static final Supplier<BlockEntityType<IndustrialModuleBlockEntity>> INDUSTRIAL_MODULE_ENTITY = BLOCK_ENTITIES.register("industrial_module",
            () -> new BlockEntityType<>(IndustrialModuleBlockEntity::new, false, INDUSTRIAL_MODULES.values().stream().map(Supplier::get).toArray(Block[]::new)));
    public static final Supplier<CreativeModeTab> TAB = TABS.register("rematter",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.rematter"))
                    .icon(() -> new ItemStack(MACHINES.get(MachineKind.DECOMPOSER).get()))
                    .displayItems((parameters, output) -> {
                        MACHINES.values().forEach(block -> output.accept(block.get()));
                        output.accept(MATTER_PIPE_ITEM.get());
                        output.accept(REACTOR_CASING_ITEM.get());
                        output.accept(REACTOR_GLASS_ITEM.get());
                        output.accept(MACHINE_PORT_ITEM.get());
                        INDUSTRIAL_MODULES.values().forEach(block -> output.accept(block.get()));
                        output.accept(SPEED_UPGRADE.get());
                        output.accept(EFFICIENCY_UPGRADE.get());
                        output.accept(MATRIX_BLEND.get());
                        output.accept(MATRIX_CRYSTAL.get());
                        output.accept(MATRIX_CLUSTER.get());
                        output.accept(SILITH.get());
                        output.accept(ARGIL.get());
                        output.accept(RHEDON.get());
                        output.accept(VIREL.get());
                        output.accept(LUMIN.get());
                        output.accept(OPTICAL_CIRCUIT.get());
                        output.accept(MACHINE_FRAME.get());
                        output.accept(CONTROL_CIRCUIT.get());
                        output.accept(MATTER_CELL_64K.get());
                        output.accept(MATTER_CELL_256K.get());
                        output.accept(MATTER_CELL_1M.get());
                        output.accept(GUIDE.get());
                    }).build());

    private ModContent() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        MatterCellData.register(bus);
        dev.latrox.rematter.content.preparation.PreparationRecipes.register(bus);
        bus.addListener(ModContent::registerCapabilities);
        bus.addListener(MachineSnapshot::register);
    }
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MACHINE_ENTITY.get(), (machine, side) -> machine.kind().processes()
                && !IndustrialStructure.isIndustrial(machine.kind()) && !dev.latrox.rematter.content.preparation.PreparationTier.manual(machine.kind()) ? machine.energy() : null);
        event.registerBlockEntity(Capabilities.Item.BLOCK, MACHINE_ENTITY.get(), (machine, side) -> !IndustrialStructure.isIndustrial(machine.kind())
                && machine.kind() != MachineKind.DRIVE && machine.automation() && side != null ? machine.automatedItems(side) : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MACHINE_ENTITY.get(), (machine, side) -> machine.automation()
                && !IndustrialStructure.isIndustrial(machine.kind())
                && (machine.kind() == MachineKind.DECOMPOSER || machine.kind() == MachineKind.SYNTHESIZER || machine.fluids().getAmountAsLong(0) > 0)
                ? machine.automatedFluids() : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MACHINE_PORT_ENTITY.get(), MachinePortBlockEntity::energy);
        event.registerBlockEntity(Capabilities.Item.BLOCK, MACHINE_PORT_ENTITY.get(), MachinePortBlockEntity::items);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MACHINE_PORT_ENTITY.get(), MachinePortBlockEntity::fluids);
    }
}
