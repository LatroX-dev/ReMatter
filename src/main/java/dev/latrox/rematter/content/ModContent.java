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
    static {
        for (MachineKind kind : MachineKind.values()) {
            var block = BLOCKS.registerBlock(kind.id(), p -> new MachineBlock(kind, p),
                    p -> p.mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL));
            MACHINES.put(kind, block);
            ITEMS.registerSimpleBlockItem(block, p -> p.stacksTo(1));
        }
    }
    public static final DeferredBlock<Block> MATTER_PIPE = BLOCKS.registerSimpleBlock("matter_pipe",
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.0F).sound(SoundType.METAL));
    public static final DeferredBlock<Block> REACTOR_CASING = BLOCKS.registerSimpleBlock("reactor_casing",
            p -> p.mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> MATTER_PIPE_ITEM = ITEMS.registerSimpleBlockItem(MATTER_PIPE);
    public static final DeferredItem<BlockItem> REACTOR_CASING_ITEM = ITEMS.registerSimpleBlockItem(REACTOR_CASING);
    public static final DeferredItem<Item> SPEED_UPGRADE = ITEMS.registerSimpleItem("speed_upgrade", p -> p.stacksTo(4));
    public static final DeferredItem<Item> EFFICIENCY_UPGRADE = ITEMS.registerSimpleItem("efficiency_upgrade", p -> p.stacksTo(4));
    public static final DeferredItem<TutorialBookItem> GUIDE = ITEMS.registerItem("guide", TutorialBookItem::new);
    public static final Supplier<BlockEntityType<MachineBlockEntity>> MACHINE_ENTITY = BLOCK_ENTITIES.register("machine",
            () -> new BlockEntityType<>(MachineBlockEntity::new, false, MACHINES.values().stream().map(Supplier::get).toArray(Block[]::new)));
    public static final Supplier<MenuType<MachineMenu>> MACHINE_MENU = MENUS.register("machine",
            () -> new MenuType<>(MachineMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<CreativeModeTab> TAB = TABS.register("rematter",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.rematter"))
                    .icon(() -> new ItemStack(MACHINES.get(MachineKind.DECOMPOSER).get()))
                    .displayItems((parameters, output) -> {
                        MACHINES.values().forEach(block -> output.accept(block.get()));
                        output.accept(MATTER_PIPE_ITEM.get());
                        output.accept(REACTOR_CASING_ITEM.get());
                        output.accept(SPEED_UPGRADE.get());
                        output.accept(EFFICIENCY_UPGRADE.get());
                        output.accept(GUIDE.get());
                    }).build());

    private ModContent() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        bus.addListener(ModContent::registerCapabilities);
        bus.addListener(MachineSnapshot::register);
    }
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MACHINE_ENTITY.get(), (machine, side) -> machine.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, MACHINE_ENTITY.get(), (machine, side) -> machine.automation() && side != null ? machine.automatedItems(side) : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MACHINE_ENTITY.get(), (machine, side) -> machine.automation() ? machine.automatedFluids() : null);
    }
}
