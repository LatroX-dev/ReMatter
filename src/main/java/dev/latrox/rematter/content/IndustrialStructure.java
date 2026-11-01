package dev.latrox.rematter.content;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Fixed three-dimensional shells; inspection never loads chunks or stores resources. */
public final class IndustrialStructure {
    public enum Role { CONTROLLER, INTERIOR, FRAME, WALL }
    public record Cell(int x, int y, int depth, Role role, Direction outward) {}
    public record Port(BlockPos position, Direction outward) {}
    public record Formation(boolean formed, List<Port> ports) {
        public static final Formation EMPTY = new Formation(false, List.of());
        public Formation { ports = List.copyOf(ports); }
    }
    private static final List<Cell> PREPARATION = createLayout(3), REACTOR = createLayout(4);
    private IndustrialStructure() {}
    public static boolean isIndustrial(MachineKind kind) {
        return kind == MachineKind.INDUSTRIAL_PREPARATOR || kind == MachineKind.INDUSTRIAL_REACTOR;
    }
    public static int height(MachineKind kind) {
        return switch (kind) { case INDUSTRIAL_PREPARATOR -> 3; case INDUSTRIAL_REACTOR -> 4;
            default -> throw new IllegalArgumentException("Not an industrial controller"); };
    }
    public static List<Cell> layout(MachineKind kind) { return height(kind) == 3 ? PREPARATION : REACTOR; }
    private static List<Cell> createLayout(int height) {
        var cells = new ArrayList<Cell>();
        for (int y = -1; y <= height - 2; y++) for (int depth = 0; depth <= 2; depth++) for (int x = -1; x <= 1; x++) {
            boolean bx = x != 0, by = y == -1 || y == height - 2, bz = depth != 1;
            int boundaries = (bx ? 1 : 0) + (by ? 1 : 0) + (bz ? 1 : 0);
            Role role = x == 0 && y == 0 && depth == 0 ? Role.CONTROLLER
                    : boundaries == 0 ? Role.INTERIOR : boundaries >= 2 ? Role.FRAME : Role.WALL;
            Direction outward = boundaries != 1 ? null : by ? (y == -1 ? Direction.DOWN : Direction.UP)
                    : bx ? (x < 0 ? Direction.WEST : Direction.EAST) : depth == 0 ? Direction.NORTH : Direction.SOUTH;
            cells.add(new Cell(x, y, depth, role, outward));
        }
        return List.copyOf(cells);
    }
    public static Direction worldDirection(Direction facing, Direction relative) {
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Horizontal controller facing required");
        return switch (relative) { case NORTH -> facing; case SOUTH -> facing.getOpposite();
            case EAST -> facing.getClockWise(); case WEST -> facing.getCounterClockWise(); default -> relative; };
    }
    public static BlockPos worldPosition(BlockPos controller, Direction facing, Cell cell) {
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Horizontal controller facing required");
        return controller.relative(facing.getClockWise(), cell.x()).relative(facing.getOpposite(), cell.depth()).above(cell.y());
    }
    public static Formation inspect(MachineBlockEntity machine) {
        Formation basic = inspectShell(machine);
        if (!basic.formed()) return basic;
        var level = (ServerLevel) machine.getLevel();
        for (var port : basic.ports()) {
            // The current shell was checked once above. Only competing controllers need another shell check.
            if (!controllersUsing(level, port.position(), true, machine.getBlockPos()).isEmpty()) return Formation.EMPTY;
        }
        return basic;
    }
    private static Formation inspectShell(MachineBlockEntity machine) {
        if (!isIndustrial(machine.kind()) || !(machine.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || machine.isRemoved() || !level.hasChunkAt(machine.getBlockPos())
                || level.getBlockEntity(machine.getBlockPos()) != machine) return Formation.EMPTY;
        Direction facing = machine.getBlockState().getValue(MachineBlock.FACING);
        var ports = new ArrayList<Port>();
        for (var cell : layout(machine.kind())) {
            BlockPos pos = worldPosition(machine.getBlockPos(), facing, cell);
            if (!level.hasChunkAt(pos)) return Formation.EMPTY;
            var state = level.getBlockState(pos);
            switch (cell.role()) {
                case CONTROLLER -> { if (level.getBlockEntity(pos) != machine) return Formation.EMPTY; }
                case INTERIOR -> { if (!state.isAir()) return Formation.EMPTY; }
                case FRAME -> { if (!state.is(ModContent.REACTOR_CASING.get())) return Formation.EMPTY; }
                case WALL -> {
                    if (state.is(ModContent.MACHINE_PORT.get())) {
                        if (!(level.getBlockEntity(pos) instanceof MachinePortBlockEntity)) return Formation.EMPTY;
                        ports.add(new Port(pos, worldDirection(facing, cell.outward())));
                    } else if (!state.is(ModContent.REACTOR_CASING.get()) && !state.is(ModContent.REACTOR_GLASS.get())) return Formation.EMPTY;
                }
            }
        }
        return ports.size() >= 2 ? new Formation(true, ports) : Formation.EMPTY;
    }
    /** A shared port is invalid even if both candidate shells have otherwise valid geometry. */
    static List<MachineBlockEntity> matchingControllers(ServerLevel level, BlockPos portPos) {
        return controllersUsing(level, portPos, true);
    }
    static List<MachineBlockEntity> controllersUsing(ServerLevel level, BlockPos partPos, boolean portOnly) {
        return controllersUsing(level, partPos, portOnly, null);
    }
    private static List<MachineBlockEntity> controllersUsing(ServerLevel level, BlockPos partPos, boolean portOnly, BlockPos excluded) {
        if (!level.getServer().isSameThread() || !level.hasChunkAt(partPos)) return List.of();
        var found = new ArrayList<MachineBlockEntity>();
        var candidates = new HashSet<BlockPos>();
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            for (var facing : Direction.Plane.HORIZONTAL) for (var cell : layout(kind)) {
                if (cell.role() != Role.WALL && (portOnly || cell.role() != Role.FRAME)) continue;
                BlockPos candidate = partPos.subtract(worldPosition(BlockPos.ZERO, facing, cell));
                if (candidate.equals(excluded) || !level.hasChunkAt(candidate)) continue;
                var state = level.getBlockState(candidate);
                // Most candidate positions are air or casing. Avoid block-entity lookups for those positions.
                if (!(state.getBlock() instanceof MachineBlock block) || block.kind() != kind || state.getValue(MachineBlock.FACING) != facing
                        || !(level.getBlockEntity(candidate) instanceof MachineBlockEntity machine)
                        || !candidates.add(candidate)) continue;
                Formation basic = inspectShell(machine);
                if (basic.formed() && (!portOnly || basic.ports().stream().anyMatch(port -> port.position().equals(partPos)))) found.add(machine);
                if (portOnly && found.size() > 1) return List.copyOf(found);
            }
        }
        return List.copyOf(found);
    }
}
