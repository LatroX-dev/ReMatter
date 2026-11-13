package dev.latrox.rematter.content;

import dev.latrox.rematter.content.MachinePortMode.Side;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeSideMode;
import dev.latrox.rematter.energy.EnergyCubeTier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The front of a device is never a connection, and neither is the open top of a bench: nothing is exposed,
 * inserted, extracted, pushed, linked or configured there. Energy Cubes are the one exception and keep all six.
 */
@EventBusSubscriber(modid = "rematter")
public final class PortSideGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("port_sides_closed_sides_expose_no_capability", PortSideGameTests::capabilities);
        TESTS.put("port_sides_hoppers_cannot_use_front_or_open_top", PortSideGameTests::hoppers);
        TESTS.put("port_sides_cables_skip_front_and_open_top", PortSideGameTests::cables);
        TESTS.put("port_sides_generator_never_pushes_through_front", PortSideGameTests::generatorPush);
        TESTS.put("port_sides_cached_generator_capability_obeys_current_role", PortSideGameTests::generatorCached);
        TESTS.put("port_sides_neighbours_never_link_through_front", PortSideGameTests::neighbours);
        TESTS.put("port_sides_server_refuses_closed_side_packets", PortSideGameTests::menuPackets);
        TESTS.put("port_sides_energy_cube_keeps_all_six_sides", PortSideGameTests::energyCube);
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, test) -> helper.register(id(name), test)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("port_sides_environment"));
        TESTS.forEach((name, test) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(environment, id("empty"), 400, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
    }

    private static Player owner(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        dev.latrox.rematter.research.ResearchService.ledger(h.getLevel().getServer())
                .visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        return owner;
    }
    private static MachineBlockEntity place(GameTestHelper h, BlockPos pos, MachineKind kind, Direction front, Player owner) {
        h.setBlock(pos, ModContent.MACHINES.get(kind).get().defaultBlockState().setValue(MachineBlock.FACING, front));
        var machine = h.getBlockEntity(pos, MachineBlockEntity.class);
        machine.placedBy(owner);
        return machine;
    }
    private static boolean cable(GameTestHelper h, BlockPos relative, Player owner) {
        var pos = h.absolutePos(relative);
        var stack = new ItemStack(ModContent.MATTER_CABLE.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter(), Direction.UP, pos, false))).consumesAction();
    }
    private static boolean arm(GameTestHelper h, BlockPos cable, Direction towards) {
        return h.getBlockState(cable).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(towards));
    }
    /** Opens a side that starts disabled, as the owner does in the Sides tab. */
    private static void enable(GameTestHelper h, MachineBlockEntity machine, Side side) {
        for (int step = 0; step < MachinePortMode.values().length && machine.portMode(side) != MachinePortMode.AUTOMATIC; step++)
            h.assertTrue(machine.cyclePort(side, true), "The owner could not configure the " + side + " of " + machine.kind());
        h.assertTrue(machine.portMode(side) == MachinePortMode.AUTOMATIC, "The " + side + " did not become Automatic");
    }

    /** Every kind, every rotation, every world side: items, fluids and FE, plus the one gate of chemicals and bridges. */
    private static void capabilities(GameTestHelper h) {
        var owner = owner(h); var relative = new BlockPos(4, 4, 4); var pos = h.absolutePos(relative);
        var level = h.getLevel();
        for (var kind : MachineKind.values()) {
            boolean items = false, fluids = false, energy = false;
            for (var front : Direction.Plane.HORIZONTAL) {
                var machine = place(h, relative, kind, front, owner);
                machine.toggleAutomation();
                h.assertTrue(machine.automation(), "Automation fixture is off for " + kind);
                for (var world : Direction.values()) {
                    var side = Side.local(front, world);
                    var itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, world);
                    var fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, world);
                    var energyHandler = level.getCapability(Capabilities.Energy.BLOCK, pos, world);
                    String where = kind + " facing " + front + " at its " + side;
                    if (side == Side.FRONT) h.assertTrue(!PortSides.available(kind, side), "The front counts as a connection: " + where);
                    if (kind.openTop() && side == Side.TOP) h.assertTrue(!PortSides.available(kind, side), "An open top counts as a connection: " + where);
                    if (!PortSides.available(kind, side)) {
                        h.assertTrue(itemHandler == null, "Item capability on a closed side: " + where);
                        h.assertTrue(fluidHandler == null, "Fluid capability on a closed side: " + where);
                        h.assertTrue(energyHandler == null, "Energy capability on a closed side: " + where);
                        h.assertTrue(machine.getSlotsForFace(world).length == 0, "Vanilla container slots on a closed side: " + where);
                        h.assertTrue(!machine.acceptsMatter(world), "Matter link on a closed side: " + where);
                        // Mekanism's chemical handler and the AE2/RS open faces ask exactly this gate.
                        for (var role : MachinePortMode.values())
                            if (role != MachinePortMode.DISABLED)
                                h.assertTrue(!machine.allowsPort(world, role), role + " allowed on a closed side: " + where);
                    } else if (machine.portMode(side) != MachinePortMode.DISABLED) {
                        items |= itemHandler != null; fluids |= fluidHandler != null; energy |= energyHandler != null;
                    }
                }
                h.assertTrue(level.getCapability(Capabilities.Item.BLOCK, pos, null) == null
                        && level.getCapability(Capabilities.Fluid.BLOCK, pos, null) == null
                        && level.getCapability(Capabilities.Energy.BLOCK, pos, null) == null,
                        "A side-less query opened a route around the sides of " + kind);
                for (var role : MachinePortMode.values())
                    h.assertTrue(!machine.allowsPort(null, role), "Side-less " + role + " on " + kind);
                h.setBlock(relative, Blocks.AIR);
            }
            // The fixture is not vacuous: the same lookups do find the capabilities on the sides that are connections.
            boolean hasSides = MachinePortMode.configurable(kind);
            if (hasSides && !kind.isStorage()) h.assertTrue(items, kind + " exposes no item capability on any connection");
            if (kind == MachineKind.DECOMPOSER || kind.synthesis()) h.assertTrue(fluids, kind + " exposes no fluid capability on any connection");
            if (hasSides && (kind.processes() || kind.generator()) && !dev.latrox.rematter.content.preparation.PreparationTier.manual(kind))
                h.assertTrue(energy, kind + " exposes no energy capability on any connection");
            if (!hasSides && kind != MachineKind.TERMINAL) h.assertTrue(!items && !fluids && !energy, kind + " exposes a capability without Machine Ports");
        }
        // A sealed top is closed until the owner enables it; then it is a connection like any other.
        var generator = place(h, relative, MachineKind.CRYSTAL_GENERATOR, Direction.NORTH, owner);
        if (PortSides.available(MachineKind.CRYSTAL_GENERATOR, Side.TOP)) {
            if (PortSides.SEALED_TOP == PortSides.SealedTop.OPT_IN)
                h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.UP) == null, "A sealed top is open before the owner enabled it");
            enable(h, generator, Side.TOP);
            h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.UP) != null, "An enabled sealed top exposes no energy");
        } else h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.UP) == null, "A closed sealed top exposes energy");
        h.succeed();
    }

    /** Real vanilla hoppers: nothing enters through the front or an open top; the same hopper feeds a side. */
    private static void hoppers(GameTestHelper h) {
        var owner = owner(h);
        var benchPos = new BlockPos(3, 3, 4); var generatorPos = new BlockPos(7, 3, 4);
        var bench = place(h, benchPos, MachineKind.DECOMPOSER, Direction.NORTH, owner); bench.toggleAutomation();
        var generator = place(h, generatorPos, MachineKind.CRYSTAL_GENERATOR, Direction.NORTH, owner); generator.toggleAutomation();
        // A full buffer keeps the generator from burning what a hopper might deliver.
        generator.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        var intoBenchFront = hopper(h, benchPos.north(), Direction.SOUTH, new ItemStack(Items.SAND, 8));
        var intoBenchTop = hopper(h, benchPos.above(), Direction.DOWN, new ItemStack(Items.SAND, 8));
        var intoGeneratorFront = hopper(h, generatorPos.north(), Direction.SOUTH, new ItemStack(ModContent.MATRIX_CRYSTAL.get(), 4));
        var intoGeneratorTop = hopper(h, generatorPos.above(), Direction.DOWN, new ItemStack(ModContent.MATRIX_CRYSTAL.get(), 4));
        boolean topStartsClosed = !PortSides.available(MachineKind.CRYSTAL_GENERATOR, Side.TOP)
                || PortSides.initial(MachineKind.CRYSTAL_GENERATOR, Side.TOP) == MachinePortMode.DISABLED;
        h.runAtTickTime(40, () -> {
            h.assertTrue(bench.getItem(0).isEmpty(), "A hopper fed the bench through its front or its open top");
            h.assertTrue(intoBenchFront.getItem(0).getCount() == 8 && intoBenchTop.getItem(0).getCount() == 8, "A bench hopper lost items");
            h.assertTrue(intoGeneratorFront.getItem(0).getCount() == 4, "A hopper fed the generator through its front");
            if (topStartsClosed) {
                h.assertTrue(generator.getItem(0).isEmpty() && intoGeneratorTop.getItem(0).getCount() == 4, "A hopper fed a closed sealed top");
                if (PortSides.available(MachineKind.CRYSTAL_GENERATOR, Side.TOP)) enable(h, generator, Side.TOP);
            }
            // The same kind of hopper against a side that is a connection.
            hopper(h, benchPos.east(), Direction.WEST, new ItemStack(Items.SAND, 8));
        });
        h.runAtTickTime(90, () -> {
            h.assertTrue(!bench.getItem(0).isEmpty(), "The control hopper did not feed the bench through its left side");
            h.assertTrue(intoBenchFront.getItem(0).getCount() == 8 && intoBenchTop.getItem(0).getCount() == 8, "A bench hopper lost items later");
            h.assertTrue(intoGeneratorFront.getItem(0).getCount() == 4, "A hopper fed the generator through its front later");
            if (PortSides.available(MachineKind.CRYSTAL_GENERATOR, Side.TOP))
                h.assertTrue(intoGeneratorTop.getItem(0).getCount() < 4 && !generator.getItem(0).isEmpty(), "An enabled sealed top took nothing from its hopper");
            else h.assertTrue(intoGeneratorTop.getItem(0).getCount() == 4, "A closed sealed top took items");
            h.succeed();
        });
    }
    private static HopperBlockEntity hopper(GameTestHelper h, BlockPos pos, Direction towards, ItemStack content) {
        h.setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, towards));
        var hopper = h.getBlockEntity(pos, HopperBlockEntity.class);
        hopper.setItem(0, content);
        return hopper;
    }

    /** Owned cables on all six neighbours: an arm grows exactly towards the sides that are connections right now. */
    private static void cables(GameTestHelper h) {
        var owner = owner(h); var center = new BlockPos(4, 4, 4);
        for (var kind : new MachineKind[]{MachineKind.DECOMPOSER, MachineKind.REACTOR, MachineKind.DRIVE, MachineKind.NETWORK_INTERFACE,
                MachineKind.PRIMAL_CONVERTER, MachineKind.CRYSTAL_GENERATOR, MachineKind.TERMINAL, MachineKind.INDUSTRIAL_REACTOR})
            for (var front : new Direction[]{Direction.NORTH, Direction.EAST}) {
                var machine = place(h, center, kind, front, owner);
                for (var world : Direction.values()) h.assertTrue(cable(h, center.relative(world), owner), "Cable placement failed");
                for (var world : Direction.values()) {
                    var side = Side.local(front, world);
                    boolean expected = machine.portMode(side) != MachinePortMode.DISABLED;
                    if (side == Side.FRONT || kind.openTop() && side == Side.TOP)
                        h.assertTrue(!expected, kind + " would accept a cable at its " + side);
                    h.assertTrue(arm(h, center.relative(world), world.getOpposite()) == expected,
                            kind + " facing " + front + ": cable at its " + side + " is " + (expected ? "not connected" : "connected"));
                }
                if (PortSides.available(kind, Side.TOP) && machine.portMode(Side.TOP) == MachinePortMode.DISABLED) {
                    enable(h, machine, Side.TOP);
                    h.assertTrue(arm(h, center.above(), Direction.DOWN), kind + ": the cable ignored an enabled top");
                }
                for (var world : Direction.values()) h.setBlock(center.relative(world), Blocks.AIR);
                h.setBlock(center, Blocks.AIR);
            }
        h.succeed();
    }

    /** A generator pushes FE into its neighbours, but never out of its front. */
    private static void generatorPush(GameTestHelper h) {
        var owner = owner(h); var pos = new BlockPos(4, 3, 4);
        var generator = place(h, pos, MachineKind.CRYSTAL_GENERATOR, Direction.NORTH, owner);
        // Each cube turns an input side to the generator: its back.
        var atFront = cube(h, pos.north(), Direction.NORTH, owner);
        var atBack = cube(h, pos.south(), Direction.SOUTH, owner);
        h.assertTrue(atFront.sideMode(Direction.SOUTH) == EnergyCubeSideMode.INPUT && atBack.sideMode(Direction.NORTH) == EnergyCubeSideMode.INPUT,
                "Cube fixtures do not face the generator with an input");
        generator.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        for (int tick = 0; tick < 5; tick++) generator.serverTick();
        h.assertTrue(atBack.energy() > 0, "The generator pushed nothing through its back: the fixture proves nothing");
        h.assertTrue(atFront.energy() == 0, "The generator pushed " + atFront.energy() + " FE out of its front");
        h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(pos), Direction.NORTH) == null,
                "The generator offers FE at its front");
        h.succeed();
    }
    private static EnergyCubeBlockEntity cube(GameTestHelper h, BlockPos pos, Direction front, Player owner) {
        h.setBlock(pos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.values()[0]).get().defaultBlockState().setValue(EnergyCubeBlock.FACING, front));
        var cube = h.getBlockEntity(pos, EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        return cube;
    }
    private static void generatorCached(GameTestHelper h) {
        var owner = owner(h); var pos = new BlockPos(4, 3, 4);
        var generator = place(h, pos, MachineKind.CRYSTAL_GENERATOR, Direction.NORTH, owner);
        generator.energy().set(10_000);
        var cached = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(pos), Direction.SOUTH);
        h.assertTrue(cached != null, "Generator fixture exposes no back output");
        try (var tx = Transaction.openRoot()) { h.assertTrue(cached.extract(100, tx) == 100, "Live generator output is inert"); }
        while (generator.portMode(Side.BACK) != MachinePortMode.DISABLED)
            h.assertTrue(generator.cyclePort(Side.BACK), "Could not disable generator output");
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(cached.getAmountAsLong() == 0 && cached.extract(100, tx) == 0,
                    "Cached generator capability drained a Disabled side");
        }
        enable(h, generator, Side.BACK);
        try (var tx = Transaction.openRoot()) { h.assertTrue(cached.extract(100, tx) == 100, "Re-enabled generator output stayed inert"); }
        h.setBlock(pos, Blocks.AIR);
        try (var tx = Transaction.openRoot()) { h.assertTrue(cached.extract(100, tx) == 0, "Removed generator retained an output"); }
        h.succeed();
    }

    /** Devices that touch link directly, but a front against a neighbour is a wall. */
    private static void neighbours(GameTestHelper h) {
        var owner = owner(h);
        // Rig one: the bench stands in front of the Drive. Rig two: beside it. Rig three: on top of it.
        var frontDrive = drive(h, new BlockPos(2, 3, 4), owner);
        var inFront = place(h, new BlockPos(2, 3, 3), MachineKind.DECOMPOSER, Direction.NORTH, owner);
        var sideDrive = drive(h, new BlockPos(5, 3, 4), owner);
        var beside = place(h, new BlockPos(6, 3, 4), MachineKind.DECOMPOSER, Direction.NORTH, owner);
        var lowerDrive = drive(h, new BlockPos(8, 3, 4), owner);
        var above = place(h, new BlockPos(8, 4, 4), MachineKind.DECOMPOSER, Direction.NORTH, owner);
        h.assertTrue(frontDrive.networkLive() && sideDrive.networkLive() && lowerDrive.networkLive(), "Drive fixtures are not live");
        h.assertTrue(!inFront.matterNetwork(true).ready(), "A bench linked to a Drive through the Drive's front");
        h.assertTrue(beside.matterNetwork(true).ready(), "A bench beside a Drive did not link: " + beside.matterNetwork(true).status());
        boolean top = PortSides.available(MachineKind.DRIVE, Side.TOP);
        if (!top || PortSides.initial(MachineKind.DRIVE, Side.TOP) == MachinePortMode.DISABLED)
            h.assertTrue(!above.matterNetwork(true).ready(), "A bench linked through a closed Drive top");
        if (top) {
            enable(h, lowerDrive, Side.TOP);
            h.runAtTickTime(3, () -> {
                h.assertTrue(above.matterNetwork(true).ready(), "A bench on an enabled Drive top did not link: " + above.matterNetwork(true).status());
                h.succeed();
            });
        } else h.succeed();
    }
    private static MachineBlockEntity drive(GameTestHelper h, BlockPos pos, Player owner) {
        var drive = place(h, pos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE, 0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        return drive;
    }

    /** The real menu button path a client packet takes: closed sides are refused, forward and backward. */
    private static void menuPackets(GameTestHelper h) {
        var owner = owner(h); var relative = new BlockPos(4, 3, 4);
        for (var kind : new MachineKind[]{MachineKind.DECOMPOSER, MachineKind.DRIVE, MachineKind.PRIMAL_CONVERTER, MachineKind.TERMINAL,
                MachineKind.INDUSTRIAL_REACTOR}) {
            var machine = place(h, relative, kind, Direction.NORTH, owner);
            var at = machine.getBlockPos();
            owner.setPos(at.getX() + .5, at.getY() + 1, at.getZ() + .5);
            var menu = machine.createMenu(7, owner.getInventory(), owner);
            h.assertTrue(menu != null, "The owner could not open " + kind);
            owner.containerMenu = menu;
            for (var side : Side.values()) {
                var before = machine.portMode(side);
                boolean forward = menu.clickMenuButton(owner, MachineMenu.CYCLE_PORT + side.ordinal());
                var stepped = machine.portMode(side);
                boolean backward = menu.clickMenuButton(owner, MachineMenu.CYCLE_PORT_BACK + side.ordinal());
                if (MachinePortMode.configurable(kind, side)) {
                    h.assertTrue(forward && stepped != before, kind + ": the server refused its configurable " + side);
                    h.assertTrue(backward && machine.portMode(side) == before, kind + ": a backward step did not undo a forward step at " + side);
                } else {
                    h.assertTrue(!forward && !backward, kind + ": the server accepted a packet for its closed " + side);
                    h.assertTrue(stepped == before && machine.portMode(side) == before, kind + ": a refused packet changed the " + side);
                }
            }
            h.assertTrue(!menu.clickMenuButton(owner, MachineMenu.CYCLE_PORT + 12) && !menu.clickMenuButton(owner, MachineMenu.CYCLE_PORT - 1),
                    kind + ": an id outside the twelve side buttons was accepted as a side");
            owner.containerMenu = owner.inventoryMenu;
            h.setBlock(relative, Blocks.AIR);
        }
        h.succeed();
    }

    /** The exception: every side of a cube, the front included, is a connection until its owner disables it. */
    private static void energyCube(GameTestHelper h) {
        var owner = owner(h); var relative = new BlockPos(4, 3, 4); var pos = h.absolutePos(relative);
        for (var front : Direction.Plane.HORIZONTAL) {
            var cube = cube(h, relative, front, owner);
            owner.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
            for (var world : Direction.values()) {
                h.assertTrue(PortSides.energyCube(Side.local(front, world)), "The cube table closes a side");
                h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, pos, world) != null,
                        "The cube facing " + front + " exposes no FE at " + world);
            }
            var seeded = cube.saveCustomOnly(h.getLevel().registryAccess()); seeded.putInt("energy", 1_000);
            cube.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess(), seeded));
            var output = h.getLevel().getCapability(Capabilities.Energy.BLOCK, pos, front);
            try (var tx = Transaction.openRoot()) {
                h.assertTrue(output != null && output.extract(100, tx) == 100, "The front of a cube is not its output");
            }
            // Direction is visual data too; it never changes the three persistent transfer modes.
            int initial = cube.getModelData().get(PortSides.PLATES);
            for (var side : Side.values())
                h.assertTrue(PortSides.appearance(initial, side) == (side == Side.FRONT ? PortAppearance.ENERGY_OUTPUT : PortAppearance.ENERGY_INPUT),
                        "A new cube does not show the actual direction of " + side);
            for (int step = 0; step < 3 && cube.sideMode(Side.LEFT) != EnergyCubeSideMode.DISABLED; step++)
                h.assertTrue(cube.cycleSide(owner, Side.LEFT), "The owner could not configure the cube");
            int shown = cube.getModelData().get(PortSides.PLATES);
            for (var side : Side.values())
                h.assertTrue(PortSides.appearance(shown, side) == (side == Side.LEFT ? PortAppearance.DISABLED
                                : side == Side.FRONT ? PortAppearance.ENERGY_OUTPUT : PortAppearance.ENERGY_INPUT),
                        "The cube shows the wrong plate on its " + side);
            h.assertTrue(cube.getUpdateTag(h.getLevel().registryAccess()).getIntOr("side_modes", -1) == cube.packedSideModes(),
                    "The cube does not publish its side roles");
            var client = new EnergyCubeBlockEntity(pos, cube.getBlockState());
            client.handleUpdateTag(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                    h.getLevel().registryAccess(), cube.getUpdateTag(h.getLevel().registryAccess())));
            h.assertTrue(client.getModelData().get(PortSides.PLATES) == shown, "The client draws other seats than the server published");
            h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, pos, Side.LEFT.world(front)) == null,
                    "A disabled cube side still exposes FE");
            int stored = cube.packedSideModes();
            for (var side : Side.values()) for (int step = 0; step < 3; step++) {
                h.assertTrue(cube.cycleSide(owner, side), "Cube direction cannot be changed on " + side);
                var expected = switch (cube.sideMode(side)) {
                    case INPUT -> PortAppearance.ENERGY_INPUT;
                    case OUTPUT -> PortAppearance.ENERGY_OUTPUT;
                    case DISABLED -> PortAppearance.DISABLED;
                };
                client.handleUpdateTag(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                        h.getLevel().registryAccess(), cube.getUpdateTag(h.getLevel().registryAccess())));
                h.assertTrue(PortSides.appearance(client.getModelData().get(PortSides.PLATES), side) == expected,
                        "The client did not receive the actual " + side + " direction");
            }
            h.assertTrue(cube.packedSideModes() == stored && cube.energy() == 1_000,
                    "A full direction cycle changed saved roles or stored FE");
            h.setBlock(relative, Blocks.AIR);
        }
        h.succeed();
    }
}
