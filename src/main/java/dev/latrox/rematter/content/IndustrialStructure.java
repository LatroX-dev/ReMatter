package dev.latrox.rematter.content;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** Bounded rectangular shells. Chunk identity and mutation revisions protect every cached observation. */
public final class IndustrialStructure {
    public static final int MAX_SIZE = 9;
    public enum Role { CONTROLLER, INTERIOR, FRAME, WALL }
    public enum Material { AIR, CASING, GLASS, PORT, CONTROLLER, OTHER, UNLOADED,
        MIXING_BASIN, SEPARATION_UNIT, GRINDING_UNIT, FIELD_COIL, REACTION_CHAMBER;
        boolean module() { return ordinal() >= MIXING_BASIN.ordinal(); }
        boolean interior() { return this == AIR || module(); }
    }
    public static final Set<String> DIAGNOSTICS = Set.of("ready", "structure", "structure_too_short", "structure_unloaded",
            "structure_frame", "structure_wall", "structure_ports", "structure_modules_missing", "structure_module_incomplete",
            "structure_module_position", "structure_module_type", "structure_interior", "structure_ambiguous");
    public record Cell(int x, int y, int depth, Role role, Direction outward) {}
    public record Shape(int width, int height, int depth, int controllerColumn) {
        public Shape {
            if (width < 3 || width > MAX_SIZE || height < 3 || height > MAX_SIZE || depth < 3 || depth > MAX_SIZE
                    || controllerColumn < 1 || controllerColumn >= width - 1) throw new IllegalArgumentException("Invalid industrial dimensions");
        }
        public int innerVolume() { return (width - 2) * (height - 2) * (depth - 2); }
    }
    @FunctionalInterface public interface Materials { Material at(int x, int y, int depth); }
    public record Port(BlockPos position, Direction outward) {}
    /** Retained only for break protection during repairs; never proof of a valid working structure. */
    public record ProtectionClaim(Shape shape, Direction facing) {
        public ProtectionClaim {
            Objects.requireNonNull(shape);Objects.requireNonNull(facing);
            if(!facing.getAxis().isHorizontal())throw new IllegalArgumentException("Invalid claim direction");
        }
        public CompoundTag save() {
            var tag=new CompoundTag();tag.putInt("schema",1);tag.putInt("width",shape.width);tag.putInt("height",shape.height);
            tag.putInt("depth",shape.depth);tag.putInt("column",shape.controllerColumn);tag.putString("facing",facing.getSerializedName());return tag;
        }
        public static Optional<ProtectionClaim> restore(CompoundTag tag) {
            try {
                if(tag.getIntOr("schema",0)!=1)return Optional.empty();
                var facing=Direction.byName(tag.getStringOr("facing",""));
                if(facing==null)return Optional.empty();
                return Optional.of(new ProtectionClaim(new Shape(tag.getIntOr("width",0),tag.getIntOr("height",0),
                        tag.getIntOr("depth",0),tag.getIntOr("column",0)),facing));
            } catch(IllegalArgumentException invalid) {return Optional.empty();}
        }
        public boolean contains(BlockPos controller,BlockPos part) { return bounds(controller,facing,shape).contains(part); }
    }
    public record LocalModule(int x, int y, int depth, Material material) {
        public LocalModule { if (!material.module()) throw new IllegalArgumentException("Not an industrial module"); }
    }
    public record Module(BlockPos position, IndustrialModuleKind kind, int x, int y, int depth) {
        public Module { position = position.immutable(); Objects.requireNonNull(kind); }
    }
    public record Inspection(String diagnostic, List<LocalModule> modules) {
        public Inspection { modules=List.copyOf(modules); if(!DIAGNOSTICS.contains(diagnostic))throw new IllegalArgumentException("Invalid diagnostic"); }
        public boolean formed() { return diagnostic.equals("ready"); }
        public int moduleCount() { return formed() ? (int)modules.stream().filter(m->m.y==0).count() : 0; }
    }
    public record Formation(boolean formed, @Nullable Shape shape, List<Port> ports, List<Module> modules, String diagnostic) {
        public static final Formation EMPTY = invalid(null,"structure");
        public Formation {
            ports = List.copyOf(ports); modules = List.copyOf(modules);
            if (!DIAGNOSTICS.contains(diagnostic) || formed != diagnostic.equals("ready")
                    || formed && (shape == null || modules.isEmpty()) || !formed && (!ports.isEmpty() || !modules.isEmpty()))
                throw new IllegalArgumentException("Invalid formation");
        }
        public int moduleCount() { return formed ? (int)modules.stream().filter(m->m.y==0).count() : 0; }
        public int baseWorkRate(MachineKind kind) { return workRate(kind,moduleCount()); }
        public String layoutFingerprint() {
            return moduleFingerprint(modules.stream().map(m->new LocalModule(m.x,m.y,m.depth,Material.valueOf(m.kind.name()))).toList());
        }
        static Formation invalid(@Nullable Shape shape,String diagnostic) { return new Formation(false,shape,List.of(),List.of(),diagnostic); }
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
        return switch (kind) { case INDUSTRIAL_PREPARATOR -> 5; case INDUSTRIAL_REACTOR -> 4;
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
    /** Rays traverse recognized interior parts, but only shell materials can terminate a ray. */
    public static Optional<Shape> detect(MachineKind kind, Materials blocks) {
        if (!isIndustrial(kind) || !blocks.at(0, 0, 1).interior()) return Optional.empty();
        int left = ray(blocks,-1,0,0), right = ray(blocks,1,0,0), rear = ray(blocks,0,0,1), top = ray(blocks,0,1,0);
        if (left < 1 || right < 1 || rear < 1 || top < 1) return Optional.empty();
        try {
            var shape = new Shape(left + right + 1, top + 2, rear + 2, left);
            return Optional.of(shape);
        } catch (IllegalArgumentException oversized) { return Optional.empty(); }
    }
    private static int ray(Materials blocks, int dx, int dy, int dz) {
        for (int distance = 1; distance < MAX_SIZE; distance++) {
            Material material = blocks.at(dx * distance, dy * distance, 1 + dz * distance);
            if (material.interior()) continue;
            return material == Material.CASING || material == Material.GLASS || material == Material.PORT ? distance : -1;
        }
        return -1;
    }
    public static boolean validates(MachineKind kind, Shape shape, Materials blocks) {
        return inspectLayout(kind,shape,blocks).formed();
    }
    /** Exactly one full floor-anchored work column per occupied interior X/Z; no hidden contents. */
    public static Inspection inspectLayout(MachineKind kind, Shape shape, Materials blocks) {
        if (!isIndustrial(kind) || shape.height < height(kind)) return invalidLayout("structure_too_short");
        int ports = 0;
        var modules = new ArrayList<LocalModule>();
        for (var cell : layout(shape)) {
            var material = blocks.at(cell.x,cell.y,cell.depth);
            if(material==Material.UNLOADED)return invalidLayout("structure_unloaded");
            switch (cell.role) {
                case CONTROLLER -> { if (material != Material.CONTROLLER) return invalidLayout("structure"); }
                case INTERIOR -> {
                    if(material.module())modules.add(new LocalModule(cell.x,cell.y,cell.depth,material));
                    else if(material!=Material.AIR)return invalidLayout("structure_interior");
                }
                case FRAME -> { if (material != Material.CASING) return invalidLayout("structure_frame"); }
                case WALL -> { if (material == Material.PORT) ports++; else if (material != Material.CASING && material != Material.GLASS) return invalidLayout("structure_wall"); }
            }
        }
        if(ports<2)return invalidLayout("structure_ports");
        if(modules.isEmpty())return invalidLayout("structure_modules_missing");
        var required = kind == MachineKind.INDUSTRIAL_PREPARATOR
                ? List.of(Material.MIXING_BASIN,Material.SEPARATION_UNIT,Material.GRINDING_UNIT)
                : List.of(Material.FIELD_COIL,Material.REACTION_CHAMBER);
        var columns = new HashMap<Long,Integer>();
        for(var module:modules){
            if(!required.contains(module.material))return invalidLayout("structure_module_type");
            if(module.y>=required.size() || module.material!=required.get(module.y))return invalidLayout("structure_module_position");
            long key=((long)module.x<<32)|(module.depth&0xffffffffL);
            columns.merge(key,1,Integer::sum);
        }
        if(columns.values().stream().anyMatch(count->count!=required.size()))return invalidLayout("structure_module_incomplete");
        return new Inspection("ready",modules);
    }
    private static Inspection invalidLayout(String diagnostic) { return new Inspection(diagnostic,List.of()); }
    public static int workRate(MachineKind kind,int columns) {
        if(!isIndustrial(kind)||columns<0||columns>49)throw new IllegalArgumentException("Invalid module count");
        return (kind==MachineKind.INDUSTRIAL_PREPARATOR?4:2)*columns;
    }
    /** An exact canonical description, not a collision-prone hash or a count-only job identity. */
    public static String moduleFingerprint(List<LocalModule> modules) {
        var ordered = new ArrayList<>(modules);
        ordered.sort(Comparator.comparingInt(LocalModule::x).thenComparingInt(LocalModule::depth).thenComparingInt(LocalModule::y));
        var key=new StringBuilder();
        for(var module:ordered)key.append(module.x).append(',').append(module.y).append(',').append(module.depth).append('=').append(module.material.name()).append(';');
        return key.toString();
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
        return bounds(machine.getBlockPos(),facing,shape);
    }
    private static Bounds bounds(BlockPos controller,Direction facing,Shape shape) {
        var a = worldPosition(controller,facing,new Cell(-shape.controllerColumn,-1,0,Role.FRAME,null));
        var b = worldPosition(controller,facing,new Cell(shape.width-shape.controllerColumn-1,shape.height-2,shape.depth-1,Role.FRAME,null));
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
        if(shape!=null){
            var inspection=inspectLayout(machine.kind(),shape,blocks);
            if(!inspection.formed())result=Formation.invalid(shape,inspection.diagnostic);
            else {
            var ports=new ArrayList<Port>();
            for(var cell:layout(shape))if(cell.role==Role.WALL){
                var pos=worldPosition(machine.getBlockPos(),facing,cell);
                if(level.getBlockState(pos).is(ModContent.MACHINE_PORT.get()))ports.add(new Port(pos,worldDirection(facing,cell.outward)));
            }
            var modules=new ArrayList<Module>();
            for(var module:inspection.modules){
                var pos=worldPosition(machine.getBlockPos(),facing,new Cell(module.x,module.y,module.depth,Role.INTERIOR,null));
                modules.add(new Module(pos,IndustrialModuleKind.valueOf(module.material.name()),module.x,module.y,module.depth));
            }
            result=new Formation(true,shape,ports,modules,"ready");
            }
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
        if(state.getBlock() instanceof IndustrialModuleBlock module && level.getBlockEntity(pos) instanceof IndustrialModuleBlockEntity)
            return Material.valueOf(module.kind().name());
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
            if(otherShell.formed&&otherShell.ports.stream().anyMatch(port->portPositions.contains(port.position))){result=Formation.invalid(basic.shape,"structure_ambiguous");break;}
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
            var shell=raw(machine);var claim=machine.industrialProtectionClaim();
            boolean inside=shell.shape!=null&&bounds(machine,shell.shape).contains(partPos)
                    || claim!=null&&claim.contains(machine.getBlockPos(),partPos);
            if(portOnly?shell.formed&&shell.ports.stream().anyMatch(port->port.position.equals(partPos)):inside)result.add(machine);
        }
        return List.copyOf(result);
    }
}
