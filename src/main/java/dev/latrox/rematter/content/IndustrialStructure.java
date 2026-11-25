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
    public static final int MAX_HINTS = 512;
    public enum Role { CONTROLLER, INTERIOR, FRAME, WALL }
    public enum Material { AIR, CASING, GLASS, PORT, CONTROLLER, OTHER, UNLOADED,
        MIXING_BASIN, SEPARATION_UNIT, GRINDING_UNIT, FIELD_COIL, REACTION_CHAMBER,
        PARTICLE_EMITTER, PRIMAL_CONDENSER_CORE, REACTOR_FUEL_CORE, REACTOR_INDUCTION_COIL,
        ENERGY_MATRIX_CELL, REINFORCED_ENERGY_MATRIX_CELL, RESONANT_ENERGY_MATRIX_CELL, PRIMAL_ENERGY_MATRIX_CELL,
        ENERGY_MATRIX_PROVIDER, REINFORCED_ENERGY_MATRIX_PROVIDER, RESONANT_ENERGY_MATRIX_PROVIDER, PRIMAL_ENERGY_MATRIX_PROVIDER;
        public boolean isReactorModule() {
            return ordinal() >= MIXING_BASIN.ordinal() && ordinal() <= REACTOR_INDUCTION_COIL.ordinal();
        }
        public boolean isMatrixCell() {
            return ordinal() >= ENERGY_MATRIX_CELL.ordinal() && ordinal() <= PRIMAL_ENERGY_MATRIX_CELL.ordinal();
        }
        public boolean isMatrixProvider() {
            return ordinal() >= ENERGY_MATRIX_PROVIDER.ordinal() && ordinal() <= PRIMAL_ENERGY_MATRIX_PROVIDER.ordinal();
        }
        public boolean isMatrixPart() {
            return isMatrixCell() || isMatrixProvider();
        }
        boolean module() { return isReactorModule() || isMatrixPart(); }
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
    public record LocalMismatch(Cell cell, Material expected, String orientation) {}
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
        private List<Mismatch> mismatches = List.of();
        private @Nullable BlockPos minBounds, maxBounds;
        public @Nullable Mismatch mismatch() {return mismatch;}
        public List<Mismatch> mismatches() {return mismatches;}
        public @Nullable BlockPos minBounds() {return minBounds;}
        public @Nullable BlockPos maxBounds() {return maxBounds;}
        Formation(boolean formed, @Nullable Shape shape, List<Port> ports, List<Module> modules, List<LocalModule> parts, String diagnostic) {
            this.ports = List.copyOf(ports); this.modules = List.copyOf(modules);
            var localParts = List.copyOf(parts);
            if (!DIAGNOSTICS.contains(diagnostic) || formed != diagnostic.equals("ready")
                    || formed && (shape == null || localParts.isEmpty()) || !formed && (!ports.isEmpty() || !modules.isEmpty() || !localParts.isEmpty()))
                throw new IllegalArgumentException("Invalid formation");
            this.formed = formed; this.shape = shape; this.diagnostic = diagnostic;
            modulePositions = this.modules.stream().map(Module::position).collect(java.util.stream.Collectors.toUnmodifiableSet());
            performance = formed ? performanceOf(localParts) : IndustrialPerformance.EMPTY;
            layoutFingerprint = moduleFingerprint(localParts);
        }
        public boolean formed() { return formed; }
        public @Nullable Shape shape() { return shape; }
        public List<Port> ports() { return ports; }
        public List<Module> modules() { return modules; }
        public String diagnostic() { return diagnostic; }
        public boolean hasModuleAt(BlockPos position) { return modulePositions.contains(position); }
        public int moduleCount() {
            if (!formed) return 0;
            if (performance.matrixCells() > 0) return performance.matrixCells();
            return (int) modules.stream().filter(m -> m.kind == IndustrialModuleKind.GRINDING_UNIT
                    || m.kind == IndustrialModuleKind.REACTION_CHAMBER
                    || m.kind == IndustrialModuleKind.PARTICLE_EMITTER
                    || m.kind == IndustrialModuleKind.PRIMAL_CONDENSER_CORE
                    || m.kind == IndustrialModuleKind.REACTOR_FUEL_CORE).count();
        }
        public IndustrialPerformance performance() { return performance; }
        public int baseWorkRate(MachineKind kind) { return performance.workRate(kind); }
        public int laneRoom(MachineKind kind) { return formed && shape != null ? IndustrialStructure.laneRoom(kind, shape.height()) : 0; }
        public String layoutFingerprint() { return layoutFingerprint; }
        static Formation invalid(@Nullable Shape shape,String diagnostic) { return new Formation(false,shape,List.of(),List.of(),List.of(),diagnostic); }
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
    public static boolean isIndustrial(MachineKind kind) {
        return kind == MachineKind.INDUSTRIAL_PREPARATOR || kind.isEnergyMatrix() || usesReactorModules(kind);
    }
    /** Controllers whose chamber mounts reactor-family modules inside solid-endcap shells. */
    public static boolean usesReactorModules(MachineKind kind) {
        return kind == MachineKind.INDUSTRIAL_REACTOR
                || kind == MachineKind.INDUSTRIAL_DECOMPOSER
                || kind == MachineKind.INDUSTRIAL_SYNTHESIZER
                || kind == MachineKind.TRANSMUTATION_REACTOR
                || kind == MachineKind.PRIMAL_REACTOR
                || kind == MachineKind.ENERGY_REACTOR;
    }
    /** Primary (ceiling-preferred) reactor module for a reactor controller kind. */
    public static Material primaryReactorModule(MachineKind kind) {
        return switch (kind) {
            case TRANSMUTATION_REACTOR -> Material.PARTICLE_EMITTER;
            case PRIMAL_REACTOR -> Material.PRIMAL_CONDENSER_CORE;
            case ENERGY_REACTOR -> Material.REACTOR_FUEL_CORE;
            case ENERGY_MATRIX -> Material.ENERGY_MATRIX_CELL;
            default -> Material.REACTION_CHAMBER;
        };
    }
    /** Secondary (floor-preferred) reactor module for a reactor controller kind. */
    public static Material secondaryReactorModule(MachineKind kind) {
        return switch (kind) {
            case PRIMAL_REACTOR -> Material.PARTICLE_EMITTER;
            case ENERGY_REACTOR -> Material.REACTOR_INDUCTION_COIL;
            case ENERGY_MATRIX -> Material.ENERGY_MATRIX_PROVIDER;
            default -> Material.FIELD_COIL;
        };
    }
    /** Required interior module types for any industrial controller. */
    public static List<Material> requiredModules(MachineKind kind) {
        if (kind == MachineKind.INDUSTRIAL_PREPARATOR) {
            return List.of(Material.MIXING_BASIN, Material.SEPARATION_UNIT, Material.GRINDING_UNIT);
        }
        return List.of(secondaryReactorModule(kind), primaryReactorModule(kind));
    }
    /** Minimum outer width and depth of each chamber type. */
    public static int minFootprint(MachineKind kind) {
        return kind == MachineKind.PRIMAL_REACTOR || kind == MachineKind.ENERGY_REACTOR ? 5 : 3;
    }
    /** Minimum outer height of each chamber type. */
    public static int height(MachineKind kind) {
        return switch (kind) {
            case ENERGY_MATRIX -> 3;
            case INDUSTRIAL_PREPARATOR, PRIMAL_REACTOR, ENERGY_REACTOR -> 5;
            case INDUSTRIAL_REACTOR, INDUSTRIAL_DECOMPOSER, INDUSTRIAL_SYNTHESIZER, TRANSMUTATION_REACTOR -> 4;
            default -> throw new IllegalArgumentException("Not an industrial controller");
        };
    }
    /** Extra outer height permits extra workers; it never multiplies a single recipe. */
    public static int laneRoom(MachineKind kind, int outerHeight) {
        int minimum = height(kind);
        return outerHeight < minimum ? 0 : Math.min(4, 1 + outerHeight - minimum);
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
    /**
     * Vertical floor/roof wall cells directly supporting or anchoring an interior module, Energy Matrix Cell,
     * or Energy Matrix Provider require a solid endcap (Casing, or a solid Port where vertical ports are permitted)
     * rather than Glass.
     */
    static boolean requiresSolidEndcap(Shape shape, Cell cell, Materials blocks) {
        if (cell.role != Role.WALL || cell.outward == null || !cell.outward.getAxis().isVertical()) return false;
        int interiorY = cell.outward == Direction.DOWN ? 0 : shape.height - 3;
        return blocks.at(cell.x, interiorY, cell.depth).module();
    }

    private static long columnKey(int x, int depth) {
        return ((long) (x + 16) << 8) | (depth + 16);
    }

    /** Validate the complete shell and the modules or Energy Matrix blocks inside it in a single volume pass. */
    public static Inspection inspectLayout(MachineKind kind, Shape shape, Materials blocks) {
        int minSide = minFootprint(kind);
        if (!isIndustrial(kind) || shape.height < height(kind) || shape.width < minSide || shape.depth < minSide) return invalidLayout("structure_too_short");
        if (kind.isEnergyMatrix() && (shape.width - 2) * (shape.height - 2) * (shape.depth - 2) < 2) return invalidLayout("structure_too_short");
        int ports = 0;
        boolean allowVerticalPorts = !usesReactorModules(kind);
        var modules = new ArrayList<LocalModule>();
        Map<Long, Cell> floorGlass = null;
        Map<Long, Cell> roofGlass = null;
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
                    if (cell.outward.getAxis().isVertical()) {
                        if (material == Material.PORT) {
                            if (allowVerticalPorts) {
                                ports++;
                            } else {
                                return invalidLayout("structure_endcaps", cell, Material.CASING);
                            }
                        } else if (material == Material.GLASS) {
                            if (cell.outward == Direction.DOWN) {
                                if (floorGlass == null) floorGlass = new HashMap<>();
                                floorGlass.put(columnKey(cell.x, cell.depth), cell);
                            } else {
                                if (roofGlass == null) roofGlass = new HashMap<>();
                                roofGlass.put(columnKey(cell.x, cell.depth), cell);
                            }
                        } else if (material != Material.CASING) {
                            return invalidLayout("structure_wall", cell, Material.GLASS);
                        }
                    } else if (material == Material.PORT) {
                        ports++;
                    } else if (material != Material.CASING && material != Material.GLASS) {
                        return invalidLayout("structure_wall", cell, Material.GLASS);
                    }
                }
            }
        }
        if (floorGlass != null || roofGlass != null) {
            int crown = shape.height - 3;
            for (var module : modules) {
                if (module.y == 0 && floorGlass != null) {
                    var glass = floorGlass.get(columnKey(module.x, module.depth));
                    if (glass != null) return invalidLayout("structure_endcaps", glass, Material.CASING);
                }
                if (module.y == crown && roofGlass != null) {
                    var glass = roofGlass.get(columnKey(module.x, module.depth));
                    if (glass != null) return invalidLayout("structure_endcaps", glass, Material.CASING);
                }
            }
        }
        if(ports<2) {
            var missing=layout(shape).stream().filter(cell->cell.role==Role.WALL
                    &&(allowVerticalPorts||cell.outward.getAxis().isHorizontal())
                    &&blocks.at(cell.x,cell.y,cell.depth)!=Material.PORT).findFirst().orElse(null);
            return invalidLayout("structure_ports",missing,Material.PORT);
        }
        if(modules.isEmpty())return invalidLayout("structure_modules_missing",new Cell(0,0,1,Role.INTERIOR,Direction.UP),
                kind==MachineKind.INDUSTRIAL_PREPARATOR?Material.MIXING_BASIN:primaryReactorModule(kind));
        if (kind.isEnergyMatrix()) {
            boolean hasCell = false, hasProvider = false;
            for (var module : modules) {
                if (module.material.isMatrixCell()) hasCell = true;
                else if (module.material.isMatrixProvider()) hasProvider = true;
                else return invalidLayout("structure_module_type", new Cell(module.x, module.y, module.depth, Role.INTERIOR, null), Material.ENERGY_MATRIX_CELL);
            }
            if (!hasCell) {
                var cell = layout(shape).stream().filter(c -> c.role == Role.INTERIOR && blocks.at(c.x, c.y, c.depth) == Material.AIR).findFirst().orElse(null);
                return invalidLayout("structure_module_incomplete", cell, Material.ENERGY_MATRIX_CELL);
            }
            if (!hasProvider) {
                var cell = layout(shape).stream().filter(c -> c.role == Role.INTERIOR && blocks.at(c.x, c.y, c.depth) == Material.AIR).findFirst().orElse(null);
                return invalidLayout("structure_module_incomplete", cell, Material.ENERGY_MATRIX_PROVIDER);
            }
            return new Inspection("ready", modules);
        }
        var required = requiredModules(kind);
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
        int cells = (int) modules.stream().filter(m -> m.material.isMatrixCell()).count();
        if (cells > 0) return cells;
        return (int) modules.stream().filter(m -> m.material == Material.GRINDING_UNIT || m.material == Material.REACTION_CHAMBER
                || m.material == Material.PARTICLE_EMITTER || m.material == Material.PRIMAL_CONDENSER_CORE
                || m.material == Material.REACTOR_FUEL_CORE).count();
    }
    private static IndustrialPerformance performanceOf(List<LocalModule> modules) {
        int grinding = 0, mixing = 0, separation = 0, coils = 0, chambers = 0, emitters = 0, condensers = 0, fuelCores = 0, inductionCoils = 0;
        int matrixCells = 0, matrixProviders = 0;
        long matrixCapacity = 0L, matrixTransferRate = 0L;
        for (var module : modules) switch (module.material) {
            case GRINDING_UNIT -> grinding++;
            case MIXING_BASIN -> mixing++;
            case SEPARATION_UNIT -> separation++;
            case FIELD_COIL -> coils++;
            case REACTION_CHAMBER -> chambers++;
            case PARTICLE_EMITTER -> emitters++;
            case PRIMAL_CONDENSER_CORE -> condensers++;
            case REACTOR_FUEL_CORE -> fuelCores++;
            case REACTOR_INDUCTION_COIL -> inductionCoils++;
            case ENERGY_MATRIX_CELL -> { matrixCells++; matrixCapacity = Math.addExact(matrixCapacity, dev.latrox.rematter.energy.EnergyMatrixCellTier.BASIC.capacity()); }
            case REINFORCED_ENERGY_MATRIX_CELL -> { matrixCells++; matrixCapacity = Math.addExact(matrixCapacity, dev.latrox.rematter.energy.EnergyMatrixCellTier.REINFORCED.capacity()); }
            case RESONANT_ENERGY_MATRIX_CELL -> { matrixCells++; matrixCapacity = Math.addExact(matrixCapacity, dev.latrox.rematter.energy.EnergyMatrixCellTier.RESONANT.capacity()); }
            case PRIMAL_ENERGY_MATRIX_CELL -> { matrixCells++; matrixCapacity = Math.addExact(matrixCapacity, dev.latrox.rematter.energy.EnergyMatrixCellTier.PRIMAL.capacity()); }
            case ENERGY_MATRIX_PROVIDER -> { matrixProviders++; matrixTransferRate = Math.addExact(matrixTransferRate, dev.latrox.rematter.energy.EnergyMatrixProviderTier.BASIC.transferRate()); }
            case REINFORCED_ENERGY_MATRIX_PROVIDER -> { matrixProviders++; matrixTransferRate = Math.addExact(matrixTransferRate, dev.latrox.rematter.energy.EnergyMatrixProviderTier.REINFORCED.transferRate()); }
            case RESONANT_ENERGY_MATRIX_PROVIDER -> { matrixProviders++; matrixTransferRate = Math.addExact(matrixTransferRate, dev.latrox.rematter.energy.EnergyMatrixProviderTier.RESONANT.transferRate()); }
            case PRIMAL_ENERGY_MATRIX_PROVIDER -> { matrixProviders++; matrixTransferRate = Math.addExact(matrixTransferRate, dev.latrox.rematter.energy.EnergyMatrixProviderTier.PRIMAL.transferRate()); }
            default -> { }
        }
        return new IndustrialPerformance(grinding, mixing, separation, coils, chambers, emitters, condensers,
                fuelCores, inductionCoils, matrixCells, matrixProviders, matrixCapacity, matrixTransferRate);
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
    /** Collects up to {@link #MAX_HINTS} missing or invalid cells across the target chamber shell and interior mounts. */
    public static List<LocalMismatch> collectMismatches(MachineKind kind, Shape shape, Materials blocks) {
        if (!isIndustrial(kind)) return List.of();
        var primary = inspectLayout(kind, shape, blocks);
        if (primary.formed()) return List.of();
        int minSide = minFootprint(kind);
        Shape target = shape.height < height(kind) || shape.width < minSide || shape.depth < minSide
                ? new Shape(Math.max(shape.width, minSide), Math.max(shape.height, height(kind)), Math.max(shape.depth, minSide),
                        Math.clamp(shape.controllerColumn, 1, Math.max(shape.width, minSide) - 2)) : shape;
        int crown = target.height - 3;
        var required = requiredModules(kind);
        var collected = new ArrayList<LocalMismatch>();
        var openWalls = new ArrayList<Cell>();
        var closedNonPortWalls = new ArrayList<Cell>();
        var freeFloor = new ArrayList<Cell>();
        var freeCeiling = new ArrayList<Cell>();
        var mountedTypes = EnumSet.noneOf(Material.class);
        int ports = 0;
        for (var cell : layout(target)) {
            var mat = blocks.at(cell.x, cell.y, cell.depth);
            if (mat == Material.UNLOADED) {
                return List.of(new LocalMismatch(cell, Material.AIR, ""));
            }
            switch (cell.role) {
                case CONTROLLER -> {
                    if (mat != Material.CONTROLLER) collected.add(new LocalMismatch(cell, Material.CONTROLLER, ""));
                }
                case FRAME -> {
                    if (mat != Material.CASING) collected.add(new LocalMismatch(cell, Material.CASING, ""));
                }
                case WALL -> {
                    if (cell.outward.getAxis().isVertical()) {
                        boolean needsMount = requiresSolidEndcap(target, cell, blocks);
                        if (mat == Material.PORT) {
                            if (!usesReactorModules(kind)) {
                                ports++;
                            } else {
                                collected.add(new LocalMismatch(cell, needsMount ? Material.CASING : Material.GLASS, ""));
                            }
                        } else if (needsMount) {
                            if (mat != Material.CASING) collected.add(new LocalMismatch(cell, Material.CASING, ""));
                        } else if (mat != Material.CASING && mat != Material.GLASS) {
                            openWalls.add(cell);
                        }
                    } else if (mat == Material.PORT) {
                        ports++;
                    } else if (mat != Material.CASING && mat != Material.GLASS) {
                        openWalls.add(cell);
                    } else {
                        closedNonPortWalls.add(cell);
                    }
                }
                case INTERIOR -> {
                    if (kind.isEnergyMatrix()) {
                        if (mat.isMatrixCell()) mountedTypes.add(Material.ENERGY_MATRIX_CELL);
                        else if (mat.isMatrixProvider()) mountedTypes.add(Material.ENERGY_MATRIX_PROVIDER);
                        else if (mat != Material.AIR) collected.add(new LocalMismatch(cell, Material.AIR, ""));
                        else freeFloor.add(cell);
                    } else if (mat.module()) {
                        if (!required.contains(mat)) {
                            String orient = cell.y == 0 ? "up" : cell.y == crown ? "down" : "";
                            collected.add(new LocalMismatch(cell, required.getFirst(), orient));
                        } else if (IndustrialModuleMount.atInteriorHeight(cell.y, crown).isEmpty()) {
                            collected.add(new LocalMismatch(cell, Material.AIR, ""));
                        } else {
                            mountedTypes.add(mat);
                        }
                    } else if (mat != Material.AIR) {
                        collected.add(new LocalMismatch(cell, Material.AIR, ""));
                    } else if (cell.y == 0) {
                        freeFloor.add(cell);
                    } else if (cell.y == crown) {
                        freeCeiling.add(cell);
                    }
                }
            }
        }
        int portsNeeded = Math.max(0, 2 - ports);
        for (var cell : openWalls) {
            if (portsNeeded > 0 && cell.outward != null && (!usesReactorModules(kind) || cell.outward.getAxis().isHorizontal())) {
                collected.add(new LocalMismatch(cell, Material.PORT, ""));
                portsNeeded--;
            } else {
                collected.add(new LocalMismatch(cell, Material.GLASS, ""));
            }
        }
        for (int i = 0; i < closedNonPortWalls.size() && portsNeeded > 0; i++, portsNeeded--) {
            collected.add(new LocalMismatch(closedNonPortWalls.get(i), Material.PORT, ""));
        }
        for (var req : required) {
            if (mountedTypes.contains(req)) continue;
            if (kind.isEnergyMatrix()) {
                Cell mount = !freeFloor.isEmpty() ? freeFloor.removeFirst() : null;
                if (mount != null) collected.add(new LocalMismatch(mount, req, ""));
                continue;
            }
            boolean preferCeiling = req == Material.GRINDING_UNIT || req == primaryReactorModule(kind);
            var firstPool = preferCeiling ? freeCeiling : freeFloor;
            var secondPool = preferCeiling ? freeFloor : freeCeiling;
            Cell mount = !firstPool.isEmpty() ? firstPool.removeFirst() : !secondPool.isEmpty() ? secondPool.removeFirst() : null;
            if (mount != null) {
                collected.add(new LocalMismatch(mount, req, mount.y == 0 ? "up" : "down"));
            }
        }
        var ordered = new ArrayList<LocalMismatch>(Math.min(MAX_HINTS, collected.size() + 1));
        var seen = new HashSet<Long>();
        if (primary.errorCell() != null && primary.expected() != null) {
            var c = primary.errorCell();
            String orient = primary.expected().isReactorModule() ? (c.y == 0 ? "up" : c.y == crown ? "down" : "") : "";
            ordered.add(new LocalMismatch(c, primary.expected(), orient));
            seen.add(cellKey(c.x, c.y, c.depth));
        }
        for (var entry : collected) {
            if (ordered.size() >= MAX_HINTS) break;
            if (seen.add(cellKey(entry.cell.x, entry.cell.y, entry.cell.depth))) {
                ordered.add(entry);
            }
        }
        return List.copyOf(ordered);
    }
    private static long cellKey(int x, int y, int depth) {
        return ((long) (x + 16) << 16) | ((long) (y + 16) << 8) | (depth + 16);
    }
    public static String expectedBlockKey(MachineKind controllerKind, Material expected) {
        return switch (expected) {
            case AIR -> "block.minecraft.air";
            case CASING -> switch (controllerKind) {
                case TRANSMUTATION_REACTOR -> "block.rematter.transmutation_reactor_casing";
                case PRIMAL_REACTOR -> "block.rematter.primal_reactor_casing";
                case ENERGY_MATRIX -> "block.rematter.energy_matrix_casing";
                case ENERGY_REACTOR -> "block.rematter.energy_reactor_casing";
                default -> "block.rematter.reactor_casing";
            };
            case GLASS -> switch (controllerKind) {
                case TRANSMUTATION_REACTOR -> "block.rematter.transmutation_reactor_glass";
                case PRIMAL_REACTOR -> "block.rematter.primal_reactor_glass";
                case ENERGY_MATRIX -> "block.rematter.energy_matrix_glass";
                case ENERGY_REACTOR -> "block.rematter.energy_reactor_glass";
                default -> "block.rematter.reactor_glass";
            };
            case PORT -> switch (controllerKind) {
                case TRANSMUTATION_REACTOR -> "block.rematter.transmutation_machine_port";
                case PRIMAL_REACTOR -> "block.rematter.primal_machine_port";
                case ENERGY_MATRIX -> "block.rematter.energy_matrix_port";
                case ENERGY_REACTOR -> "block.rematter.energy_reactor_port";
                default -> "block.rematter.machine_port";
            };
            case CONTROLLER -> "block.rematter." + controllerKind.id();
            default -> "block.rematter." + expected.name().toLowerCase(java.util.Locale.ROOT);
        };
    }
    /** Infers a completion blueprint when an unformed controller is placed into a half-to-3/4 built chamber. */
    public static Optional<IndustrialBlueprint> inferPartialBlueprint(MachineBlockEntity machine) {
        if (!live(machine)) return Optional.empty();
        var level = (ServerLevel) machine.getLevel();
        var facing = machine.getBlockState().getValue(MachineBlock.FACING);
        var origin = machine.getBlockPos();
        Materials blocks = (x, y, depth) -> {
            var pos = worldPosition(origin, facing, new Cell(x, y, depth, Role.INTERIOR, null));
            if (!pos.equals(origin)) {
                for (var other : controllersUsing(level, pos)) {
                    if (other != machine) return Material.OTHER;
                }
            }
            return material(level, machine, pos);
        };
        return IndustrialBlueprint.inferPartial(machine.kind(), blocks);
    }
    public static List<IndustrialBlueprint.PartRequirement> blueprintBom(MachineBlockEntity machine, IndustrialBlueprint blueprint) {
        if (!live(machine)) return blueprint.billOfMaterials(List.of());
        var level = (ServerLevel) machine.getLevel();
        boolean lifting = IndustrialAutoBuilder.shouldLiftFromGround(level, machine);
        BlockPos buildOrigin = lifting ? machine.getBlockPos().above() : machine.getBlockPos();
        Materials blocks = IndustrialAutoBuilder.plannedMaterials(level, machine, buildOrigin, lifting);
        return blueprint.billOfMaterials(blueprint.collectAllMismatches(blocks));
    }
    public static dev.latrox.rematter.network.StructureHintPayload hintFor(int containerId, MachineBlockEntity machine) {
        if (!live(machine)) return dev.latrox.rematter.network.StructureHintPayload.EMPTY;
        boolean showOverlay = machine.structurePreviewEnabled();
        var level = (ServerLevel) machine.getLevel();
        boolean lifting = IndustrialAutoBuilder.shouldLiftFromGround(level, machine);
        boolean hasPartial = !machine.hasCustomBlueprint() && !lifting && !inspect(machine).formed()
                && inferPartialBlueprint(machine).isPresent();
        if (machine.hasCustomBlueprint() || lifting || hasPartial) {
            var blueprint = machine.industrialBlueprint();
            var facing = machine.getBlockState().getValue(MachineBlock.FACING);
            BlockPos buildOrigin = lifting ? machine.getBlockPos().above() : machine.getBlockPos();
            Materials blocks = IndustrialAutoBuilder.plannedMaterials(level, machine, buildOrigin, lifting);
            var local = blueprint.collectMismatches(blocks);
            if (local.isEmpty()) {
                return new dev.latrox.rematter.network.StructureHintPayload(
                        containerId, BlockPos.ZERO, "", "", List.of(), false, BlockPos.ZERO, BlockPos.ZERO, false);
            }
            var entries = new ArrayList<dev.latrox.rematter.network.StructureHintPayload.Entry>(local.size());
            for (var entry : local) {
                entries.add(new dev.latrox.rematter.network.StructureHintPayload.Entry(
                        worldPosition(buildOrigin, facing, entry.cell()),
                        expectedBlockKey(machine.kind(), entry.expected()),
                        entry.orientation()));
            }
            var primary = entries.getFirst();
            var box = bounds(buildOrigin, facing, blueprint.shape());
            return new dev.latrox.rematter.network.StructureHintPayload(
                    containerId, primary.position(), primary.expected(), primary.orientation(), entries,
                    true, new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ), showOverlay);
        }
        return dev.latrox.rematter.network.StructureHintPayload.fromFormation(containerId, inspect(machine), showOverlay);
    }
    private static Formation scan(ServerLevel level,MachineBlockEntity machine) {
        var facing=machine.getBlockState().getValue(MachineBlock.FACING);
        Materials blocks=(x,y,depth)->material(level,machine,worldPosition(machine.getBlockPos(),facing,new Cell(x,y,depth,Role.INTERIOR,null)));
        var shape=detect(machine.kind(),blocks).orElse(null);
        if(shape==null) {
            var claim=machine.industrialProtectionClaim();
            int minSide = minFootprint(machine.kind());
            shape=claim!=null&&claim.facing()==facing?claim.shape()
                    :machine.kind().isEnergyMatrix()?IndustrialBlueprint.defaultFor(machine.kind()).shape()
                    :new Shape(minSide,height(machine.kind()),machine.kind()==MachineKind.INDUSTRIAL_PREPARATOR?4:minSide,minSide/2);
        }
        var inspection=inspectLayout(machine.kind(),shape,blocks);
        if(!inspection.formed()) {
            var failure=Formation.invalid(shape,inspection.diagnostic);
            int minSide = minFootprint(machine.kind());
            Shape target=shape.height<height(machine.kind())||shape.width<minSide||shape.depth<minSide
                    ?new Shape(Math.max(shape.width,minSide),Math.max(shape.height,height(machine.kind())),Math.max(shape.depth,minSide),
                            Math.clamp(shape.controllerColumn,1,Math.max(shape.width,minSide)-2)):shape;
            var box=bounds(machine.getBlockPos(),facing,target);
            failure.minBounds=new BlockPos(box.minX,box.minY,box.minZ);
            failure.maxBounds=new BlockPos(box.maxX,box.maxY,box.maxZ);
            var local=collectMismatches(machine.kind(),shape,blocks);
            var worldList=new ArrayList<Mismatch>(local.size());
            for(var entry:local) {
                worldList.add(new Mismatch(worldPosition(machine.getBlockPos(),facing,entry.cell()),
                        expectedBlockKey(machine.kind(),entry.expected()),entry.orientation()));
            }
            failure.mismatches=List.copyOf(worldList);
            if(!worldList.isEmpty())failure.mismatch=worldList.getFirst();
            return failure;
        }
        var ports=new ArrayList<Port>();
        var requiredPort = ModContent.machinePort(machine.kind());
        for(var cell:layout(shape))if(cell.role==Role.WALL){
            var pos=worldPosition(machine.getBlockPos(),facing,cell);
            if(level.getBlockState(pos).is(requiredPort))ports.add(new Port(pos,worldDirection(facing,cell.outward)));
        }
        var modules=new ArrayList<Module>();
        for(var module:inspection.modules){
            if(module.material.isReactorModule()) {
                var pos=worldPosition(machine.getBlockPos(),facing,new Cell(module.x,module.y,module.depth,Role.INTERIOR,null));
                modules.add(new Module(pos,IndustrialModuleKind.valueOf(module.material.name()),module.x,module.y,module.depth));
            }
        }
        return new Formation(true,shape,ports,modules,inspection.modules,"ready");
    }
    static Material materialAt(ServerLevel level, MachineBlockEntity machine, BlockPos pos) {
        return material(level, machine, pos);
    }
    private static Material material(ServerLevel level,MachineBlockEntity machine,BlockPos pos){
        if(!level.hasChunkAt(pos))return Material.UNLOADED;
        var state=level.getBlockState(pos);
        if(pos.equals(machine.getBlockPos()))return level.getBlockEntity(pos)==machine?Material.CONTROLLER:Material.OTHER;
        if(state.isAir())return Material.AIR;
        var kind = machine.kind();
        if(state.is(ModContent.reactorCasing(kind)))return Material.CASING;
        if(state.is(ModContent.reactorGlass(kind)))return Material.GLASS;
        if(state.is(ModContent.machinePort(kind))&&level.getBlockEntity(pos) instanceof MachinePortBlockEntity)return Material.PORT;
        if(state.getBlock() instanceof IndustrialModuleBlock module && level.getBlockEntity(pos) instanceof IndustrialModuleBlockEntity)
            return Material.valueOf(module.kind().name());
        if(state.getBlock() instanceof dev.latrox.rematter.energy.EnergyMatrixCellBlock cell) {
            return switch (cell.tier()) {
                case BASIC -> Material.ENERGY_MATRIX_CELL;
                case REINFORCED -> Material.REINFORCED_ENERGY_MATRIX_CELL;
                case RESONANT -> Material.RESONANT_ENERGY_MATRIX_CELL;
                case PRIMAL -> Material.PRIMAL_ENERGY_MATRIX_CELL;
            };
        }
        if(state.getBlock() instanceof dev.latrox.rematter.energy.EnergyMatrixProviderBlock provider) {
            return switch (provider.tier()) {
                case BASIC -> Material.ENERGY_MATRIX_PROVIDER;
                case REINFORCED -> Material.REINFORCED_ENERGY_MATRIX_PROVIDER;
                case RESONANT -> Material.RESONANT_ENERGY_MATRIX_PROVIDER;
                case PRIMAL -> Material.PRIMAL_ENERGY_MATRIX_PROVIDER;
            };
        }
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
            Formation result=basic;
            if(!keepsPorts(self,others)) {
                result=Formation.invalid(basic.shape,"structure_ambiguous");
                var box=bounds(machine,basic.shape);
                result.minBounds=new BlockPos(box.minX,box.minY,box.minZ);
                result.maxBounds=new BlockPos(box.maxX,box.maxY,box.maxZ);
                var disputed=sharedPorts(self,others).stream().limit(MAX_HINTS)
                        .map(p->new Mismatch(p,"block.minecraft.air","")).toList();
                result.mismatches=disputed;
                if(!disputed.isEmpty())result.mismatch=disputed.getFirst();
            } else if(!clearOfForeignClaims(self,shell,others)) {
                result=Formation.invalid(basic.shape,"structure_claimed");
                var box=bounds(machine,basic.shape);
                result.minBounds=new BlockPos(box.minX,box.minY,box.minZ);
                result.maxBounds=new BlockPos(box.maxX,box.maxY,box.maxZ);
            }
            if(!observed.matches(level))return Formation.EMPTY;
            seen.settled=new Settled(basic,observed,self,List.copyOf(neighbors),result);
        }
        seen.settledTick=tick;seen.settledEpoch=epoch;
        return seen.settled.result;
    }
    private static List<BlockPos> sharedPorts(Claimant self, List<Claimant> others) {
        var result = new ArrayList<BlockPos>();
        for (var port : self.ports()) {
            for (var other : others) {
                boolean conflict = Objects.equals(self.owner(), other.owner())
                        ? other.ports().contains(port)
                        : other.claims(port) || other.ports().contains(port) && !self.claims(port);
                if (conflict) { result.add(port); break; }
            }
        }
        result.sort(Comparator.comparingLong(BlockPos::asLong));
        return result;
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
