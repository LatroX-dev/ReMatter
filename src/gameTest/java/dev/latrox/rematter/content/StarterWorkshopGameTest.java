package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.content.preparation.PreparationRecipe;
import dev.latrox.rematter.content.preparation.PreparationWork;
import dev.latrox.rematter.research.ResearchService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ServerLevelData;

/** Play the starter guide with a crafting table, finite vanilla supplies and initially empty machine buffers. */
final class StarterWorkshopGameTest {
    private final GameTestHelper h;
    private final Player owner;
    private final Map<Item, Integer> stock = new LinkedHashMap<>();
    private final Map<Identifier, Recipe<?>> recipes = new HashMap<>();
    private final BlockPos bench = new BlockPos(3, 2, 2);
    private MachineBlockEntity generator;

    private StarterWorkshopGameTest(GameTestHelper h) {
        this.h = h;
        owner = h.makeMockPlayer(GameType.SURVIVAL);
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        stock.putAll(Map.of(Items.FLINT, 2, Items.IRON_INGOT, 48, Items.COPPER_INGOT, 48,
                Items.REDSTONE, 16, Items.GOLD_INGOT, 2, Items.SAND, 48, Items.CLAY_BALL, 48,
                Items.GLASS, 5, Items.DIAMOND, 1, Items.EMERALD, 3));
        h.getLevel().getServer().getRecipeManager().getRecipes().forEach(holder -> recipes.put(holder.id().identifier(), holder.value()));
    }

    static void run(GameTestHelper h) {
        new StarterWorkshopGameTest(h).makeGlass();
        h.succeed();
    }

    private void makeGlass() {
        craft("rematter:hand_preparator", 1);
        var hand = place(MachineKind.HAND_PREPARATOR, new BlockPos(3, 2, 3));
        owner.setPos(hand.getBlockPos().getX() + .5, hand.getBlockPos().getY(), hand.getBlockPos().getZ() + .5);
        prepare(hand, "silith", 31);
        prepare(hand, "argil", 30);
        prepare(hand, "matrix_blend", 24);
        prepare(hand, "matrix_crystal", 87);
        prepare(hand, "ember_matrix_crystal", 1);
        prepare(hand, "gold_dust", 2);
        prepare(hand, "amber_matrix_crystal", 2);
        prepare(hand, "copper_dust", 2);
        prepare(hand, "copper_matrix_crystal", 2);

        craft("minecraft:copper_block", 2);
        craft("rematter:structural_bars", 3);
        craft("rematter:machine_frame", 3);
        craft("rematter:control_circuit", 3);
        craft("rematter:crystal_generator", 1);
        craft("rematter:material_preparator", 1);
        generator = place(MachineKind.CRYSTAL_GENERATOR, bench.east());
        var prep = place(MachineKind.PREPARATOR, bench);
        refuel(45);
        prepare(prep, "diamond_powder", 1);
        prepare(prep, "emerald_dust", 3);
        prepare(prep, "mint_matrix_crystal", 1);
        prepare(prep, "leaf_matrix_crystal", 3);

        craft("rematter:cell_rack", 1);
        craft("rematter:cell_housing", 1);
        craft("rematter:matter_cell_64k", 1);
        craft("rematter:matter_drive", 1);
        craft("rematter:decomposer", 1);
        craft("rematter:material_synthesizer", 1);
        var drive = place(MachineKind.DRIVE, bench.west());
        drive.setItem(drive.cellSlotForBay(0), take(ModContent.MATTER_CELL_64K.get(), 1));

        // Give each processor the generator's output in turn, as a player can by moving the machines.
        var decomposer = place(MachineKind.DECOMPOSER, bench);
        decomposer.setItem(0, take(Items.SAND, 1));
        refuel(11);
        for (int i = 0; i < 1000 && !decomposer.getItem(0).isEmpty(); i++) tick(decomposer);
        h.assertTrue(decomposer.getItem(0).isEmpty() && drive.matterStore().amount(BuiltinChemistry.SILICA) == 1_000,
                "Sand did not decompose with starter generator power");
        h.assertTrue(ResearchService.allowsSynthesis(h.getLevel().getServer(), owner.getUUID(), "minecraft:glass"),
                "Decomposing sand did not unlock glass for its owner");

        var synth = place(MachineKind.SYNTHESIZER, bench);
        refuel(21);
        h.assertTrue(synth.selectRecipe("minecraft:glass") && synth.requestBatch(), "Could not request glass");
        for (int i = 0; i < 1200 && synth.getItem(1).isEmpty(); i++) tick(synth);
        h.assertTrue(ItemStack.matches(new ItemStack(Items.GLASS), synth.getItem(1)) && drive.matterStore().total() == 0,
                "One sand and the starter line must produce exactly one glass");
    }

