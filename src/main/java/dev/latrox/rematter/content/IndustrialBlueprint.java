package dev.latrox.rematter.content;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * Player-configurable chamber blueprint for Industrial Reactor and Industrial Preparator controllers.
 * Calculates resulting chamber effects, exact required parts (BOM), and world preview mismatches.
 */
public record IndustrialBlueprint(
        MachineKind kind,
        int width,
        int height,
        int depth,
        int controllerColumn,
        int ports,
        int chambers,
        int coils,
        int grinding,
        int mixing,
        int separation,
        ShellStyle shellStyle,
        Map<Long, IndustrialStructure.Material> exactLayout) {

    public static final int MAX_CONFIGURED_PORTS = 16;

    public enum ShellStyle {
        SIDES_GLASS("sides_glass"),
        ALL_GLASS("all_glass"),
        FULL_CASING("full_casing");

        private final String id;

        ShellStyle(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public ShellStyle step(int delta) {
            var values = values();
            int idx = Math.floorMod(ordinal() + (delta >= 0 ? 1 : -1), values.length);
            return values[idx];
        }

        public static ShellStyle load(String raw) {
            if (raw != null) {
                for (var style : values()) {
                    if (style.id.equals(raw) || style.name().equalsIgnoreCase(raw)) {
                        return style;
                    }
                }
            }
            return SIDES_GLASS;
        }
    }

    public enum Parameter {
        WIDTH,
        DEPTH,
        HEIGHT,
        PORTS,
        CHAMBERS,
        COILS,
        GRINDING,
        MIXING,
        SEPARATION,
        SHELL_STYLE
    }

    public record PartRequirement(IndustrialStructure.Material material, String blockKey, int required, int missing) {}

    public IndustrialBlueprint(
            MachineKind kind,
            int width,
            int height,
            int depth,
            int controllerColumn,
            int ports,
            int chambers,
            int coils,
            int grinding,
            int mixing,
            int separation) {
        this(kind, width, height, depth, controllerColumn, ports, chambers, coils, grinding, mixing, separation, ShellStyle.SIDES_GLASS, Map.of());
    }

    public IndustrialBlueprint(
            MachineKind kind,
            int width,
            int height,
            int depth,
            int controllerColumn,
            int ports,
            int chambers,
            int coils,
            int grinding,
            int mixing,
            int separation,
            ShellStyle shellStyle) {
        this(kind, width, height, depth, controllerColumn, ports, chambers, coils, grinding, mixing, separation, shellStyle, Map.of());
    }

    public IndustrialBlueprint {
        Objects.requireNonNull(kind);
        if (!IndustrialStructure.isIndustrial(kind)) {
            throw new IllegalArgumentException("Not an industrial controller: " + kind);
        }
        shellStyle = shellStyle == null ? ShellStyle.SIDES_GLASS : shellStyle;
        exactLayout = exactLayout == null || exactLayout.isEmpty() ? Map.of() : Map.copyOf(exactLayout);
        int minH = IndustrialStructure.height(kind);
        int minSide = IndustrialStructure.minFootprint(kind);
        width = Math.clamp(width, minSide, IndustrialStructure.MAX_SIZE);
        height = Math.clamp(height, minH, IndustrialStructure.MAX_SIZE);
        depth = Math.clamp(depth, minSide, IndustrialStructure.MAX_SIZE);
        if (kind == MachineKind.INDUSTRIAL_PREPARATOR && mountCapacity(width, depth) < 3) {
            depth = Math.max(depth, 4);
        }
        if (kind.isEnergyMatrix() && matrixInteriorCapacity(width, height, depth) < 2) {
            depth = Math.max(depth, 4);
        }
        controllerColumn = Math.clamp(controllerColumn, 1, width - 2);
        ports = Math.clamp(ports, 2, Math.min(MAX_CONFIGURED_PORTS, maxHorizontalWallPorts(width, height, depth)));

        int maxSlots = kind.isEnergyMatrix() ? matrixInteriorCapacity(width, height, depth) : mountCapacity(width, depth);
        if (IndustrialStructure.usesReactorModules(kind) || kind.isEnergyMatrix()) {
            grinding = 0;
            mixing = 0;
            separation = 0;
            chambers = Math.clamp(chambers, 1, Math.max(1, maxSlots - 1));
            coils = Math.clamp(coils, 1, Math.max(1, maxSlots - chambers));
        } else {
            chambers = 0;
            coils = 0;
            grinding = Math.clamp(grinding, 1, Math.max(1, maxSlots - 2));
            mixing = Math.clamp(mixing, 1, Math.max(1, maxSlots - grinding - 1));
            separation = Math.clamp(separation, 1, Math.max(1, maxSlots - grinding - mixing));
        }
    }

    /**
     * Infers a completion blueprint from a partially built (roughly half to three-quarters built) chamber
     * connected directly to the controller at (0, 0, 0), without picking up disconnected neighboring chambers.
     */
    public static Optional<IndustrialBlueprint> inferPartial(MachineKind kind, IndustrialStructure.Materials blocks) {
        if (!IndustrialStructure.isIndustrial(kind)) return Optional.empty();
        int maxReach = IndustrialStructure.MAX_SIZE - 1; // 8
        var visited = new HashSet<Long>();
        var queue = new java.util.ArrayDeque<int[]>();
        long originKey = localCoordKey(0, 0, 0);
        visited.add(originKey);
        queue.add(new int[]{0, 0, 0});

        int minX = 0, maxX = 0, minY = 0, maxY = 0, maxZ = 0;
        int shellCount = 0;
        int portsCount = 0;
        int anyGlass = 0;
        int sideWallCasing = 0;
        var foundModules = new EnumMap<IndustrialStructure.Material, Integer>(IndustrialStructure.Material.class);
        var foundCoords = new ArrayList<int[]>();
        var foundMats = new ArrayList<IndustrialStructure.Material>();

        final int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.removeFirst();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0];
                int ny = cur[1] + d[1];
                int nz = cur[2] + d[2];
                if (nx < -maxReach || nx > maxReach || ny < -1 || ny > maxReach - 1 || nz < 0 || nz > maxReach) continue;
                long key = localCoordKey(nx, ny, nz);
                if (!visited.add(key)) continue;
                var mat = blocks.at(nx, ny, nz);
                if (!isRecognizedPart(kind, mat)) continue;

                int nextMinX = Math.min(minX, nx);
                int nextMaxX = Math.max(maxX, nx);
                int nextMinY = Math.min(minY, ny);
                int nextMaxY = Math.max(maxY, ny);
                int nextMaxZ = Math.max(maxZ, nz);
                if (nextMaxX - nextMinX + 1 > IndustrialStructure.MAX_SIZE
                        || nextMaxY - Math.min(-1, nextMinY) + 1 > IndustrialStructure.MAX_SIZE
                        || nextMaxZ + 1 > IndustrialStructure.MAX_SIZE) {
                    continue;
                }
                minX = nextMinX;
                maxX = nextMaxX;
                minY = nextMinY;
                maxY = nextMaxY;
                maxZ = nextMaxZ;
                foundCoords.add(new int[]{nx, ny, nz});
                foundMats.add(mat);
                if (mat.module()) {
                    if (kind.isEnergyMatrix()) {
                        foundModules.merge(mat.isMatrixCell()
                                ? IndustrialStructure.Material.ENERGY_MATRIX_CELL
                                : IndustrialStructure.Material.ENERGY_MATRIX_PROVIDER, 1, Integer::sum);
                    } else {
                        foundModules.merge(mat, 1, Integer::sum);
                    }
                } else {
                    shellCount++;
                    if (mat == IndustrialStructure.Material.PORT) portsCount++;
                    else if (mat == IndustrialStructure.Material.GLASS) anyGlass++;
                }
                queue.add(new int[]{nx, ny, nz});
            }
        }

        // Require a genuine partial structure spanning width & depth around the controller (not just 1-3 stray blocks).
        if (shellCount < 8 || minX > -1 || maxX < 1 || maxZ < 2) return Optional.empty();

        int minFoot = IndustrialStructure.minFootprint(kind);
        int width = Math.max(minFoot, maxX - minX + 1);
        int depth = Math.max(minFoot, maxZ + 1);
        int height = Math.max(IndustrialStructure.height(kind), maxY + 2);
        if (width > IndustrialStructure.MAX_SIZE || depth > IndustrialStructure.MAX_SIZE || height > IndustrialStructure.MAX_SIZE) {
            return Optional.empty();
        }
        int controllerColumn = -minX;
        if (controllerColumn < 1 || controllerColumn > width - 2) return Optional.empty();

        // Sanity-check: almost all connected parts must lie cleanly on shell/interior positions of the inferred box.
        int left = -controllerColumn;
        int right = left + width - 1;
        int roofY = height - 2;
        int rearZ = depth - 1;
        int validFit = 0;
        boolean verticalGlass = false;
        for (int i = 0; i < foundCoords.size(); i++) {
            int[] c = foundCoords.get(i);
            var mat = foundMats.get(i);
            boolean bx = c[0] == left || c[0] == right;
            boolean by = c[1] == -1 || c[1] == roofY;
            boolean bz = c[2] == 0 || c[2] == rearZ;
            int boundaries = (bx ? 1 : 0) + (by ? 1 : 0) + (bz ? 1 : 0);
            if (mat.module()) {
                if (boundaries == 0) validFit++;
            } else if (boundaries >= 2) {
                if (mat == IndustrialStructure.Material.CASING) validFit++;
            } else if (boundaries == 1) {
                validFit++;
                if (by && mat == IndustrialStructure.Material.GLASS) verticalGlass = true;
                if (!by && mat == IndustrialStructure.Material.CASING) sideWallCasing++;
            }
        }
        if (validFit * 100 < foundCoords.size() * 85) return Optional.empty();

        var def = defaultFor(kind);
        int plannedPorts = Math.clamp(Math.max(def.ports(), portsCount), 2, Math.min(MAX_CONFIGURED_PORTS, maxHorizontalWallPorts(width, height, depth)));
        ShellStyle style = verticalGlass ? ShellStyle.ALL_GLASS
                : anyGlass > 0 ? ShellStyle.SIDES_GLASS
                : sideWallCasing >= 4 ? ShellStyle.FULL_CASING
                : ShellStyle.SIDES_GLASS;

        if (IndustrialStructure.usesReactorModules(kind) || kind.isEnergyMatrix()) {
            var primary = IndustrialStructure.primaryReactorModule(kind);
            var secondary = IndustrialStructure.secondaryReactorModule(kind);
            int ch = Math.max(def.chambers(), foundModules.getOrDefault(primary, 0));
            int co = Math.max(def.coils(), foundModules.getOrDefault(secondary, 0));
            return Optional.of(new IndustrialBlueprint(kind, width, height, depth, controllerColumn, plannedPorts, ch, co, 0, 0, 0, style));
        }
        int gr = Math.max(def.grinding(), foundModules.getOrDefault(IndustrialStructure.Material.GRINDING_UNIT, 0));
        int mx = Math.max(def.mixing(), foundModules.getOrDefault(IndustrialStructure.Material.MIXING_BASIN, 0));
        int sp = Math.max(def.separation(), foundModules.getOrDefault(IndustrialStructure.Material.SEPARATION_UNIT, 0));
        return Optional.of(new IndustrialBlueprint(kind, width, height, depth, controllerColumn, plannedPorts, 0, 0, gr, mx, sp, style));
    }

    private static boolean isRecognizedPart(MachineKind kind, IndustrialStructure.Material mat) {
        if (mat == IndustrialStructure.Material.CASING || mat == IndustrialStructure.Material.GLASS || mat == IndustrialStructure.Material.PORT) {
            return true;
        }
        if (kind.isEnergyMatrix()) return mat.isMatrixPart();
        return mat.module() && IndustrialStructure.requiredModules(kind).contains(mat);
    }

    static long localCoordKey(int x, int y, int depth) {
        return ((long) (x + 16) << 16) | ((long) (y + 16) << 8) | (depth + 16);
    }

    public static IndustrialBlueprint defaultFor(MachineKind kind) {
        if (kind == MachineKind.PRIMAL_REACTOR || kind == MachineKind.ENERGY_MATRIX || kind == MachineKind.ENERGY_REACTOR) {
            return new IndustrialBlueprint(kind, 5, 5, 5, 2, 2, 2, 2, 0, 0, 0);
        }
        if (kind == MachineKind.TRANSMUTATION_REACTOR) {
            return new IndustrialBlueprint(kind, 5, 5, 5, 2, 2, 2, 2, 0, 0, 0);
        }
        if (IndustrialStructure.usesReactorModules(kind)) {
            return new IndustrialBlueprint(kind, 3, 4, 3, 1, 2, 1, 1, 0, 0, 0);
        }
        if (kind == MachineKind.INDUSTRIAL_PREPARATOR) {
            return new IndustrialBlueprint(kind, 3, 5, 4, 1, 2, 0, 0, 1, 1, 1);
        }
        throw new IllegalArgumentException("Not an industrial controller: " + kind);
    }

    public static IndustrialBlueprint fromFormation(MachineKind kind, IndustrialStructure.Formation formation) {
        var shape = formation.shape();
        if (!formation.formed() || shape == null) {
            return defaultFor(kind);
        }
        var perf = formation.performance();
        int portCount = Math.max(2, formation.ports().size());
        if (IndustrialStructure.usesReactorModules(kind) || kind.isEnergyMatrix()) {
            int primary = switch (kind) {
                case PRIMAL_REACTOR -> perf.condensers();
                case TRANSMUTATION_REACTOR -> perf.emitters();
                case ENERGY_REACTOR -> perf.fuelCores();
                case ENERGY_MATRIX -> perf.matrixCells();
                default -> perf.chambers();
            };
            int secondary = switch (kind) {
                case PRIMAL_REACTOR -> perf.emitters();
                case ENERGY_REACTOR -> perf.inductionCoils();
                case ENERGY_MATRIX -> perf.matrixProviders();
                default -> perf.coils();
            };
            return new IndustrialBlueprint(
                    kind, shape.width(), shape.height(), shape.depth(), shape.controllerColumn(),
                    portCount, Math.max(1, primary), Math.max(1, secondary), 0, 0, 0);
        }
        return new IndustrialBlueprint(
                kind, shape.width(), shape.height(), shape.depth(), shape.controllerColumn(),
                portCount, 0, 0, Math.max(1, perf.grinding()), Math.max(1, perf.mixing()), Math.max(1, perf.separation()));
    }

    public static int defaultColumn(int width) {
        return Math.clamp(width / 2, 1, Math.max(1, width - 2));
    }

    public static int mountCapacity(int width, int depth) {
        return 2 * Math.max(1, width - 2) * Math.max(1, depth - 2);
    }

    public static int matrixInteriorCapacity(int width, int height, int depth) {
        return Math.max(1, width - 2) * Math.max(1, height - 2) * Math.max(1, depth - 2);
    }

    public static int maxHorizontalWallPorts(int width, int height, int depth) {
        int innerH = Math.max(1, height - 2);
        int innerW = Math.max(1, width - 2);
        int innerD = Math.max(1, depth - 2);
        return Math.max(2, 2 * innerH * (innerW + innerD) - 1);
    }

    public IndustrialStructure.Shape shape() {
        return new IndustrialStructure.Shape(width, height, depth, controllerColumn);
    }

    public int slotCapacity() {
        return kind.isEnergyMatrix() ? matrixInteriorCapacity(width, height, depth) : mountCapacity(width, depth);
    }

    public int totalModules() {
        return (IndustrialStructure.usesReactorModules(kind) || kind.isEnergyMatrix()) ? chambers + coils : grinding + mixing + separation;
    }

    public IndustrialPerformance performance() {
        if (kind == MachineKind.TRANSMUTATION_REACTOR) {
            return new IndustrialPerformance(0, 0, 0, coils, 0, chambers, 0);
        }
        if (kind == MachineKind.PRIMAL_REACTOR) {
            return new IndustrialPerformance(0, 0, 0, 0, 0, coils, chambers);
        }
        if (kind == MachineKind.ENERGY_REACTOR) {
            return IndustrialPerformance.energyReactor(chambers, coils);
        }
        if (kind == MachineKind.ENERGY_MATRIX) {
            return IndustrialPerformance.energyMatrixBasic(chambers, coils);
        }
        return new IndustrialPerformance(grinding, mixing, separation, coils, chambers);
    }

    public int workRate() {
        return performance().workRate(kind);
    }

    public int laneRoom() {
        return IndustrialStructure.laneRoom(kind, height);
    }

    public IndustrialBlueprint withShellStyle(ShellStyle next) {
        return new IndustrialBlueprint(kind, width, height, depth, controllerColumn, ports, chambers, coils, grinding, mixing, separation, next, Map.of());
    }

    public IndustrialBlueprint withExactLayout(Map<Long, IndustrialStructure.Material> layout) {
        if (layout == null || layout.isEmpty()) {
            return exactLayout.isEmpty() ? this
                    : new IndustrialBlueprint(kind, width, height, depth, controllerColumn, ports, chambers, coils, grinding, mixing, separation, shellStyle, Map.of());
        }
        var sanitized = new LinkedHashMap<Long, IndustrialStructure.Material>();
        var target = shape();
        for (var cell : IndustrialStructure.layout(target)) {
            if (cell.role() == IndustrialStructure.Role.CONTROLLER) continue;
            long key = localCoordKey(cell.x(), cell.y(), cell.depth());
            var mat = layout.get(key);
            if (mat != null && mat != IndustrialStructure.Material.AIR
                    && mat != IndustrialStructure.Material.CONTROLLER
                    && mat != IndustrialStructure.Material.OTHER
                    && mat != IndustrialStructure.Material.UNLOADED) {
                sanitized.put(key, mat);
            }
        }
        return new IndustrialBlueprint(kind, width, height, depth, controllerColumn, ports, chambers, coils, grinding, mixing, separation, shellStyle, sanitized);
    }

    /** Adjusts one parameter by delta, auto-growing chamber footprint when adding modules and trimming cleanly when shrinking. */
    public IndustrialBlueprint adjust(Parameter parameter, int delta) {
        if (delta == 0) return this;
        int w = width;
        int h = height;
        int d = depth;
        int p = ports;
        int ch = chambers;
        int co = coils;
        int gr = grinding;
        int mx = mixing;
        int sp = separation;

        switch (parameter) {
            case WIDTH -> {
                w = Math.clamp(w + delta, 3, IndustrialStructure.MAX_SIZE);
                if (kind == MachineKind.INDUSTRIAL_PREPARATOR && mountCapacity(w, d) < 3) {
                    if (delta < 0 && d < 4) d = 4;
                    else w = Math.max(w, 4);
                }
                return trimModulesToFit(w, h, d, defaultColumn(w), p, ch, co, gr, mx, sp);
            }
            case DEPTH -> {
                d = Math.clamp(d + delta, 3, IndustrialStructure.MAX_SIZE);
                if (kind == MachineKind.INDUSTRIAL_PREPARATOR && mountCapacity(w, d) < 3) {
                    if (delta < 0 && w < 4) w = 4;
                    else d = Math.max(d, 4);
                }
                return trimModulesToFit(w, h, d, controllerColumn, p, ch, co, gr, mx, sp);
            }
            case HEIGHT -> {
                h = Math.clamp(h + delta, IndustrialStructure.height(kind), IndustrialStructure.MAX_SIZE);
                return kind.isEnergyMatrix()
                        ? trimModulesToFit(w, h, d, controllerColumn, p, ch, co, gr, mx, sp)
                        : new IndustrialBlueprint(kind, w, h, d, controllerColumn, p, ch, co, gr, mx, sp, shellStyle);
            }
            case PORTS -> {
                p = Math.clamp(p + delta, 2, Math.min(MAX_CONFIGURED_PORTS, maxHorizontalWallPorts(w, h, d)));
                return new IndustrialBlueprint(kind, w, h, d, controllerColumn, p, ch, co, gr, mx, sp, shellStyle);
            }
            case CHAMBERS -> {
                if (!IndustrialStructure.usesReactorModules(kind) && !kind.isEnergyMatrix()) return this;
                int maxTotal = kind.isEnergyMatrix()
                        ? matrixInteriorCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE)
                        : mountCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE);
                ch = Math.clamp(ch + delta, 1, maxTotal - Math.max(1, co));
                return growFootprintToFit(w, h, d, p, ch, co, 0, 0, 0);
            }
            case COILS -> {
                if (!IndustrialStructure.usesReactorModules(kind) && !kind.isEnergyMatrix()) return this;
                int maxTotal = kind.isEnergyMatrix()
                        ? matrixInteriorCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE)
                        : mountCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE);
                co = Math.clamp(co + delta, 1, maxTotal - Math.max(1, ch));
                return growFootprintToFit(w, h, d, p, ch, co, 0, 0, 0);
            }
            case GRINDING -> {
                if (kind != MachineKind.INDUSTRIAL_PREPARATOR) return this;
                int maxTotal = mountCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE);
                gr = Math.clamp(gr + delta, 1, maxTotal - Math.max(1, mx) - Math.max(1, sp));
                return growFootprintToFit(w, h, d, p, 0, 0, gr, mx, sp);
            }
            case MIXING -> {
                if (kind != MachineKind.INDUSTRIAL_PREPARATOR) return this;
                int maxTotal = mountCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE);
                mx = Math.clamp(mx + delta, 1, maxTotal - Math.max(1, gr) - Math.max(1, sp));
                return growFootprintToFit(w, h, d, p, 0, 0, gr, mx, sp);
            }
            case SEPARATION -> {
                if (kind != MachineKind.INDUSTRIAL_PREPARATOR) return this;
                int maxTotal = mountCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE);
                sp = Math.clamp(sp + delta, 1, maxTotal - Math.max(1, gr) - Math.max(1, mx));
                return growFootprintToFit(w, h, d, p, 0, 0, gr, mx, sp);
            }
            case SHELL_STYLE -> {
                return withShellStyle(shellStyle.step(delta));
            }
        }
        return this;
    }

    private IndustrialBlueprint growFootprintToFit(int w, int h, int d, int p, int ch, int co, int gr, int mx, int sp) {
        int needed = (IndustrialStructure.usesReactorModules(kind) || kind.isEnergyMatrix()) ? ch + co : gr + mx + sp;
        boolean colWasCentered = controllerColumn == defaultColumn(w);
        if (kind.isEnergyMatrix()) {
            while (matrixInteriorCapacity(w, h, d) < needed
                    && (w < IndustrialStructure.MAX_SIZE || d < IndustrialStructure.MAX_SIZE || h < IndustrialStructure.MAX_SIZE)) {
                if (d <= w && d <= h && d < IndustrialStructure.MAX_SIZE) d++;
                else if (w <= h && w < IndustrialStructure.MAX_SIZE) w++;
                else if (h < IndustrialStructure.MAX_SIZE) h++;
                else if (w < IndustrialStructure.MAX_SIZE) w++;
                else d++;
            }
        } else {
            while (mountCapacity(w, d) < needed && (w < IndustrialStructure.MAX_SIZE || d < IndustrialStructure.MAX_SIZE)) {
                if (d <= w && d < IndustrialStructure.MAX_SIZE) {
                    d++;
                } else if (w < IndustrialStructure.MAX_SIZE) {
                    w++;
                } else {
                    d++;
                }
            }
        }
        int col = colWasCentered ? defaultColumn(w) : Math.clamp(controllerColumn, 1, w - 2);
        return new IndustrialBlueprint(kind, w, h, d, col, p, ch, co, gr, mx, sp, shellStyle);
    }

    private IndustrialBlueprint trimModulesToFit(int w, int h, int d, int col, int p, int ch, int co, int gr, int mx, int sp) {
        int cap = kind.isEnergyMatrix() ? matrixInteriorCapacity(w, h, d) : mountCapacity(w, d);
        if (IndustrialStructure.usesReactorModules(kind) || kind.isEnergyMatrix()) {
            while (ch + co > cap && (ch > 1 || co > 1)) {
                if (ch >= co && ch > 1) ch--;
                else if (co > 1) co--;
            }
        } else {
            while (gr + mx + sp > cap && (gr > 1 || mx > 1 || sp > 1)) {
                if (gr >= mx && gr >= sp && gr > 1) gr--;
                else if (mx >= sp && mx > 1) mx--;
                else if (sp > 1) sp--;
            }
        }
        return new IndustrialBlueprint(kind, w, h, d, col, p, ch, co, gr, mx, sp, shellStyle);
    }

    private static long xzKey(int x, int depth) {
        return ((long) (x + 16) << 8) | (depth + 16);
    }

    /** Exact bill of materials required to build this configured chamber (including 1 controller). */
    public Map<IndustrialStructure.Material, Integer> requiredCounts() {
        var counts = new LinkedHashMap<IndustrialStructure.Material, Integer>();
        counts.put(IndustrialStructure.Material.CONTROLLER, 1);
        var byMat = new EnumMap<IndustrialStructure.Material, Integer>(IndustrialStructure.Material.class);
        for (var m : collectAllMismatches((x, y, depth) -> x == 0 && y == 0 && depth == 0
                ? IndustrialStructure.Material.CONTROLLER : IndustrialStructure.Material.AIR)) {
            if (m.expected() != IndustrialStructure.Material.AIR) {
                byMat.merge(m.expected(), 1, Integer::sum);
            }
        }
        for (var key : List.of(
                IndustrialStructure.Material.CASING,
                IndustrialStructure.Material.GLASS,
                IndustrialStructure.Material.PORT)) {
            int n = byMat.getOrDefault(key, 0);
            if (n > 0) counts.put(key, n);
        }
        if (!exactLayout.isEmpty()) {
            for (var entry : byMat.entrySet()) {
                var mat = entry.getKey();
                if (mat.module() && entry.getValue() > 0) {
                    counts.put(mat, entry.getValue());
                }
            }
            return Map.copyOf(counts);
        }
        if (IndustrialStructure.usesReactorModules(kind) || kind.isEnergyMatrix()) {
            counts.put(IndustrialStructure.primaryReactorModule(kind), chambers);
            counts.put(IndustrialStructure.secondaryReactorModule(kind), coils);
        } else {
            counts.put(IndustrialStructure.Material.GRINDING_UNIT, grinding);
            counts.put(IndustrialStructure.Material.MIXING_BASIN, mixing);
            counts.put(IndustrialStructure.Material.SEPARATION_UNIT, separation);
        }
        return Map.copyOf(counts);
    }

    public List<PartRequirement> billOfMaterials(List<IndustrialStructure.LocalMismatch> mismatches) {
        var missingByMat = new EnumMap<IndustrialStructure.Material, Integer>(IndustrialStructure.Material.class);
        for (var mismatch : mismatches) {
            if (mismatch.expected() != IndustrialStructure.Material.AIR) {
                missingByMat.merge(mismatch.expected(), 1, Integer::sum);
            }
        }
        var out = new ArrayList<PartRequirement>();
        for (var entry : requiredCounts().entrySet()) {
            var mat = entry.getKey();
            int req = entry.getValue();
            int miss = Math.min(req, missingByMat.getOrDefault(mat, 0));
            out.add(new PartRequirement(mat, IndustrialStructure.expectedBlockKey(kind, mat), req, miss));
        }
        return List.copyOf(out);
    }

    /** Computes up to {@link IndustrialStructure#MAX_HINTS} missing or invalid blocks for client preview hints. */
    public List<IndustrialStructure.LocalMismatch> collectMismatches(IndustrialStructure.Materials blocks) {
        var all = collectAllMismatches(blocks);
        return all.size() <= IndustrialStructure.MAX_HINTS
                ? all
                : List.copyOf(all.subList(0, IndustrialStructure.MAX_HINTS));
    }

    /** Computes all missing or invalid blocks across the entire configured chamber volume without hint-packet truncation. */
    List<IndustrialStructure.LocalMismatch> collectAllMismatches(IndustrialStructure.Materials blocks) {
        var target = shape();
        int crown = target.height() - 3;
        if (!exactLayout.isEmpty()) {
            var exactCollected = new ArrayList<IndustrialStructure.LocalMismatch>();
            for (var cell : IndustrialStructure.layout(target)) {
                var actual = blocks.at(cell.x(), cell.y(), cell.depth());
                if (actual == IndustrialStructure.Material.UNLOADED) {
                    return List.of(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.AIR, ""));
                }
                if (cell.role() == IndustrialStructure.Role.CONTROLLER) {
                    if (actual != IndustrialStructure.Material.CONTROLLER) {
                        exactCollected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.CONTROLLER, ""));
                    }
                    continue;
                }
                var expected = exactLayout.getOrDefault(localCoordKey(cell.x(), cell.y(), cell.depth()), IndustrialStructure.Material.AIR);
                if (actual != expected) {
                    String orient = expected.isReactorModule() ? (cell.y() == 0 ? "up" : cell.y() == crown ? "down" : "") : "";
                    exactCollected.add(new IndustrialStructure.LocalMismatch(cell, expected, orient));
                }
            }
            return List.copyOf(exactCollected);
        }
        var allowedModules = IndustrialStructure.requiredModules(kind);

        var collected = new ArrayList<IndustrialStructure.LocalMismatch>();
        var openHorizontalWalls = new ArrayList<IndustrialStructure.Cell>();
        var verticalWalls = new ArrayList<IndustrialStructure.Cell>();
        var openVerticalWalls = new ArrayList<IndustrialStructure.Cell>();
        var closedNonPortHorizontalWalls = new ArrayList<IndustrialStructure.Cell>();
        var freeFloor = new ArrayList<IndustrialStructure.Cell>();
        var freeCeiling = new ArrayList<IndustrialStructure.Cell>();
        var floorMounts = new HashSet<Long>();
        var roofMounts = new HashSet<Long>();
        var matrixCellCols = new HashSet<Long>();
        var matrixProviderCols = new HashSet<Long>();
        var placedModules = new EnumMap<IndustrialStructure.Material, Integer>(IndustrialStructure.Material.class);
        int placedPorts = 0;

        for (var cell : IndustrialStructure.layout(target)) {
            var mat = blocks.at(cell.x(), cell.y(), cell.depth());
            if (mat == IndustrialStructure.Material.UNLOADED) {
                return List.of(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.AIR, ""));
            }
            switch (cell.role()) {
                case CONTROLLER -> {
                    if (mat != IndustrialStructure.Material.CONTROLLER) {
                        collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.CONTROLLER, ""));
                    }
                }
                case FRAME -> {
                    if (mat != IndustrialStructure.Material.CASING) {
                        collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.CASING, ""));
                    }
                }
                case WALL -> {
                    if (cell.outward().getAxis().isVertical()) {
                        verticalWalls.add(cell);
                    } else if (mat == IndustrialStructure.Material.PORT) {
                        placedPorts++;
                    } else if (mat != IndustrialStructure.Material.CASING && mat != IndustrialStructure.Material.GLASS) {
                        openHorizontalWalls.add(cell);
                    } else {
                        closedNonPortHorizontalWalls.add(cell);
                    }
                }
                case INTERIOR -> {
                    if (kind.isEnergyMatrix()) {
                        if (mat.isMatrixCell()) {
                            placedModules.merge(IndustrialStructure.Material.ENERGY_MATRIX_CELL, 1, Integer::sum);
                            matrixCellCols.add(xzKey(cell.x(), cell.depth()));
                            if (cell.y() == 0) floorMounts.add(xzKey(cell.x(), cell.depth()));
                            if (cell.y() == crown) roofMounts.add(xzKey(cell.x(), cell.depth()));
                        } else if (mat.isMatrixProvider()) {
                            placedModules.merge(IndustrialStructure.Material.ENERGY_MATRIX_PROVIDER, 1, Integer::sum);
                            matrixProviderCols.add(xzKey(cell.x(), cell.depth()));
                            if (cell.y() == 0) floorMounts.add(xzKey(cell.x(), cell.depth()));
                            if (cell.y() == crown) roofMounts.add(xzKey(cell.x(), cell.depth()));
                        } else if (mat != IndustrialStructure.Material.AIR) {
                            collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.AIR, ""));
                        } else {
                            freeFloor.add(cell);
                        }
                    } else if (mat.module()) {
                        if (!allowedModules.contains(mat)) {
                            String orient = cell.y() == 0 ? "up" : cell.y() == crown ? "down" : "";
                            collected.add(new IndustrialStructure.LocalMismatch(cell, allowedModules.getFirst(), orient));
                        } else if (IndustrialModuleMount.atInteriorHeight(cell.y(), crown).isEmpty()) {
                            collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.AIR, ""));
                        } else {
                            placedModules.merge(mat, 1, Integer::sum);
                            if (cell.y() == 0) floorMounts.add(xzKey(cell.x(), cell.depth()));
                            if (cell.y() == crown) roofMounts.add(xzKey(cell.x(), cell.depth()));
                        }
                    } else if (mat != IndustrialStructure.Material.AIR) {
                        collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.AIR, ""));
                    } else if (cell.y() == 0) {
                        freeFloor.add(cell);
                    } else if (cell.y() == crown) {
                        freeCeiling.add(cell);
                    }
                }
            }
        }

        if (kind.isEnergyMatrix()) {
            sortMatrixCoreOutward(freeFloor, target);
        } else {
            sortSymmetrically(freeFloor, target);
            sortSymmetrically(freeCeiling, target);
        }

        var plannedModuleMismatches = new ArrayList<IndustrialStructure.LocalMismatch>();
        if (kind.isEnergyMatrix()) {
            int needCells = chambers - placedModules.getOrDefault(IndustrialStructure.Material.ENERGY_MATRIX_CELL, 0);
            int needProviders = coils - placedModules.getOrDefault(IndustrialStructure.Material.ENERGY_MATRIX_PROVIDER, 0);
            allocateMatrixParts(plannedModuleMismatches, IndustrialStructure.Material.ENERGY_MATRIX_CELL,
                    needCells, freeFloor, matrixCellCols, matrixProviderCols, crown, floorMounts, roofMounts);
            allocateMatrixParts(plannedModuleMismatches, IndustrialStructure.Material.ENERGY_MATRIX_PROVIDER,
                    needProviders, freeFloor, matrixProviderCols, matrixCellCols, crown, floorMounts, roofMounts);
        } else if (IndustrialStructure.usesReactorModules(kind)) {
            var primary = IndustrialStructure.primaryReactorModule(kind);
            var secondary = IndustrialStructure.secondaryReactorModule(kind);
            allocateModules(plannedModuleMismatches, primary,
                    chambers - placedModules.getOrDefault(primary, 0),
                    true, freeCeiling, freeFloor, crown, floorMounts, roofMounts);
            allocateModules(plannedModuleMismatches, secondary,
                    coils - placedModules.getOrDefault(secondary, 0),
                    false, freeCeiling, freeFloor, crown, floorMounts, roofMounts);
        } else {
            allocateModules(plannedModuleMismatches, IndustrialStructure.Material.GRINDING_UNIT,
                    grinding - placedModules.getOrDefault(IndustrialStructure.Material.GRINDING_UNIT, 0),
                    true, freeCeiling, freeFloor, crown, floorMounts, roofMounts);
            allocateModules(plannedModuleMismatches, IndustrialStructure.Material.MIXING_BASIN,
                    mixing - placedModules.getOrDefault(IndustrialStructure.Material.MIXING_BASIN, 0),
                    false, freeCeiling, freeFloor, crown, floorMounts, roofMounts);
            allocateModules(plannedModuleMismatches, IndustrialStructure.Material.SEPARATION_UNIT,
                    separation - placedModules.getOrDefault(IndustrialStructure.Material.SEPARATION_UNIT, 0),
                    false, freeCeiling, freeFloor, crown, floorMounts, roofMounts);
        }

        var horizontalWallFill = shellStyle == ShellStyle.FULL_CASING
                ? IndustrialStructure.Material.CASING
                : IndustrialStructure.Material.GLASS;
        var verticalWallFill = shellStyle == ShellStyle.ALL_GLASS
                ? IndustrialStructure.Material.GLASS
                : IndustrialStructure.Material.CASING;

        for (var cell : verticalWalls) {
            var mat = blocks.at(cell.x(), cell.y(), cell.depth());
            boolean needsMount = (cell.outward() == net.minecraft.core.Direction.DOWN ? floorMounts : roofMounts)
                    .contains(xzKey(cell.x(), cell.depth()));
            if (mat == IndustrialStructure.Material.PORT) {
                if (!IndustrialStructure.usesReactorModules(kind)) {
                    placedPorts++;
                } else {
                    collected.add(new IndustrialStructure.LocalMismatch(cell,
                            needsMount ? IndustrialStructure.Material.CASING : verticalWallFill, ""));
                }
            } else if (needsMount) {
                if (mat != IndustrialStructure.Material.CASING) {
                    collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.CASING, ""));
                }
            } else if (mat != IndustrialStructure.Material.CASING && mat != IndustrialStructure.Material.GLASS) {
                openVerticalWalls.add(cell);
            }
        }

        int portsNeeded = Math.max(0, ports - placedPorts);
        var chosenPorts = selectSymmetricPorts(openHorizontalWalls, target, portsNeeded);
        var chosenPortKeys = new HashSet<Long>(chosenPorts.size() * 2);
        for (var cell : chosenPorts) {
            collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.PORT, ""));
            chosenPortKeys.add(cellKey(cell));
            portsNeeded--;
        }
        for (var cell : openHorizontalWalls) {
            if (!chosenPortKeys.contains(cellKey(cell))) {
                collected.add(new IndustrialStructure.LocalMismatch(cell, horizontalWallFill, ""));
            }
        }
        for (var cell : openVerticalWalls) {
            collected.add(new IndustrialStructure.LocalMismatch(cell, verticalWallFill, ""));
        }
        if (portsNeeded > 0) {
            for (var cell : selectSymmetricPorts(closedNonPortHorizontalWalls, target, portsNeeded)) {
                collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.PORT, ""));
                portsNeeded--;
            }
        }
        collected.addAll(plannedModuleMismatches);

        var ordered = new ArrayList<IndustrialStructure.LocalMismatch>(collected.size());
        var seen = new HashSet<Long>();
        for (var entry : collected) {
            long key = cellKey(entry.cell());
            if (seen.add(key)) {
                ordered.add(entry);
            }
        }
        return List.copyOf(ordered);
    }

    private static long cellKey(IndustrialStructure.Cell cell) {
        return ((long) (cell.x() + 16) << 16) | ((long) (cell.y() + 16) << 8) | (cell.depth() + 16);
    }

    /**
     * Selects horizontal wall ports bottom-first (y = 0, then y = 1, ...) in symmetric opposite pairs,
     * keeping the front controller face clear until side and back walls on that row are used.
     */
    private static List<IndustrialStructure.Cell> selectSymmetricPorts(
            List<IndustrialStructure.Cell> candidates, IndustrialStructure.Shape shape, int needed) {
        if (needed <= 0 || candidates.isEmpty()) return List.of();
        int innerW = Math.max(1, shape.width() - 2);
        int innerD = Math.max(1, shape.depth() - 2);
        int minX = -shape.controllerColumn() + 1;
        int leftWallX = -shape.controllerColumn();
        int rightWallX = leftWallX + shape.width() - 1;
        int backZ = shape.depth() - 1;
        int maxY = shape.height() - 3;

        var byKey = new java.util.HashMap<Long, IndustrialStructure.Cell>(candidates.size() * 2);
        for (var cell : candidates) byKey.put(cellKey(cell), cell);

        var chosen = new ArrayList<IndustrialStructure.Cell>(Math.min(needed, candidates.size()));
        var used = new HashSet<Long>(needed * 2);

        for (int y = 0; y <= maxY && chosen.size() < needed; y++) {
            int remaining = needed - chosen.size();
            var pairs = new ArrayList<IndustrialStructure.Cell[]>();

            // 1. Center side pair (WEST <-> EAST) at the horizontal middle of this bottom-first row.
            int midZ = 1 + innerD / 2;
            addWallPair(pairs, byKey, used, leftWallX, y, midZ, rightWallX, y, midZ);

            // 2. Back wall symmetric pairs (SOUTH) moving outward from center.
            int centerIxLow = (innerW - 1) / 2;
            int centerIxHigh = innerW / 2;
            if (centerIxLow != centerIxHigh) {
                for (int step = 0; centerIxLow - step >= 0 && centerIxHigh + step < innerW; step++) {
                    addWallPair(pairs, byKey, used,
                            minX + centerIxLow - step, y, backZ,
                            minX + centerIxHigh + step, y, backZ);
                }
            } else {
                for (int step = 1; centerIxLow - step >= 0 && centerIxLow + step < innerW; step++) {
                    addWallPair(pairs, byKey, used,
                            minX + centerIxLow - step, y, backZ,
                            minX + centerIxLow + step, y, backZ);
                }
            }

            // 3. Remaining side wall opposite pairs (WEST <-> EAST), radiating outward from center (back-side before front-side).
            for (int step = 1; step <= innerD; step++) {
                int zBack = midZ + step;
                if (zBack >= 1 && zBack <= innerD) {
                    addWallPair(pairs, byKey, used, leftWallX, y, zBack, rightWallX, y, zBack);
                }
                int zFront = midZ - step;
                if (zFront >= 1 && zFront <= innerD) {
                    addWallPair(pairs, byKey, used, leftWallX, y, zFront, rightWallX, y, zFront);
                }
            }

            // 4. Front wall symmetric pairs (NORTH) last so the controller window stays clean unless needed.
            if (centerIxLow != centerIxHigh) {
                for (int step = 0; centerIxLow - step >= 0 && centerIxHigh + step < innerW; step++) {
                    addWallPair(pairs, byKey, used,
                            minX + centerIxLow - step, y, 0,
                            minX + centerIxHigh + step, y, 0);
                }
            } else {
                for (int step = 1; centerIxLow - step >= 0 && centerIxLow + step < innerW; step++) {
                    addWallPair(pairs, byKey, used,
                            minX + centerIxLow - step, y, 0,
                            minX + centerIxLow + step, y, 0);
                }
            }

            // Center singletons on odd-width back/front walls (used when remaining is odd, or when filling the row).
            var singles = new ArrayList<IndustrialStructure.Cell>(2);
            if ((innerW & 1) == 1) {
                int centerX = minX + innerW / 2;
                var backMid = byKey.get(cellKey(new IndustrialStructure.Cell(centerX, y, backZ, IndustrialStructure.Role.WALL, net.minecraft.core.Direction.SOUTH)));
                if (backMid != null && !used.contains(cellKey(backMid))) singles.add(backMid);
                var frontMid = byKey.get(cellKey(new IndustrialStructure.Cell(centerX, y, 0, IndustrialStructure.Role.WALL, net.minecraft.core.Direction.NORTH)));
                if (frontMid != null && !used.contains(cellKey(frontMid))) singles.add(frontMid);
            }

            // Take as many complete opposite pairs as fit while leaving room for a center singleton if remaining is odd.
            int pairBudget = Math.min(pairs.size(), remaining / 2);
            for (int i = 0; i < pairBudget; i++) {
                var pair = pairs.get(i);
                for (var c : pair) {
                    if (used.add(cellKey(c))) chosen.add(c);
                }
            }
            if (chosen.size() < needed && ((needed - chosen.size()) & 1) == 1 && !singles.isEmpty()) {
                var mid = singles.removeFirst();
                if (used.add(cellKey(mid))) chosen.add(mid);
            }
            // If we still need more ports on this row (e.g. filling all slots on y = 0 before going up to y = 1),
            // consume remaining pairs, singles, and any unpaired open wall cells on this row.
            for (int i = pairBudget; i < pairs.size() && chosen.size() < needed; i++) {
                for (var c : pairs.get(i)) {
                    if (chosen.size() < needed && used.add(cellKey(c))) chosen.add(c);
                }
            }
            for (var c : singles) {
                if (chosen.size() < needed && used.add(cellKey(c))) chosen.add(c);
            }
            if (chosen.size() < needed) {
                for (var c : candidates) {
                    if (c.y() == y && chosen.size() < needed && used.add(cellKey(c))) chosen.add(c);
                }
            }
        }
        return chosen;
    }

    private static void addWallPair(
            List<IndustrialStructure.Cell[]> out,
            Map<Long, IndustrialStructure.Cell> byKey,
            Set<Long> used,
            int ax, int ay, int az,
            int bx, int by, int bz) {
        if (ax == bx && ay == by && az == bz) return;
        long ka = ((long) (ax + 16) << 16) | ((long) (ay + 16) << 8) | (az + 16);
        long kb = ((long) (bx + 16) << 16) | ((long) (by + 16) << 8) | (bz + 16);
        var ca = byKey.get(ka);
        var cb = byKey.get(kb);
        if (ca != null && cb != null && !used.contains(ka) && !used.contains(kb)) {
            out.add(new IndustrialStructure.Cell[]{ca, cb});
        }
    }

    /**
     * Orders Energy Matrix 3D interior cells from the core outward, stacking evenly to the roof:
     * 1) center column(s) stacked bottom-to-top, 2) diagonals expanding outward to corners evenly by height,
     * 3) cardinal axes expanding from center to side walls evenly by height, 4) remaining columns outward evenly.
     */
    private static void sortMatrixCoreOutward(List<IndustrialStructure.Cell> cells, IndustrialStructure.Shape shape) {
        if (cells.size() <= 1) return;
        int innerW = Math.max(1, shape.width() - 2);
        int innerH = Math.max(1, shape.height() - 2);
        int innerD = Math.max(1, shape.depth() - 2);
        int minX = -shape.controllerColumn() + 1;
        int minZ = 1;

        var groups = new java.util.TreeMap<Long, List<int[]>>();
        for (int iz = 0; iz < innerD; iz++) {
            for (int ix = 0; ix < innerW; ix++) {
                int hx = Math.abs(2 * ix - (innerW - 1));
                int hz = Math.abs(2 * iz - (innerD - 1));
                int radius = Math.max(hx, hz);
                int phase;
                if (radius <= 1) {
                    phase = 0; // Center pillar(s)
                } else if (hx == hz) {
                    phase = 1; // Diagonals expanding outward into corners
                } else if (hx <= 1 || hz <= 1) {
                    phase = 2; // Cardinal axes expanding from center to side walls
                } else {
                    phase = 3; // Remaining columns expanding evenly outward
                }
                long groupKey = ((long) phase << 16) | radius;
                groups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(new int[]{ix, iz});
            }
        }

        int[][][] rank = new int[innerW][innerH][innerD];
        for (int[][] plane : rank) {
            for (int[] row : plane) java.util.Arrays.fill(row, Integer.MAX_VALUE);
        }
        int next = 0;
        for (var groupCols : groups.values()) {
            // Pair opposite columns cleanly within each phase/radius ring so every height step grows symmetrically.
            var orderedCols = orderOppositeColumns(groupCols, innerW, innerD);
            for (int y = 0; y < innerH; y++) {
                for (int[] col : orderedCols) {
                    if (rank[col[0]][y][col[1]] == Integer.MAX_VALUE) {
                        rank[col[0]][y][col[1]] = next++;
                    }
                }
            }
        }

        cells.sort((a, b) -> {
            int ax = Math.clamp(a.x() - minX, 0, innerW - 1);
            int ay = Math.clamp(a.y(), 0, innerH - 1);
            int az = Math.clamp(a.depth() - minZ, 0, innerD - 1);
            int bx = Math.clamp(b.x() - minX, 0, innerW - 1);
            int by = Math.clamp(b.y(), 0, innerH - 1);
            int bz = Math.clamp(b.depth() - minZ, 0, innerD - 1);
            return Integer.compare(rank[ax][ay][az], rank[bx][by][bz]);
        });
    }

    private static List<int[]> orderOppositeColumns(List<int[]> cols, int w, int d) {
        var present = new boolean[w][d];
        for (int[] c : cols) present[c[0]][c[1]] = true;
        // Sort deterministically: front-left corners/axes first (ascending z, then x).
        cols.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
        var used = new boolean[w][d];
        var out = new ArrayList<int[]>(cols.size());
        for (int[] c : cols) {
            if (!used[c[0]][c[1]]) {
                used[c[0]][c[1]] = true;
                out.add(c);
            }
            int ox = w - 1 - c[0];
            int oz = d - 1 - c[1];
            if (present[ox][oz] && !used[ox][oz]) {
                used[ox][oz] = true;
                out.add(new int[]{ox, oz});
            }
        }
        return out;
    }

    /**
     * Orders candidate interior mount cells layer-by-layer (ascending y) in concentric perimeter rings from outside in.
     * On each ring: 1) diagonal opposite corners, 2) opposite side midpoints, 3) farthest-point pairs around the ring
     * maximizing distance from already placed mounts until the ring is full, then repeats on the next inner ring.
     */
    private static void sortSymmetrically(List<IndustrialStructure.Cell> cells, IndustrialStructure.Shape shape) {
        if (cells.size() <= 1) return;
        int innerW = Math.max(1, shape.width() - 2);
        int innerD = Math.max(1, shape.depth() - 2);
        int minX = -shape.controllerColumn() + 1;
        int minZ = 1;
        int[][] rank = symmetricLayerRanks(innerW, innerD);
        cells.sort((a, b) -> {
            int byY = Integer.compare(a.y(), b.y());
            if (byY != 0) return byY;
            int ax = Math.clamp(a.x() - minX, 0, innerW - 1);
            int az = Math.clamp(a.depth() - minZ, 0, innerD - 1);
            int bx = Math.clamp(b.x() - minX, 0, innerW - 1);
            int bz = Math.clamp(b.depth() - minZ, 0, innerD - 1);
            return Integer.compare(rank[ax][az], rank[bx][bz]);
        });
    }

    private static int[][] symmetricLayerRanks(int w, int d) {
        int[][] rank = new int[w][d];
        for (int[] row : rank) java.util.Arrays.fill(row, -1);
        int[] next = {0};

        for (int ring = 0; ring <= (w - 1) / 2 && ring <= (d - 1) / 2; ring++) {
            int x0 = ring, x1 = w - 1 - ring;
            int z0 = ring, z1 = d - 1 - ring;
            int rw = x1 - x0 + 1;
            int rd = z1 - z0 + 1;

            // Collect this ring's perimeter cells in clockwise order so tie-breaks sweep cleanly around the rim.
            var perimeter = new ArrayList<int[]>(Math.max(1, 2 * (rw + rd) - 4));
            if (rw == 1 && rd == 1) {
                perimeter.add(new int[]{x0, z0});
            } else if (rd == 1) {
                for (int x = x0; x <= x1; x++) perimeter.add(new int[]{x, z0});
            } else if (rw == 1) {
                for (int z = z0; z <= z1; z++) perimeter.add(new int[]{x0, z});
            } else {
                for (int x = x0; x <= x1; x++) perimeter.add(new int[]{x, z0});
                for (int z = z0 + 1; z <= z1; z++) perimeter.add(new int[]{x1, z});
                for (int x = x1 - 1; x >= x0; x--) perimeter.add(new int[]{x, z1});
                for (int z = z1 - 1; z > z0; z--) perimeter.add(new int[]{x0, z});
            }

            var placedInRing = new ArrayList<int[]>(perimeter.size());

            // 1. Diagonal opposite corners of this ring first.
            assignRingPair(rank, next, placedInRing, x0, z0, x0, x1, z0, z1);
            assignRingPair(rank, next, placedInRing, x1, z0, x0, x1, z0, z1);

            // 2. Opposite side midpoints (on odd-length sides of length >= 3).
            if (rw >= 3 && (rw & 1) == 1) {
                assignRingPair(rank, next, placedInRing, x0 + rw / 2, z0, x0, x1, z0, z1);
            }
            if (rd >= 3 && (rd & 1) == 1) {
                assignRingPair(rank, next, placedInRing, x0, z0 + rd / 2, x0, x1, z0, z1);
            }

            // 3. Remaining perimeter positions in opposite pairs, always maximizing spacing from already placed ring mounts.
            while (placedInRing.size() < perimeter.size()) {
                int[] best = null;
                int bestMinDistSq = -1;
                long bestSumDistSq = -1L;
                for (int[] cand : perimeter) {
                    if (rank[cand[0]][cand[1]] >= 0) continue;
                    int minDistSq = Integer.MAX_VALUE;
                    long sumDistSq = 0L;
                    for (int[] p : placedInRing) {
                        int dx = cand[0] - p[0];
                        int dz = cand[1] - p[1];
                        int distSq = dx * dx + dz * dz;
                        if (distSq < minDistSq) minDistSq = distSq;
                        sumDistSq += distSq;
                    }
                    if (best == null || minDistSq > bestMinDistSq || (minDistSq == bestMinDistSq && sumDistSq > bestSumDistSq)) {
                        best = cand;
                        bestMinDistSq = minDistSq;
                        bestSumDistSq = sumDistSq;
                    }
                }
                if (best == null) break;
                assignRingPair(rank, next, placedInRing, best[0], best[1], x0, x1, z0, z1);
            }
        }
        return rank;
    }

    private static void assignRingPair(
            int[][] rank, int[] next, List<int[]> placedInRing,
            int ix, int iz, int x0, int x1, int z0, int z1) {
        if (rank[ix][iz] < 0) {
            rank[ix][iz] = next[0]++;
            placedInRing.add(new int[]{ix, iz});
        }
        int ox = x0 + x1 - ix;
        int oz = z0 + z1 - iz;
        if (rank[ox][oz] < 0) {
            rank[ox][oz] = next[0]++;
            placedInRing.add(new int[]{ox, oz});
        }
    }

    /**
     * Allocates Energy Matrix Cells or Providers preferring same-type columns (avoiding columns occupied by the other type),
     * falling back to mixed columns only when every pure column is full so full 3D capacity remains usable.
     */
    private static void allocateMatrixParts(
            List<IndustrialStructure.LocalMismatch> out,
            IndustrialStructure.Material partMat,
            int needed,
            List<IndustrialStructure.Cell> freeCells,
            Set<Long> ownCols,
            Set<Long> otherCols,
            int crown,
            Set<Long> floorMounts,
            Set<Long> roofMounts) {
        if (needed <= 0 || freeCells.isEmpty()) return;
        int remaining = needed;
        // Pass 1: pure columns only (never stack on top of the opposite part type while free/same-type columns exist).
        for (var it = freeCells.iterator(); it.hasNext() && remaining > 0; ) {
            var cell = it.next();
            long col = xzKey(cell.x(), cell.depth());
            if (otherCols.contains(col)) continue;
            it.remove();
            out.add(new IndustrialStructure.LocalMismatch(cell, partMat, ""));
            ownCols.add(col);
            if (cell.y() == 0) floorMounts.add(col);
            if (cell.y() == crown) roofMounts.add(col);
            remaining--;
        }
        // Pass 2: fallback to remaining free slots only when filling a near-full chamber.
        for (var it = freeCells.iterator(); it.hasNext() && remaining > 0; ) {
            var cell = it.next();
            long col = xzKey(cell.x(), cell.depth());
            it.remove();
            out.add(new IndustrialStructure.LocalMismatch(cell, partMat, ""));
            ownCols.add(col);
            if (cell.y() == 0) floorMounts.add(col);
            if (cell.y() == crown) roofMounts.add(col);
            remaining--;
        }
    }

    private static void allocateModules(
            List<IndustrialStructure.LocalMismatch> out,
            IndustrialStructure.Material moduleMat,
            int needed,
            boolean preferCeiling,
            List<IndustrialStructure.Cell> freeCeiling,
            List<IndustrialStructure.Cell> freeFloor,
            int crown,
            java.util.Set<Long> floorMounts,
            java.util.Set<Long> roofMounts) {
        var first = preferCeiling ? freeCeiling : freeFloor;
        var second = preferCeiling ? freeFloor : freeCeiling;
        for (int i = 0; i < needed; i++) {
            var cell = !first.isEmpty() ? first.removeFirst() : !second.isEmpty() ? second.removeFirst() : null;
            if (cell == null) break;
            out.add(new IndustrialStructure.LocalMismatch(cell, moduleMat, cell.y() == 0 ? "up" : "down"));
            if (cell.y() == 0) floorMounts.add(xzKey(cell.x(), cell.depth()));
            if (cell.y() == crown) roofMounts.add(xzKey(cell.x(), cell.depth()));
        }
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putInt("depth", depth);
        tag.putInt("column", controllerColumn);
        tag.putInt("ports", ports);
        tag.putInt("chambers", chambers);
        tag.putInt("coils", coils);
        tag.putInt("grinding", grinding);
        tag.putInt("mixing", mixing);
        tag.putInt("separation", separation);
        if (shellStyle != ShellStyle.SIDES_GLASS) {
            tag.putString("shell_style", shellStyle.id());
        }
        if (!exactLayout.isEmpty()) {
            int[] packed = new int[exactLayout.size() * 4];
            int i = 0;
            for (var cell : IndustrialStructure.layout(shape())) {
                if (cell.role() == IndustrialStructure.Role.CONTROLLER) continue;
                var mat = exactLayout.get(localCoordKey(cell.x(), cell.y(), cell.depth()));
                if (mat == null) continue;
                packed[i++] = cell.x();
                packed[i++] = cell.y();
                packed[i++] = cell.depth();
                packed[i++] = mat.ordinal();
            }
            if (i > 0) {
                tag.putIntArray("exact_layout", i == packed.length ? packed : java.util.Arrays.copyOf(packed, i));
            }
        }
        return tag;
    }

    public static Optional<IndustrialBlueprint> restore(MachineKind kind, CompoundTag tag) {
        if (!IndustrialStructure.isIndustrial(kind) || tag.getIntOr("schema", 0) != 1) {
            return Optional.empty();
        }
        try {
            var defaults = defaultFor(kind);
            int w = tag.getIntOr("width", defaults.width);
            var base = new IndustrialBlueprint(
                    kind,
                    w,
                    tag.getIntOr("height", defaults.height),
                    tag.getIntOr("depth", defaults.depth),
                    tag.getIntOr("column", defaultColumn(w)),
                    tag.getIntOr("ports", defaults.ports),
                    tag.getIntOr("chambers", defaults.chambers),
                    tag.getIntOr("coils", defaults.coils),
                    tag.getIntOr("grinding", defaults.grinding),
                    tag.getIntOr("mixing", defaults.mixing),
                    tag.getIntOr("separation", defaults.separation),
                    ShellStyle.load(tag.getStringOr("shell_style", ShellStyle.SIDES_GLASS.id())));
            var rawLayout = tag.getIntArray("exact_layout").orElse(null);
            if (rawLayout != null && rawLayout.length >= 4 && (rawLayout.length & 3) == 0 && rawLayout.length <= 4 * 9 * 9 * 9) {
                var materials = IndustrialStructure.Material.values();
                var map = new LinkedHashMap<Long, IndustrialStructure.Material>(rawLayout.length / 4);
                for (int i = 0; i < rawLayout.length; i += 4) {
                    int ord = rawLayout[i + 3];
                    if (ord >= 0 && ord < materials.length) {
                        map.put(localCoordKey(rawLayout[i], rawLayout[i + 1], rawLayout[i + 2]), materials[ord]);
                    }
                }
                return Optional.of(base.withExactLayout(map));
            }
            return Optional.of(base);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("depth", depth);
        json.addProperty("column", controllerColumn);
        json.addProperty("ports", ports);
        json.addProperty("chambers", chambers);
        json.addProperty("coils", coils);
        json.addProperty("grinding", grinding);
        json.addProperty("mixing", mixing);
        json.addProperty("separation", separation);
        json.addProperty("shellStyle", shellStyle.id());
        if (!exactLayout.isEmpty()) {
            var arr = new com.google.gson.JsonArray();
            for (var cell : IndustrialStructure.layout(shape())) {
                if (cell.role() == IndustrialStructure.Role.CONTROLLER) continue;
                var mat = exactLayout.get(localCoordKey(cell.x(), cell.y(), cell.depth()));
                if (mat == null) continue;
                arr.add(cell.x());
                arr.add(cell.y());
                arr.add(cell.depth());
                arr.add(mat.ordinal());
            }
            if (!arr.isEmpty()) json.add("exactLayout", arr);
        }
        return json;
    }

    public static IndustrialBlueprint fromJson(MachineKind kind, JsonObject json) {
        var defaults = defaultFor(kind);
        if (json == null) return defaults;
        int w = json.has("width") ? json.get("width").getAsInt() : defaults.width;
        var base = new IndustrialBlueprint(
                kind,
                w,
                json.has("height") ? json.get("height").getAsInt() : defaults.height,
                json.has("depth") ? json.get("depth").getAsInt() : defaults.depth,
                json.has("column") ? json.get("column").getAsInt() : defaultColumn(w),
                json.has("ports") ? json.get("ports").getAsInt() : defaults.ports,
                json.has("chambers") ? json.get("chambers").getAsInt() : defaults.chambers,
                json.has("coils") ? json.get("coils").getAsInt() : defaults.coils,
                json.has("grinding") ? json.get("grinding").getAsInt() : defaults.grinding,
                json.has("mixing") ? json.get("mixing").getAsInt() : defaults.mixing,
                json.has("separation") ? json.get("separation").getAsInt() : defaults.separation,
                json.has("shellStyle") ? ShellStyle.load(json.get("shellStyle").getAsString()) : ShellStyle.SIDES_GLASS);
        if (json.has("exactLayout") && json.get("exactLayout").isJsonArray()) {
            var arr = json.getAsJsonArray("exactLayout");
            if (arr.size() >= 4 && (arr.size() & 3) == 0 && arr.size() <= 4 * 9 * 9 * 9) {
                var materials = IndustrialStructure.Material.values();
                var map = new LinkedHashMap<Long, IndustrialStructure.Material>(arr.size() / 4);
                for (int i = 0; i < arr.size(); i += 4) {
                    int ord = arr.get(i + 3).getAsInt();
                    if (ord >= 0 && ord < materials.length) {
                        map.put(localCoordKey(arr.get(i).getAsInt(), arr.get(i + 1).getAsInt(), arr.get(i + 2).getAsInt()), materials[ord]);
                    }
                }
                return base.withExactLayout(map);
            }
        }
        return base;
    }
}
