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
        int separation) {

    public static final int MAX_CONFIGURED_PORTS = 16;

    public enum Parameter {
        WIDTH,
        DEPTH,
        HEIGHT,
        PORTS,
        CHAMBERS,
        COILS,
        GRINDING,
        MIXING,
        SEPARATION
    }

    public record PartRequirement(IndustrialStructure.Material material, String blockKey, int required, int missing) {}

    public IndustrialBlueprint {
        Objects.requireNonNull(kind);
        if (!IndustrialStructure.isIndustrial(kind)) {
            throw new IllegalArgumentException("Not an industrial controller: " + kind);
        }
        int minH = IndustrialStructure.height(kind);
        width = Math.clamp(width, 3, IndustrialStructure.MAX_SIZE);
        height = Math.clamp(height, minH, IndustrialStructure.MAX_SIZE);
        depth = Math.clamp(depth, 3, IndustrialStructure.MAX_SIZE);
        if (kind == MachineKind.INDUSTRIAL_PREPARATOR && mountCapacity(width, depth) < 3) {
            depth = Math.max(depth, 4);
        }
        controllerColumn = Math.clamp(controllerColumn, 1, width - 2);
        ports = Math.clamp(ports, 2, Math.min(MAX_CONFIGURED_PORTS, maxHorizontalWallPorts(width, height, depth)));

        int maxSlots = mountCapacity(width, depth);
        if (IndustrialStructure.usesReactorModules(kind)) {
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

    public static IndustrialBlueprint defaultFor(MachineKind kind) {
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
        if (IndustrialStructure.usesReactorModules(kind)) {
            return new IndustrialBlueprint(
                    kind, shape.width(), shape.height(), shape.depth(), shape.controllerColumn(),
                    portCount, Math.max(1, perf.chambers()), Math.max(1, perf.coils()), 0, 0, 0);
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
        return mountCapacity(width, depth);
    }

    public int totalModules() {
        return IndustrialStructure.usesReactorModules(kind) ? chambers + coils : grinding + mixing + separation;
    }

    public IndustrialPerformance performance() {
        return new IndustrialPerformance(grinding, mixing, separation, coils, chambers);
    }

    public int workRate() {
        return performance().workRate(kind);
    }

    public int laneRoom() {
        return IndustrialStructure.laneRoom(kind, height);
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
                return new IndustrialBlueprint(kind, w, h, d, controllerColumn, p, ch, co, gr, mx, sp);
            }
            case PORTS -> {
                p = Math.clamp(p + delta, 2, Math.min(MAX_CONFIGURED_PORTS, maxHorizontalWallPorts(w, h, d)));
                return new IndustrialBlueprint(kind, w, h, d, controllerColumn, p, ch, co, gr, mx, sp);
            }
            case CHAMBERS -> {
                if (!IndustrialStructure.usesReactorModules(kind)) return this;
                int maxTotal = mountCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE);
                ch = Math.clamp(ch + delta, 1, maxTotal - Math.max(1, co));
                return growFootprintToFit(w, h, d, p, ch, co, 0, 0, 0);
            }
            case COILS -> {
                if (!IndustrialStructure.usesReactorModules(kind)) return this;
                int maxTotal = mountCapacity(IndustrialStructure.MAX_SIZE, IndustrialStructure.MAX_SIZE);
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
        }
        return this;
    }

    private IndustrialBlueprint growFootprintToFit(int w, int h, int d, int p, int ch, int co, int gr, int mx, int sp) {
        int needed = IndustrialStructure.usesReactorModules(kind) ? ch + co : gr + mx + sp;
        boolean colWasCentered = controllerColumn == defaultColumn(w);
        while (mountCapacity(w, d) < needed && (w < IndustrialStructure.MAX_SIZE || d < IndustrialStructure.MAX_SIZE)) {
            if (d <= w && d < IndustrialStructure.MAX_SIZE) {
                d++;
            } else if (w < IndustrialStructure.MAX_SIZE) {
                w++;
            } else {
                d++;
            }
        }
        int col = colWasCentered ? defaultColumn(w) : Math.clamp(controllerColumn, 1, w - 2);
        return new IndustrialBlueprint(kind, w, h, d, col, p, ch, co, gr, mx, sp);
    }

    private IndustrialBlueprint trimModulesToFit(int w, int h, int d, int col, int p, int ch, int co, int gr, int mx, int sp) {
        int cap = mountCapacity(w, d);
        if (IndustrialStructure.usesReactorModules(kind)) {
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
        return new IndustrialBlueprint(kind, w, h, d, col, p, ch, co, gr, mx, sp);
    }

    /** Exact bill of materials required to build this configured chamber (including 1 controller). */
    public Map<IndustrialStructure.Material, Integer> requiredCounts() {
        var counts = new LinkedHashMap<IndustrialStructure.Material, Integer>();
        int casingCount = 0;
        int wallPool = 0;
        for (var cell : IndustrialStructure.layout(shape())) {
            switch (cell.role()) {
                case CONTROLLER -> {}
                case FRAME -> casingCount++;
                case WALL -> {
                    if (IndustrialStructure.usesReactorModules(kind) && cell.outward().getAxis().isVertical()) {
                        casingCount++;
                    } else {
                        wallPool++;
                    }
                }
                case INTERIOR -> {}
            }
        }
        int portCount = Math.min(ports, wallPool);
        int glassCount = Math.max(0, wallPool - portCount);
        counts.put(IndustrialStructure.Material.CONTROLLER, 1);
        counts.put(IndustrialStructure.Material.CASING, casingCount);
        if (glassCount > 0) counts.put(IndustrialStructure.Material.GLASS, glassCount);
        counts.put(IndustrialStructure.Material.PORT, portCount);
        if (IndustrialStructure.usesReactorModules(kind)) {
            counts.put(IndustrialStructure.Material.REACTION_CHAMBER, chambers);
            counts.put(IndustrialStructure.Material.FIELD_COIL, coils);
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
        var allowedModules = kind == MachineKind.INDUSTRIAL_PREPARATOR
                ? List.of(IndustrialStructure.Material.GRINDING_UNIT, IndustrialStructure.Material.MIXING_BASIN, IndustrialStructure.Material.SEPARATION_UNIT)
                : List.of(IndustrialStructure.Material.REACTION_CHAMBER, IndustrialStructure.Material.FIELD_COIL);

        var collected = new ArrayList<IndustrialStructure.LocalMismatch>();
        var openHorizontalWalls = new ArrayList<IndustrialStructure.Cell>();
        var openVerticalWalls = new ArrayList<IndustrialStructure.Cell>();
        var closedNonPortHorizontalWalls = new ArrayList<IndustrialStructure.Cell>();
        var freeFloor = new ArrayList<IndustrialStructure.Cell>();
        var freeCeiling = new ArrayList<IndustrialStructure.Cell>();
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
                    if (IndustrialStructure.usesReactorModules(kind) && cell.outward().getAxis().isVertical()) {
                        if (mat != IndustrialStructure.Material.CASING) {
                            collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.CASING, ""));
                        }
                    } else if (mat == IndustrialStructure.Material.PORT) {
                        if (cell.outward().getAxis().isHorizontal()) {
                            placedPorts++;
                        } else {
                            collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.GLASS, ""));
                        }
                    } else if (mat != IndustrialStructure.Material.CASING && mat != IndustrialStructure.Material.GLASS) {
                        if (cell.outward().getAxis().isHorizontal()) openHorizontalWalls.add(cell);
                        else openVerticalWalls.add(cell);
                    } else if (cell.outward().getAxis().isHorizontal()) {
                        closedNonPortHorizontalWalls.add(cell);
                    }
                }
                case INTERIOR -> {
                    if (mat.module()) {
                        if (!allowedModules.contains(mat)) {
                            String orient = cell.y() == 0 ? "up" : cell.y() == crown ? "down" : "";
                            collected.add(new IndustrialStructure.LocalMismatch(cell, allowedModules.getFirst(), orient));
                        } else if (IndustrialModuleMount.atInteriorHeight(cell.y(), crown).isEmpty()) {
                            collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.AIR, ""));
                        } else {
                            placedModules.merge(mat, 1, Integer::sum);
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

        // Prefer eye-level (y == 0) horizontal wall positions first when suggesting Machine Ports.
        openHorizontalWalls.sort((a, b) -> Integer.compare(Math.abs(a.y()), Math.abs(b.y())));
        closedNonPortHorizontalWalls.sort((a, b) -> Integer.compare(Math.abs(a.y()), Math.abs(b.y())));

        int portsNeeded = Math.max(0, ports - placedPorts);
        for (var cell : openHorizontalWalls) {
            if (portsNeeded > 0) {
                collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.PORT, ""));
                portsNeeded--;
            } else {
                collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.GLASS, ""));
            }
        }
        for (var cell : openVerticalWalls) {
            collected.add(new IndustrialStructure.LocalMismatch(cell, IndustrialStructure.Material.GLASS, ""));
        }
        for (int i = 0; i < closedNonPortHorizontalWalls.size() && portsNeeded > 0; i++, portsNeeded--) {
            collected.add(new IndustrialStructure.LocalMismatch(closedNonPortHorizontalWalls.get(i), IndustrialStructure.Material.PORT, ""));
        }

        if (IndustrialStructure.usesReactorModules(kind)) {
            allocateModules(collected, IndustrialStructure.Material.REACTION_CHAMBER,
                    chambers - placedModules.getOrDefault(IndustrialStructure.Material.REACTION_CHAMBER, 0),
                    true, freeCeiling, freeFloor);
            allocateModules(collected, IndustrialStructure.Material.FIELD_COIL,
                    coils - placedModules.getOrDefault(IndustrialStructure.Material.FIELD_COIL, 0),
                    false, freeCeiling, freeFloor);
        } else {
            allocateModules(collected, IndustrialStructure.Material.GRINDING_UNIT,
                    grinding - placedModules.getOrDefault(IndustrialStructure.Material.GRINDING_UNIT, 0),
                    true, freeCeiling, freeFloor);
            allocateModules(collected, IndustrialStructure.Material.MIXING_BASIN,
                    mixing - placedModules.getOrDefault(IndustrialStructure.Material.MIXING_BASIN, 0),
                    false, freeCeiling, freeFloor);
            allocateModules(collected, IndustrialStructure.Material.SEPARATION_UNIT,
                    separation - placedModules.getOrDefault(IndustrialStructure.Material.SEPARATION_UNIT, 0),
                    false, freeCeiling, freeFloor);
        }

        var ordered = new ArrayList<IndustrialStructure.LocalMismatch>(collected.size());
        var seen = new HashSet<Long>();
        for (var entry : collected) {
            long key = ((long) (entry.cell().x() + 16) << 16) | ((long) (entry.cell().y() + 16) << 8) | (entry.cell().depth() + 16);
            if (seen.add(key)) {
                ordered.add(entry);
            }
        }
        return List.copyOf(ordered);
    }

    private static void allocateModules(
            List<IndustrialStructure.LocalMismatch> out,
            IndustrialStructure.Material moduleMat,
            int needed,
            boolean preferCeiling,
            List<IndustrialStructure.Cell> freeCeiling,
            List<IndustrialStructure.Cell> freeFloor) {
        var first = preferCeiling ? freeCeiling : freeFloor;
        var second = preferCeiling ? freeFloor : freeCeiling;
        for (int i = 0; i < needed; i++) {
            var cell = !first.isEmpty() ? first.removeFirst() : !second.isEmpty() ? second.removeFirst() : null;
            if (cell == null) break;
            out.add(new IndustrialStructure.LocalMismatch(cell, moduleMat, cell.y() == 0 ? "up" : "down"));
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
        return tag;
    }

    public static Optional<IndustrialBlueprint> restore(MachineKind kind, CompoundTag tag) {
        if (!IndustrialStructure.isIndustrial(kind) || tag.getIntOr("schema", 0) != 1) {
            return Optional.empty();
        }
        try {
            var defaults = defaultFor(kind);
            int w = tag.getIntOr("width", defaults.width);
            return Optional.of(new IndustrialBlueprint(
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
                    tag.getIntOr("separation", defaults.separation)));
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
        return json;
    }

    public static IndustrialBlueprint fromJson(MachineKind kind, JsonObject json) {
        var defaults = defaultFor(kind);
        if (json == null) return defaults;
        int w = json.has("width") ? json.get("width").getAsInt() : defaults.width;
        return new IndustrialBlueprint(
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
                json.has("separation") ? json.get("separation").getAsInt() : defaults.separation);
    }
}