    private void refuel(int count) {
        h.assertTrue(generator.getItem(0).isEmpty(), "The previous stage still has unspent fuel items");
        generator.setItem(0, take(ModContent.MATRIX_CRYSTAL.get(), count));
    }

    private MachineBlockEntity place(MachineKind kind, BlockPos pos) {
        var block = ModContent.MACHINES.get(kind).get();
        take(block.asItem(), 1);
        h.setBlock(pos, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var machine = h.getBlockEntity(pos, MachineBlockEntity.class);
        machine.placedBy(owner);
        return machine;
    }

    private ItemStack take(Item item, int count) {
        int available = stock.getOrDefault(item, 0);
        h.assertTrue(available >= count, "Insufficient " + item + " in survival inventory, have " + available + ", need " + count);
        stock.put(item, available - count);
        return new ItemStack(item, count);
    }

    private ItemStack take(Ingredient ingredient, int count) {
        var item = stock.entrySet().stream()
                .filter(entry -> entry.getValue() >= count && ingredient.test(new ItemStack(entry.getKey())))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalStateException("Workshop cannot supply " + count + " of " + ingredient));
        return take(item, count);
    }

    private void store(ItemStack result) {
        stock.merge(result.getItem(), result.getCount(), Integer::sum);
    }

    private void craft(String name, int batches) {
        var recipe = (ShapedRecipe) recipes.get(Identifier.parse(name));
        for (int n = 0; n < batches; n++) {
            var slots = recipe.getIngredients().stream()
                    .map(slot -> slot.map(ingredient -> take(ingredient, 1)).orElse(ItemStack.EMPTY)).toList();
            var input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), slots);
            h.assertTrue(recipe.matches(input, h.getLevel()), "Crafting grid failed for " + name);
            store(recipe.assemble(input));
        }
    }

    private void prepare(MachineBlockEntity machine, String name, int batches) {
        var recipe = (PreparationRecipe) recipes.get(Identifier.parse("rematter:" + name));
        for (int n = 0; n < batches; n++) {
            machine.setItem(0, take(recipe.primary(), recipe.primaryCount()));
            machine.setItem(3, recipe.secondary().map(ingredient -> take(ingredient, recipe.secondaryCount())).orElse(ItemStack.EMPTY));
            if (machine.kind() == MachineKind.HAND_PREPARATOR) {
                for (int work = 0; work < recipe.ticks(); work += PreparationWork.CRANK_WORK) {
                    for (int i = 0; i < PreparationWork.CRANK_COOLDOWN; i++) tick(machine);
                    h.assertTrue(machine.crank(owner), "Hand Preparator refused " + name);
                }
            } else {
                for (int i = 0; i < recipe.ticks() + 1000 && machine.getItem(1).isEmpty(); i++) tick(machine);
            }
            var output = machine.removeItem(1, 64);
            h.assertTrue(ItemStack.matches(recipe.result(), output), "Unexpected result from " + name + " in " + machine.kind());
            store(output);
        }
    }

    private void tick(MachineBlockEntity consumer) {
        var level = h.getLevel();
        ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1);
        if (generator != null) generator.serverTick();
        consumer.serverTick();
    }
}
