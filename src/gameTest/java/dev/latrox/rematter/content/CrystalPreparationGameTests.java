package dev.latrox.rematter.content;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.preparation.PreparationRecipe;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.content.preparation.PreparationWork;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Actual reloaded crystal recipes and machine commits, including the unpowered bootstrap. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class CrystalPreparationGameTests {
    private static final List<String> CRYSTALS = List.of("matrix_crystal", "ice_matrix_crystal", "copper_matrix_crystal",
            "leaf_matrix_crystal", "violet_matrix_crystal", "amber_matrix_crystal", "ember_matrix_crystal",
            "blue_matrix_crystal", "mint_matrix_crystal");
    private static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of(
            "matrix_crystals_are_preparation_only", CrystalPreparationGameTests::recipes,
            "hand_preparator_bootstraps_amber_without_fe", CrystalPreparationGameTests::manual,
            "powered_crystal_batch_commits_at_640_ticks", CrystalPreparationGameTests::powered,
            "four_speed_upgrades_require_128_crystal_ticks", CrystalPreparationGameTests::speed);
    private static Identifier id(String name) { return Identifier.parse("rematter_tests:crystal_preparation/" + name); }
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> TESTS.forEach((name, body) -> registry.register(id(name), body)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        TESTS.forEach((name, body) -> {
            // These tests advance game time themselves; each owns an isolated serial environment.
            var environment = event.registerEnvironment(id(name + "_batch"));
            event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                    new TestData<>(environment, Identifier.parse("rematter_tests:empty"), 100, 0, true, Rotation.NONE, false, 1, 1, true, 2)));
        });
    }
    private static ItemStack item(String name, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("rematter:" + name)), count);
    }
    private static PreparationRecipe recipe(GameTestHelper h, String name) {
        var key = Identifier.parse("rematter:" + name);
        return PreparationRecipes.serverRecipes(h.getLevel()).stream().filter(entry -> entry.id().equals(key))
                .findFirst().orElseThrow(() -> new IllegalStateException("Actual reload omitted " + key)).recipe();
    }
    private static void recipes(GameTestHelper h) {
        var crystalIds = new HashSet<String>();
        for (var name : CRYSTALS) {
            crystalIds.add("rematter:" + name);
            var recipe = recipe(h, name);
            h.assertTrue(recipe.getType() == PreparationRecipes.TYPE.get(), name + " has a different recipe type");
            h.assertTrue(ItemStack.matches(item(name, 1), recipe.result()), name + " does not produce exactly one plain crystal");
            h.assertTrue(recipe.ticks() == 640 && recipe.energy() == 4000, name + " has the wrong shipped work or FE price");
        }
        // Inspect the actual recipe reload, including vanilla crafting/cooking and any added fixed-output alternatives.
        var context = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var holder : h.getLevel().getServer().getRecipeManager().getRecipes()) {
            var serialized = Recipe.CODEC.encodeStart(context, holder.value()).getOrThrow().getAsJsonObject();
            var outputs = new HashSet<String>();
            for (var field : List.of("result", "results")) if (serialized.has(field)) collectOutputs(serialized.get(field), outputs);
            if (outputs.stream().anyMatch(crystalIds::contains))
                h.assertTrue(holder.value() instanceof PreparationRecipe,
                        "A crystal bypasses physical preparation through " + holder.id().identifier());
        }
        h.succeed();
    }
    private static void collectOutputs(JsonElement value, Set<String> outputs) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(entry -> collectOutputs(entry, outputs));
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) outputs.add(value.getAsString());
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> collectOutputs(entry.getValue(), outputs));
    }
    private static MachineBlockEntity machine(GameTestHelper h, MachineKind kind, Player owner) {
        var pos = new BlockPos(3, 3, 3);
        h.setBlock(pos, ModContent.MACHINES.get(kind).get());
        var machine = h.getBlockEntity(pos, MachineBlockEntity.class);
        machine.placedBy(owner);
        owner.setPos(machine.getBlockPos().getX() + .5, machine.getBlockPos().getY(), machine.getBlockPos().getZ() + .5);
        return machine;
    }
    private static void nextTick(GameTestHelper h, MachineBlockEntity machine) {
        var level = h.getLevel();
        ((ServerLevelData) level.getLevelData()).setGameTime(Math.incrementExact(level.getGameTime()));
        machine.serverTick();
    }
    private static void manualBatch(GameTestHelper h, MachineBlockEntity machine, Player owner, String name) {
        var recipe = recipe(h, name);
        var primary = machine.getItem(0).copy(); var secondary = machine.getItem(3).copy();
        int cranks = (recipe.ticks() + 79) / 80;
        long sequence = machine.visualCrankSequence();
        for (int crank = 1; crank <= cranks; crank++) {
            // The first crank may arrive immediately; subsequent cranks obey the real five-tick cooldown.
            for (int tick = 0; tick < 5; tick++) nextTick(h, machine);
            h.assertTrue(machine.crank(owner), name + ": accepted crank refused at " + crank);
            h.assertTrue(machine.energy().getAmountAsLong() == 0, name + ": hand processing acquired or spent FE");
            h.assertTrue(machine.visualCrankSequence() == sequence + crank, name + ": crank counter differs");
            if (crank < cranks) {
                h.assertTrue(machine.work().progress == crank * 80, name + ": one crank did not add exactly eighty work");
                h.assertTrue(machine.getItem(1).isEmpty() && ItemStack.matches(primary, machine.getItem(0))
                        && ItemStack.matches(secondary, machine.getItem(3)), name + ": inputs committed before the final crank");
            }
        }
        h.assertTrue(ItemStack.matches(recipe.result(), machine.getItem(1)), name + ": wrong batch output or count");
        h.assertTrue(machine.getItem(0).isEmpty() && machine.getItem(3).isEmpty(), name + ": input debit was not exact");
        h.assertTrue(machine.work().progress == 0 && machine.work().duration == 0, name + ": completed work was retained");
    }
    private static void manual(GameTestHelper h) {
        h.assertTrue(PreparationWork.CRANK_WORK == 80, "The intended hand-work cadence changed");
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var machine = machine(h, MachineKind.HAND_PREPARATOR, owner);
        machine.setItem(0, item("silith", 1)); machine.setItem(3, item("argil", 1));
        manualBatch(h, machine, owner, "matrix_blend");
        h.assertTrue(machine.getItem(1).getCount() == 4, "Bootstrap mixture did not yield four blends");
        var blends = machine.removeItem(1, 4);
        machine.setItem(0, blends.split(1)); owner.getInventory().setItem(0, blends);
        manualBatch(h, machine, owner, "matrix_crystal");
        machine.setItem(0, machine.removeItem(1, 1)); machine.setItem(3, item("gold_dust", 1));
        manualBatch(h, machine, owner, "amber_matrix_crystal");
        h.assertTrue(ItemStack.matches(item("matrix_blend", 3), owner.getInventory().getItem(0)), "Unused blend was lost during the bootstrap");
        h.assertTrue(machine.visualCrankSequence() == 22, "Bootstrap did not complete with exactly six plus eight plus eight cranks");
        h.succeed();
    }
    private static void powered(GameTestHelper h) { powered(h, false); }
    private static void speed(GameTestHelper h) { powered(h, true); }
    private static void powered(GameTestHelper h, boolean upgraded) {
        var machine = machine(h, MachineKind.PREPARATOR, h.makeMockPlayer(GameType.SURVIVAL));
        var recipe = recipe(h, "amber_matrix_crystal");
        machine.setItem(0, item("matrix_crystal", 2)); machine.setItem(3, item("gold_dust", 2));
        if (upgraded) machine.setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        int ticks = upgraded ? 128 : 640;
        int price = upgraded ? 8000 : 4000;
        machine.energy().set(price);
        h.assertTrue(recipe.ticks() == 640 && recipe.energy() == 4000, "Test no longer uses the shipped crystal recipe");
        h.assertTrue(machine.speed() == (upgraded ? 5 : 1), "Preparation speed does not match the upgrade stack");
        for (int tick = 1; tick < ticks; tick++) {
            nextTick(h, machine);
            h.assertTrue(machine.getItem(1).isEmpty() && machine.getItem(0).getCount() == 2 && machine.getItem(3).getCount() == 2,
                    "Physical inputs or output committed before tick " + ticks + " (at " + tick + ")");
            h.assertTrue(machine.energy().getAmountAsLong() == price, "FE was consumed before the completed batch");
            h.assertTrue(machine.work().progress == tick * (upgraded ? 5 : 1), "Work counter skipped or repeated a tick");
        }
        nextTick(h, machine);
        h.assertTrue(ItemStack.matches(item("amber_matrix_crystal", 1), machine.getItem(1)), "Completed batch did not produce exactly one crystal");
        h.assertTrue(machine.getItem(0).getCount() == 1 && machine.getItem(3).getCount() == 1, "Completed batch did not debit exactly one of each input");
        h.assertTrue(machine.energy().getAmountAsLong() == 0, "Completed batch did not debit exactly " + price + " FE");
        h.assertTrue(machine.work().progress == 0 && machine.work().duration == 0, "Work counters were not reset after completion");
        for (int tick = 0; tick < 20; tick++) nextTick(h, machine);
        h.assertTrue(machine.getItem(0).getCount() == 1 && machine.getItem(3).getCount() == 1 && machine.getItem(1).getCount() == 1
                && machine.energy().getAmountAsLong() == 0 && machine.work().progress == 0,
                "An unfunded second batch consumed inputs or produced another output");
        h.succeed();
    }
}
