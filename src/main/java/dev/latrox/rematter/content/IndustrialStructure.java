package dev.latrox.rematter.content;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
            "structure_frame", "structure_wall", "structure_endcaps", "structure_ports", "structure_modules_missing", "structure_module_incomplete",
            "structure_module_position", "structure_module_type", "structure_interior", "structure_ambiguous", "structure_claimed");
    public record Cell(int x, int y, int depth, Role role, Direction outward) {}
    public record Shape(int width, int height, int depth, int controllerColumn) {
        public Shape {
            if (width < 3 || width > MAX_SIZE || height < 3 || height > MAX_SIZE || depth < 3 || depth > MAX_SIZE
                    || controllerColumn < 1 || controllerColumn >= width - 1) throw new IllegalArgumentException("Invalid industrial dimensions");
        }
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
        public boolean overlaps(BlockPos controller,ProtectionClaim other,BlockPos otherController) {
            return bounds(controller,facing,shape).intersects(bounds(otherController,other.facing,other.shape));
        }
    }
    public record LocalModule(int x, int y, int depth, Material material) {
        public LocalModule { if (!material.module()) throw new IllegalArgumentException("Not an industrial module"); }
    }
    public record Module(BlockPos position, IndustrialModuleKind kind, int x, int y, int depth) {
        public Module { position = position.immutable(); Objects.requireNonNull(kind); }
    }
    public record Inspection(String diagnostic, List<LocalModule> modules, Cell errorCell, Material expected) {
        public Inspection(String diagnostic,List<LocalModule> modules) {this(diagnostic,modules,null,null);}
        public Inspection { modules=List.copyOf(modules); if(!DIAGNOSTICS.contains(diagnostic))throw new IllegalArgumentException("Invalid diagnostic"); }
        public boolean formed() { return diagnostic.equals("ready"); }
        public int moduleCount() { return formed() ? coreCount(modules) : 0; }
        public IndustrialPerformance performance() { return formed() ? performanceOf(modules) : IndustrialPerformance.EMPTY; }
    }
    /** One observed shell. Derived values are computed once, because every tick asks for them several times. */
    public record Mismatch(BlockPos position,String expected,String orientation) {public Mismatch {position=position.immutable();}}
    public static final class Formation {
        public static final Formation EMPTY = invalid(null,"structure");
        private final boolean formed;
        private final @Nullable Shape shape;
        private final List<Port> ports;
        private final List<Module> modules;
        private final String diagnostic;
        private final Set<BlockPos> modulePositions;
        private final IndustrialPerformance performance;
        private final String layoutFingerprint;
        private Mismatch mismatch;
        public Mismatch mismatch() {return mismatch;}
        Formation(boolean formed, @Nullable Shape shape, List<Port> ports, List<Module> modules, String diagnostic) {
            this.ports = List.copyOf(ports); this.modules = List.copyOf(modules);
            if (!DIAGNOSTICS.contains(diagnostic) || formed != diagnostic.equals("ready")
                    || formed && (shape == null || modules.isEmpty()) || !formed && (!ports.isEmpty() || !modules.isEmpty()))
                throw new IllegalArgumentException("Invalid formation");
            this.formed = formed; this.shape = shape; this.diagnostic = diagnostic;
            var local = this.modules.stream().map(m -> new LocalModule(m.x, m.y, m.depth, Material.valueOf(m.kind.name()))).toList();
            modulePositions = this.modules.stream().map(Module::position).collect(java.util.stream.Collectors.toUnmodifiableSet());
            performance = formed ? performanceOf(local) : IndustrialPerformance.EMPTY;
            layoutFingerprint = moduleFingerprint(local);
        }
        public boolean formed() { return formed; }
        public @Nullable Shape shape() { return shape; }
        public List<Port> ports() { return ports; }
        public List<Module> modules() { return modules; }
        public String diagnostic() { return diagnostic; }
        public boolean hasModuleAt(BlockPos position) { return modulePositions.contains(position); }
        public int moduleCount() { return formed ? (int) modules.stream().filter(m -> m.kind == IndustrialModuleKind.GRINDING_UNIT || m.kind == IndustrialModuleKind.REACTION_CHAMBER).count() : 0; }
        public IndustrialPerformance performance() { return performance; }
        public int baseWorkRate(MachineKind kind) { return performance.workRate(kind); }
        public String layoutFingerprint() { return layoutFingerprint; }
        static Formation invalid(@Nullable Shape shape,String diagnostic) { return new Formation(false,shape,List.of(),List.of(),diagnostic); }
    }
    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(BlockPos p) { return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ; }
        Bounds expand(int amount) { return new Bounds(minX-amount,minY-amount,minZ-amount,maxX+amount,maxY+amount,maxZ+amount); }
        boolean intersects(Bounds o) { return minX <= o.maxX && o.minX <= maxX && minY <= o.maxY && o.minY <= maxY && minZ <= o.maxZ && o.minZ <= maxZ; }
    }
    private record Stamp(List<ChunkRevisions.Stamp> chunks) {
        boolean matches(ServerLevel level) { return chunks.stream().allMatch(chunk -> chunk.matches(level)); }
    }
    /** What one controller last observed, with the game tick and epoch of its last confirmation. */
    private static final class Observation {
        private @Nullable Stamp stamp;
        private Formation raw = Formation.EMPTY;
        private long rawTick = Long.MIN_VALUE, rawEpoch;
        private @Nullable Settled settled;
        private long settledTick = Long.MIN_VALUE, settledEpoch;
    }
    /** A shell after the port rule, valid while its neighbourhood and every neighbour's shell and stake are unchanged. */
    private record Settled(Formation raw, Stamp area, Claimant self, List<Neighbor> neighbors, Formation result) {
        boolean current(ServerLevel level, Formation basic, Claimant claimant) {
            if (raw != basic || !self.equals(claimant) || !area.matches(level)) return false;
            for (var neighbor : neighbors) {
                var machine = neighbor.machine.get();
                if (machine == null) return false;
                var shell = IndustrialStructure.raw(machine);
                if (shell != neighbor.raw || !claimant(machine, shell).equals(neighbor.claimant)) return false;
            }
            return true;
        }
    }
    private record Neighbor(WeakReference<MachineBlockEntity> machine, Formation raw, Claimant claimant) {}
    /** One controller's stake in port positions: the ports of its formed shell and its retained protection claim. */
    record Claimant(BlockPos controller, @Nullable UUID owner, Set<BlockPos> ports, @Nullable ProtectionClaim claim) {
        Claimant { ports = Set.copyOf(ports); }
        boolean claims(BlockPos position) { return claim != null && claim.contains(controller, position); }
    }
    private static final class PortObservation {
        private @Nullable WeakReference<MachineBlockEntity> controller;
        private @Nullable Stamp vacant;
        private long tick = Long.MIN_VALUE, epoch;
    }
    private static final Map<MachineBlockEntity, Observation> OBSERVATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MachinePortBlockEntity, PortObservation> PORTS = Collections.synchronizedMap(new WeakHashMap<>());
    private IndustrialStructure() {}
    public static boolean isIndustrial(MachineKind kind) { return kind == MachineKind.INDUSTRIAL_PREPARATOR || kind == MachineKind.INDUSTRIAL_REACTOR; }
    /** Minimum outer height of each chamber type. */
    public static int height(MachineKind kind) {
        return switch (kind) { case INDUSTRIAL_PREPARATOR -> 5; case INDUSTRIAL_REACTOR -> 4;
            default -> throw new IllegalArgumentException("Not an industrial controller"); };
    }
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
    /** Validate the complete shell and the modules mounted on its inner floor and ceiling. */
    public static Inspection inspectLayout(MachineKind kind, Shape shape, Materials blocks) {
        if (!isIndustrial(kind) || shape.height < height(kind)) return invalidLayout("structure_too_short");
        int ports = 0;
        var modules = new ArrayList<LocalModule>();
        for (var cell : layout(shape)) {
            var material = blocks.at(cell.x,cell.y,cell.depth);
            if(material==Material.UNLOADED)return invalidLayout("structure_unloaded",cell,Material.AIR);
            switch (cell.role) {
                case CONTROLLER -> { if (material != Material.CONTROLLER) return invalidLayout("structure",cell,Material.CONTROLLER); }
                case INTERIOR -> {
                    if(material.module())modules.add(new LocalModule(cell.x,cell.y,cell.depth,material));
                    else if(material!=Material.AIR)return invalidLayout("structure_interior",cell,Material.AIR);
                }
                case FRAME -> { if (material != Material.CASING) return invalidLayout("structure_frame",cell,Material.CASING); }
                case WALL -> {
                    if (kind == MachineKind.INDUSTRIAL_REACTOR && cell.outward.getAxis().isVertical()) {
                        if (material != Material.CASING) return invalidLayout("structure_endcaps",cell,Material.CASING);
                    } else if (material == Material.PORT) ports++;
                    else if (material != Material.CASING && material != Material.GLASS) return invalidLayout("structure_wall",cell,Material.GLASS);
                }
            }
        }
        if(ports<2) {
            var missing=layout(shape).stream().filter(cell->cell.role==Role.WALL&&cell.outward.getAxis().isHorizontal()
                    &&blocks.at(cell.x,cell.y,cell.depth)!=Material.PORT).findFirst().orElse(null);
            return invalidLayout("structure_ports",missing,Material.PORT);
        }
        if(modules.isEmpty())return invalidLayout("structure_modules_missing",new Cell(0,0,1,Role.INTERIOR,Direction.UP),
                kind==MachineKind.INDUSTRIAL_PREPARATOR?Material.MIXING_BASIN:Material.REACTION_CHAMBER);
        var required = kind == MachineKind.INDUSTRIAL_PREPARATOR
                ? List.of(Material.MIXING_BASIN,Material.SEPARATION_UNIT,Material.GRINDING_UNIT)
                : List.of(Material.FIELD_COIL,Material.REACTION_CHAMBER);
        for (var module : modules)
            if (!required.contains(module.material)) return invalidLayout("structure_module_type",new Cell(module.x,module.y,module.depth,Role.INTERIOR,null),required.getFirst());
        int crown = shape.height - 3;
        for (var module : modules) {
            if (IndustrialModuleMount.atInteriorHeight(module.y, crown).isEmpty())
                return invalidLayout("structure_module_position",new Cell(module.x,module.y,module.depth,Role.INTERIOR,null),Material.AIR);
        }
        for(var missing:required)if(modules.stream().noneMatch(module -> module.material == missing)) {
            var cell=layout(shape).stream().filter(c->c.role==Role.INTERIOR&&(c.y==0||c.y==crown)
                    &&blocks.at(c.x,c.y,c.depth)==Material.AIR).findFirst().orElse(null);
            return invalidLayout("structure_module_incomplete",cell,missing);
        }
        return new Inspection("ready",modules);
    }
    private static int coreCount(List<LocalModule> modules) {
        return (int) modules.stream().filter(m -> m.material == Material.GRINDING_UNIT || m.material == Material.REACTION_CHAMBER).count();
    }
    private static IndustrialPerformance performanceOf(List<LocalModule> modules) {
        int grinding = 0, mixing = 0, separation = 0, coils = 0, chambers = 0;
        for (var module : modules) switch (module.material) {
            case GRINDING_UNIT -> grinding++;
            case MIXING_BASIN -> mixing++;
            case SEPARATION_UNIT -> separation++;
            case FIELD_COIL -> coils++;
            case REACTION_CHAMBER -> chambers++;
            default -> { }
        }
        return new IndustrialPerformance(grinding, mixing, separation, coils, chambers);
    }
    private static Inspection invalidLayout(String diagnostic) { return new Inspection(diagnostic,List.of()); }
    private static Inspection invalidLayout(String diagnostic,Cell cell,Material expected) {return new Inspection(diagnostic,List.of(),cell,expected);}
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
        return box(worldPosition(controller,facing,new Cell(-shape.controllerColumn,-1,0,Role.FRAME,null)),
                worldPosition(controller,facing,new Cell(shape.width-shape.controllerColumn-1,shape.height-2,shape.depth-1,Role.FRAME,null)));
    }
    /** Every block the detection rays and the largest possible shell can read around one controller. */
    private static Bounds reach(MachineBlockEntity machine) {
        var facing=machine.getBlockState().getValue(MachineBlock.FACING);
        return box(worldPosition(machine.getBlockPos(),facing,new Cell(1-MAX_SIZE,-1,0,Role.INTERIOR,null)),
                worldPosition(machine.getBlockPos(),facing,new Cell(MAX_SIZE-1,MAX_SIZE-1,MAX_SIZE,Role.INTERIOR,null)));
    }
    private static Bounds box(BlockPos a, BlockPos b) {
        return new Bounds(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),
                Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
    }
    private static Bounds around(BlockPos p, int radius) { return new Bounds(p.getX()-radius,p.getY()-radius,p.getZ()-radius,p.getX()+radius,p.getY()+radius,p.getZ()+radius); }
    private static Stamp stamp(ServerLevel level, Bounds area) {
        var chunks = new ArrayList<ChunkRevisions.Stamp>();
        for (int x=area.minX>>4;x<=area.maxX>>4;x++) for(int z=area.minZ>>4;z<=area.maxZ>>4;z++) chunks.add(ChunkRevisions.Stamp.capture(level,x,z));
        return new Stamp(List.copyOf(chunks));
    }
    private static boolean live(MachineBlockEntity machine) {
        return isIndustrial(machine.kind()) && machine.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()
                && !machine.isRemoved() && level.hasChunkAt(machine.getBlockPos()) && level.getBlockEntity(machine.getBlockPos()) == machine;
    }
    /** The shell alone. Within one game tick an unchanged epoch confirms it; otherwise its watched chunks must be unchanged. */
    private static Formation raw(MachineBlockEntity machine) {
        if (!live(machine)) return Formation.EMPTY;
        var level=(ServerLevel)machine.getLevel();
        var seen=OBSERVATIONS.computeIfAbsent(machine,ignored->new Observation());
        long tick=level.getGameTime(),epoch=ChunkRevisions.epoch();
        if(seen.rawTick==tick&&seen.rawEpoch==epoch)return seen.raw;
        if(seen.stamp==null||!seen.stamp.matches(level)){
            var observed=stamp(level,reach(machine));
            if(!observed.matches(level))return Formation.EMPTY;
            var result=scan(level,machine);
            // A reentrant block/BE callback cannot publish a result observed during mutation.
            if(!observed.matches(level))return Formation.EMPTY;
            seen.stamp=observed;seen.raw=result;seen.settled=null;
        }
        seen.rawTick=tick;seen.rawEpoch=epoch;
        return seen.raw;
    }
    private static Formation scan(ServerLevel level,MachineBlockEntity machine) {
        var facing=machine.getBlockState().getValue(MachineBlock.FACING);
        Materials blocks=(x,y,depth)->material(level,machine,worldPosition(machine.getBlockPos(),facing,new Cell(x,y,depth,Role.INTERIOR,null)));
        var shape=detect(machine.kind(),blocks).orElse(null);
        if(shape==null)shape=new Shape(3,height(machine.kind()),machine.kind()==MachineKind.INDUSTRIAL_PREPARATOR?4:3,1);
        var inspection=inspectLayout(machine.kind(),shape,blocks);
        if(!inspection.formed()) {
            var failure=Formation.invalid(shape,inspection.diagnostic);
            if(inspection.errorCell()!=null&&inspection.expected()!=null) {
                var cell=inspection.errorCell();String expected=switch(inspection.expected()) {
                    case AIR -> "block.minecraft.air";
                    case CASING -> "block.rematter.reactor_casing";
                    case GLASS -> "block.rematter.reactor_glass";
                    case PORT -> "block.rematter.machine_port";
                    case CONTROLLER -> "block.rematter."+machine.kind().id();
                    default -> "block.rematter."+inspection.expected().name().toLowerCase(java.util.Locale.ROOT);
                };
                failure.mismatch=new Mismatch(worldPosition(machine.getBlockPos(),facing,cell),expected,
                        cell.y==0?"up":cell.y==shape.height-3?"down":"");
            }
            return failure;
        }
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
        return new Formation(true,shape,ports,modules,"ready");
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
    /** The shell after the port and claim rules. Repeated calls in one tick cost one map lookup while nothing structural changed. */
    public static Formation inspect(MachineBlockEntity machine) {
        Formation basic=raw(machine);
        if(!basic.formed)return basic;
        var level=(ServerLevel)machine.getLevel();var seen=OBSERVATIONS.get(machine);
        long tick=level.getGameTime(),epoch=ChunkRevisions.epoch();
        if(seen.settled!=null&&seen.settledTick==tick&&seen.settledEpoch==epoch)return seen.settled.result;
        var self=claimant(machine,basic);
        if(seen.settled==null||!seen.settled.current(level,basic,self)){
            // Every controller whose shell or claim could reach one of these ports stands in this area.
            var area=bounds(machine,basic.shape).expand(MAX_SIZE-1);
            var observed=stamp(level,area);
            var neighbors=new ArrayList<Neighbor>();
            for(var other:candidates(level,area))if(other!=machine){
                var shell=raw(other);
                neighbors.add(new Neighbor(new WeakReference<>(other),shell,claimant(other,shell)));
            }
            var others=neighbors.stream().map(Neighbor::claimant).toList();
            var shell=new ProtectionClaim(basic.shape,machine.getBlockState().getValue(MachineBlock.FACING));
            var result=!keepsPorts(self,others)?Formation.invalid(basic.shape,"structure_ambiguous")
                    :!clearOfForeignClaims(self,shell,others)?Formation.invalid(basic.shape,"structure_claimed"):basic;
            if(!observed.matches(level))return Formation.EMPTY;
            seen.settled=new Settled(basic,observed,self,List.copyOf(neighbors),result);
        }
        seen.settledTick=tick;seen.settledEpoch=epoch;
        return seen.settled.result;
    }
    private static Claimant claimant(MachineBlockEntity machine,Formation shell) {
        var ports=new HashSet<BlockPos>();shell.ports.forEach(port->ports.add(port.position));
        return new Claimant(machine.getBlockPos(),machine.networkOwner(),ports,machine.industrialProtectionClaim());
    }
    /**
     * Shells of one owner that share a port both stop. Against another owner a port belongs only to its sole claimant:
     * no shell forms over a port inside a foreign protection claim, and a shell keeps a port that a foreign shell also
     * uses only while its own claim covers that port.
     */
    static boolean keepsPorts(Claimant self,List<Claimant> others) {
        for(var other:others)for(var port:self.ports()){
            if(Objects.equals(self.owner(),other.owner())){if(other.ports().contains(port))return false;}
            else if(other.claims(port)||other.ports().contains(port)&&!self.claims(port))return false;
        }
        return true;
    }
    /**
     * A shell never forms over a block inside another owner's protection claim. The first chamber to form keeps its
     * space, so a later foreign shell can neither take its parts nor protect them against their owner.
     */
    static boolean clearOfForeignClaims(Claimant self,ProtectionClaim shell,List<Claimant> others) {
        for(var other:others)if(!Objects.equals(self.owner(),other.owner())&&other.claim()!=null
                &&other.claim().overlaps(other.controller(),shell,self.controller()))return false;
        return true;
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
    private static boolean listsPort(MachineBlockEntity machine,BlockPos port){
        var shell=inspect(machine);
        return shell.formed&&shell.ports.stream().anyMatch(candidate->candidate.position.equals(port));
    }
    /**
     * The port rule leaves at most one formed shell per port, so a controller whose shell still lists the port stays the
     * only one. Without a controller, the search repeats only after a structural change wherever a shell could reach it.
     */
    static @Nullable MachineBlockEntity associatedController(MachinePortBlockEntity port){
        if(!(port.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||port.isRemoved()
                ||!level.hasChunkAt(port.getBlockPos())||level.getBlockEntity(port.getBlockPos())!=port)return null;
        var seen=PORTS.computeIfAbsent(port,ignored->new PortObservation());
        long tick=level.getGameTime(),epoch=ChunkRevisions.epoch();
        var known=seen.controller==null?null:seen.controller.get();
        if(seen.tick==tick&&seen.epoch==epoch)return known!=null&&live(known)?known:null;
        if(known!=null&&listsPort(known,port.getBlockPos())||known==null&&seen.vacant!=null&&seen.vacant.matches(level)){
            seen.tick=tick;seen.epoch=epoch;return known;
        }
        var observed=stamp(level,around(port.getBlockPos(),2*MAX_SIZE-1));
        if(!observed.matches(level))return null;
        MachineBlockEntity found=null;
        for(var machine:candidates(level,around(port.getBlockPos(),MAX_SIZE-1))){
            if(listsPort(machine,port.getBlockPos())){
                if(found!=null){found=null;break;}found=machine;
            }
        }
        if(!observed.matches(level))return null;
        seen.controller=found==null?null:new WeakReference<>(found);seen.vacant=found==null?observed:null;
        seen.tick=tick;seen.epoch=epoch;
        return found;
    }
    /** Controllers whose formed shell or retained claim contains the part. An unformed shape protects nothing. */
    static List<MachineBlockEntity> controllersUsing(ServerLevel level,BlockPos partPos){
        if(!level.getServer().isSameThread()||!level.hasChunkAt(partPos))return List.of();
        var result=new ArrayList<MachineBlockEntity>();
        for(var machine:candidates(level,around(partPos,MAX_SIZE-1))){
            var shell=inspect(machine);var claim=machine.industrialProtectionClaim();
            if(shell.formed&&bounds(machine,shell.shape).contains(partPos)
                    || claim!=null&&claim.contains(machine.getBlockPos(),partPos))result.add(machine);
        }
        return List.copyOf(result);
    }
}
