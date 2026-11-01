package dev.latrox.rematter.content;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** Bounded rectangular shells. Chunk identity and mutation revisions protect every cached observation. */
public final class IndustrialStructure {
    public static final int MAX_SIZE = 9;
    public enum Role { CONTROLLER, INTERIOR, FRAME, WALL }
    public enum Material { AIR, CASING, GLASS, PORT, CONTROLLER, OTHER, UNLOADED }
    public record Cell(int x, int y, int depth, Role role, Direction outward) {}
    public record Shape(int width, int height, int depth, int controllerColumn) {
        public Shape {
            if (width < 3 || width > MAX_SIZE || height < 3 || height > MAX_SIZE || depth < 3 || depth > MAX_SIZE
                    || controllerColumn < 1 || controllerColumn >= width - 1) throw new IllegalArgumentException("Invalid industrial dimensions");
        }
        public int innerVolume() { return (width - 2) * (height - 2) * (depth - 2); }
        public int baseWorkRate(MachineKind kind) {
            if (!isIndustrial(kind) || height < IndustrialStructure.height(kind)) throw new IllegalArgumentException("Wrong industrial tier");
            return integerSquareRoot((kind == MachineKind.INDUSTRIAL_PREPARATOR ? 16 : 2) * innerVolume());
        }
    }
    @FunctionalInterface public interface Materials { Material at(int x, int y, int depth); }
    public record Port(BlockPos position, Direction outward) {}
    public record Formation(boolean formed, @Nullable Shape shape, List<Port> ports) {
        public static final Formation EMPTY = new Formation(false, null, List.of());
        public Formation { ports = List.copyOf(ports); if (formed != (shape != null)) throw new IllegalArgumentException("Invalid formation"); }
    }
    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(BlockPos p) { return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ; }
        Bounds expand(int amount) { return new Bounds(minX-amount,minY-amount,minZ-amount,maxX+amount,maxY+amount,maxZ+amount); }
    }
    private record ChunkStamp(int x, int z, @Nullable WeakReference<LevelChunk> chunk, ChunkRevisions.Revision revision) {
        boolean matches(ServerLevel level) {
            var current = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) return current == null;
            return current != null && chunk.get() == current && revision.stable() && revision.equals(ChunkRevisions.current(current));
        }
    }
    private record Stamp(List<ChunkStamp> chunks) {
        boolean matches(ServerLevel level) { return chunks.stream().allMatch(chunk -> chunk.matches(level)); }
    }
    private record Cached(Stamp stamp, Formation raw, @Nullable Formation complete) {}
    private record PortCache(Stamp stamp, @Nullable WeakReference<MachineBlockEntity> controller) {}
    private static final Map<MachineBlockEntity, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MachinePortBlockEntity, PortCache> PORT_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private IndustrialStructure() {}
    public static boolean isIndustrial(MachineKind kind) { return kind == MachineKind.INDUSTRIAL_PREPARATOR || kind == MachineKind.INDUSTRIAL_REACTOR; }
    /** Minimum height, retained for minimum-size recipes and existing structures. */
    public static int height(MachineKind kind) {
        return switch (kind) { case INDUSTRIAL_PREPARATOR -> 3; case INDUSTRIAL_REACTOR -> 4;
            default -> throw new IllegalArgumentException("Not an industrial controller"); };
    }
    public static List<Cell> layout(MachineKind kind) { return layout(new Shape(3, height(kind), 3, 1)); }
    public static List<Cell> layout(Shape shape) {
        var cells = new ArrayList<Cell>(shape.width * shape.height * shape.depth);
        int left = -shape.controllerColumn, right = left + shape.width - 1;
        for (int y = -1; y <= shape.height - 2; y++) for (int depth = 0; depth < shape.depth; depth++) for (int x = left; x <= right; x++) {
            boolean bx = x == left || x == right, by = y == -1 || y == shape.height - 2, bz = depth == 0 || depth == shape.depth - 1;
            int boundaries = (bx ? 1 : 0) + (by ? 1 : 0) + (bz ? 1 : 0);
            Role role = x == 0 && y == 0 && depth == 0 ? Role.CONTROLLER
                    : boundaries == 0 ? Role.INTERIOR : boundaries >= 2 ? Role.FRAME : Role.WALL;
            Direction outward = boundaries != 1 ? null : by ? (y == -1 ? Direction.DOWN : Direction.UP)
                    : bx ? (x == left ? Direction.WEST : Direction.EAST) : depth == 0 ? Direction.NORTH : Direction.SOUTH;
            cells.add(new Cell(x, y, depth, role, outward));
        }
        return List.copyOf(cells);
    }
    /** Four rays from the air behind the controller determine one shape, never a set of size guesses. */
    public static Optional<Shape> detect(MachineKind kind, Materials blocks) {
        if (!isIndustrial(kind) || blocks.at(0, 0, 1) != Material.AIR) return Optional.empty();
        int left = ray(blocks,-1,0,0), right = ray(blocks,1,0,0), rear = ray(blocks,0,0,1), top = ray(blocks,0,1,0);
        if (left < 1 || right < 1 || rear < 1 || top < 1) return Optional.empty();
        try {
            var shape = new Shape(left + right + 1, top + 2, rear + 2, left);
            return shape.height >= height(kind) ? Optional.of(shape) : Optional.empty();
        } catch (IllegalArgumentException oversized) { return Optional.empty(); }
    }
    private static int ray(Materials blocks, int dx, int dy, int dz) {
        for (int distance = 1; distance < MAX_SIZE; distance++) {
            Material material = blocks.at(dx * distance, dy * distance, 1 + dz * distance);
            if (material == Material.UNLOADED) return -1;
            if (material != Material.AIR) return distance;
        }
        return -1;
    }
    public static boolean validates(MachineKind kind, Shape shape, Materials blocks) {
        if (!isIndustrial(kind) || shape.height < height(kind)) return false;
        int ports = 0;
        for (var cell : layout(shape)) {
            var material = blocks.at(cell.x,cell.y,cell.depth);
            switch (cell.role) {
                case CONTROLLER -> { if (material != Material.CONTROLLER) return false; }
                case INTERIOR -> { if (material != Material.AIR) return false; }
                case FRAME -> { if (material != Material.CASING) return false; }
                case WALL -> { if (material == Material.PORT) ports++; else if (material != Material.CASING && material != Material.GLASS) return false; }
            }
        }
        return ports >= 2;
    }
    public static int integerSquareRoot(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative volume");
        int low = 0, high = Math.min(value, 46340) + 1;
        while (low + 1 < high) { int middle = low + (high-low)/2; if ((long)middle*middle <= value) low=middle; else high=middle; }
        return low;
    }
    public static Direction worldDirection(Direction facing, Direction relative) {
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Horizontal controller facing required");
        return switch (relative) { case NORTH -> facing; case SOUTH -> facing.getOpposite(); case EAST -> facing.getClockWise(); case WEST -> facing.getCounterClockWise(); default -> relative; };
    }
    public static BlockPos worldPosition(BlockPos controller, Direction facing, Cell cell) {
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Horizontal controller facing required");
        return controller.relative(facing.getClockWise(), cell.x).relative(facing.getOpposite(), cell.depth).above(cell.y);
    }
    private static Bounds bounds(MachineBlockEntity machine, Shape shape) {
        var facing = machine.getBlockState().getValue(MachineBlock.FACING);
        var a = worldPosition(machine.getBlockPos(),facing,new Cell(-shape.controllerColumn,-1,0,Role.FRAME,null));
        var b = worldPosition(machine.getBlockPos(),facing,new Cell(shape.width-shape.controllerColumn-1,shape.height-2,shape.depth-1,Role.FRAME,null));
        return new Bounds(Math.min(a.getX(),b.getX()),a.getY(),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX()),b.getY(),Math.max(a.getZ(),b.getZ()));
    }
    private static Bounds around(BlockPos p, int radius) { return new Bounds(p.getX()-radius,p.getY()-radius,p.getZ()-radius,p.getX()+radius,p.getY()+radius,p.getZ()+radius); }
    private static Stamp stamp(ServerLevel level, Bounds area) {
        var chunks = new ArrayList<ChunkStamp>();
        for (int x=area.minX>>4;x<=area.maxX>>4;x++) for(int z=area.minZ>>4;z<=area.maxZ>>4;z++) {
            var chunk=level.getChunkSource().getChunkNow(x,z);
            chunks.add(new ChunkStamp(x,z,chunk==null?null:new WeakReference<>(chunk),chunk==null?new ChunkRevisions.Revision(0,0):ChunkRevisions.current(chunk)));
        }
        return new Stamp(List.copyOf(chunks));
    }
    private static boolean live(MachineBlockEntity machine) {
        return isIndustrial(machine.kind()) && machine.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()
                && !machine.isRemoved() && level.hasChunkAt(machine.getBlockPos()) && level.getBlockEntity(machine.getBlockPos()) == machine;
    }
    private static Formation raw(MachineBlockEntity machine) {
        if (!live(machine)) return Formation.EMPTY;
        var level=(ServerLevel)machine.getLevel();
        var cached=CACHE.get(machine);
        if(cached!=null&&cached.stamp.matches(level))return cached.raw;
        // Every possible shape and every shell that could share one of its ports fits this watch area.
        var observed=stamp(level,around(machine.getBlockPos(),3*(MAX_SIZE-1)));
        if(!observed.matches(level))return Formation.EMPTY;
        var facing=machine.getBlockState().getValue(MachineBlock.FACING);
        Materials blocks=(x,y,depth)->material(level,machine,worldPosition(machine.getBlockPos(),facing,new Cell(x,y,depth,Role.INTERIOR,null)));
        var shape=detect(machine.kind(),blocks).orElse(null);
        Formation result=Formation.EMPTY;
        if(shape!=null&&validates(machine.kind(),shape,blocks)){
            var ports=new ArrayList<Port>();
            for(var cell:layout(shape))if(cell.role==Role.WALL){
                var pos=worldPosition(machine.getBlockPos(),facing,cell);
                if(level.getBlockState(pos).is(ModContent.MACHINE_PORT.get()))ports.add(new Port(pos,worldDirection(facing,cell.outward)));
            }
            result=new Formation(true,shape,ports);
        }
        // A reentrant block/BE callback cannot publish a result observed during mutation.
        if(!observed.matches(level))return Formation.EMPTY;
        CACHE.put(machine,new Cached(observed,result,null));
        return result;
    }
    private static Material material(ServerLevel level,MachineBlockEntity machine,BlockPos pos){
        if(!level.hasChunkAt(pos))return Material.UNLOADED;
        var state=level.getBlockState(pos);
        if(pos.equals(machine.getBlockPos()))return level.getBlockEntity(pos)==machine?Material.CONTROLLER:Material.OTHER;
        if(state.isAir())return Material.AIR;
        if(state.is(ModContent.REACTOR_CASING.get()))return Material.CASING;
        if(state.is(ModContent.REACTOR_GLASS.get()))return Material.GLASS;
        if(state.is(ModContent.MACHINE_PORT.get())&&level.getBlockEntity(pos) instanceof MachinePortBlockEntity)return Material.PORT;
        return Material.OTHER;
    }
    public static Formation inspect(MachineBlockEntity machine) {
        Formation basic=raw(machine);
        if(!basic.formed)return basic;
        var level=(ServerLevel)machine.getLevel();var cached=CACHE.get(machine);
        if(cached!=null&&cached.complete!=null&&cached.stamp.matches(level))return cached.complete;
        var portPositions=new HashSet<BlockPos>();basic.ports.forEach(port->portPositions.add(port.position));
        Formation result=basic;
        // One bounded BE-map query for the whole shell, not one search for every possible port.
        for(var other:candidates(level,bounds(machine,basic.shape).expand(MAX_SIZE-1))){
            if(other==machine)continue;
            var otherShell=raw(other);
            if(otherShell.formed&&otherShell.ports.stream().anyMatch(port->portPositions.contains(port.position))){result=Formation.EMPTY;break;}
        }
        if(cached==null||!cached.stamp.matches(level))return Formation.EMPTY;
        CACHE.put(machine,new Cached(cached.stamp,basic,result));return result;
    }
    private static List<MachineBlockEntity> candidates(ServerLevel level,Bounds area){
        var candidates=new ArrayList<MachineBlockEntity>();
        for(int x=area.minX>>4;x<=area.maxX>>4;x++)for(int z=area.minZ>>4;z<=area.maxZ>>4;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            // Includes pending loaded-chunk BE positions; resolving one never requests another chunk.
            for(var pos:List.copyOf(chunk.getBlockEntitiesPos()))if(area.contains(pos)){
                var state=chunk.getBlockState(pos);
                if(state.getBlock() instanceof MachineBlock block&&isIndustrial(block.kind())&&chunk.getBlockEntity(pos) instanceof MachineBlockEntity machine)candidates.add(machine);
            }
        }
        candidates.sort(Comparator.comparingLong(machine->machine.getBlockPos().asLong()));return candidates;
    }
    static @Nullable MachineBlockEntity associatedController(MachinePortBlockEntity port){
        if(!(port.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||port.isRemoved()
                ||!level.hasChunkAt(port.getBlockPos())||level.getBlockEntity(port.getBlockPos())!=port)return null;
        var cached=PORT_CACHE.get(port);
        if(cached!=null&&cached.stamp.matches(level)){
            var machine=cached.controller==null?null:cached.controller.get();
            return machine!=null&&live(machine)?machine:null;
        }
        var observed=stamp(level,around(port.getBlockPos(),3*(MAX_SIZE-1)));
        if(!observed.matches(level))return null;
        MachineBlockEntity found=null;
        for(var machine:candidates(level,around(port.getBlockPos(),MAX_SIZE-1))){
            var shell=inspect(machine);
            if(shell.formed&&shell.ports.stream().anyMatch(p->p.position.equals(port.getBlockPos()))){
                if(found!=null){found=null;break;}found=machine;
            }
        }
        if(!observed.matches(level))return null;
        PORT_CACHE.put(port,new PortCache(observed,found==null?null:new WeakReference<>(found)));return found;
    }
    static List<MachineBlockEntity> controllersUsing(ServerLevel level,BlockPos partPos,boolean portOnly){
        if(!level.getServer().isSameThread()||!level.hasChunkAt(partPos))return List.of();
        var result=new ArrayList<MachineBlockEntity>();
        for(var machine:candidates(level,around(partPos,MAX_SIZE-1))){
            var shell=raw(machine);if(!shell.formed)continue;
            if(portOnly?shell.ports.stream().anyMatch(port->port.position.equals(partPos)):bounds(machine,shell.shape).contains(partPos))result.add(machine);
        }
        return List.copyOf(result);
    }
}
