package dev.latrox.rematter.content;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A controller's association with its industrial shell: protection claim, ports, module visuals, client shape, and planned blueprint. */
final class MachineStructureLink {
    private final MachineBlockEntity machine;
    private boolean formed;
    private IndustrialStructure.@Nullable Shape shape;
    private IndustrialStructure.@Nullable ProtectionClaim claim;
    private List<IndustrialStructure.Port> ports = List.of();
    private List<IndustrialStructure.Module> modules = List.of();
    private boolean publishedWorking;
    private boolean previewEnabled;
    private @Nullable IndustrialBlueprint blueprint;

    MachineStructureLink(MachineBlockEntity machine) { this.machine = machine; }

    private boolean industrial() { return IndustrialStructure.isIndustrial(machine.kind()); }
    IndustrialStructure.@Nullable ProtectionClaim claim() { return claim; }
    List<IndustrialStructure.Port> ports() { return ports; }

    boolean previewEnabled() { return industrial() && previewEnabled; }
    void setPreviewEnabled(boolean enabled) {
        if (!industrial() || this.previewEnabled == enabled) return;
        this.previewEnabled = enabled;
        machine.setChanged();
    }

    boolean hasCustomBlueprint() { return industrial() && blueprint != null; }
    @Nullable IndustrialBlueprint customBlueprint() { return industrial() ? blueprint : null; }

    IndustrialBlueprint effectiveBlueprint() {
        if (!industrial()) throw new IllegalStateException("Not an industrial controller");
        if (blueprint != null) return blueprint;
        if (machine.getLevel() instanceof ServerLevel) {
            var current = IndustrialStructure.inspect(machine);
            if (current.formed()) return IndustrialBlueprint.fromFormation(machine.kind(), current);
            if (claim != null) {
                var s = claim.shape();
                var def = IndustrialBlueprint.defaultFor(machine.kind());
                return new IndustrialBlueprint(machine.kind(), s.width(), s.height(), s.depth(), s.controllerColumn(),
                        def.ports(), def.chambers(), def.coils(), def.grinding(), def.mixing(), def.separation());
            }
        }
        return IndustrialBlueprint.defaultFor(machine.kind());
    }

    void setBlueprint(@Nullable IndustrialBlueprint value) {
        if (!industrial()) return;
        if (value != null && value.kind() != machine.kind()) return;
        if (Objects.equals(this.blueprint, value)) return;
        this.blueprint = value;
        machine.setChanged();
    }

    boolean adjustBlueprint(IndustrialBlueprint.Parameter parameter, int delta) {
        if (!industrial() || delta == 0) return false;
        IndustrialBlueprint current = effectiveBlueprint();
        IndustrialBlueprint next = current.adjust(parameter, delta);
        if (next.equals(current) && blueprint != null) return false;
        this.blueprint = next;
        machine.setChanged();
        return true;
    }

    boolean resetBlueprint() {
        if (!industrial() || blueprint == null) return false;
        blueprint = null;
        machine.setChanged();
        return true;
    }

    /** Returns true when a claim was dropped. */
    boolean clearClaim() {
        if (claim == null) return false;
        claim = null;
        return true;
    }

    boolean formed() {
        return machine.getLevel() instanceof ServerLevel ? IndustrialStructure.inspect(machine).formed() : formed;
    }
    IndustrialStructure.@Nullable Shape visibleShape() {
        if (machine.getLevel() instanceof ServerLevel) {
            var current = IndustrialStructure.inspect(machine);
            return current.formed() ? current.shape() : null;
        }
        return formed ? shape : null;
    }

    /** Server tick: follows the live shell and returns true when clients need an update. */
    boolean refresh() {
        if (!industrial()) return false;
        var level = machine.getLevel();
        var current = IndustrialStructure.inspect(machine);
        if (current.formed()) {
            var next = new IndustrialStructure.ProtectionClaim(current.shape(), machine.getBlockState().getValue(MachineBlock.FACING));
            if (!next.equals(claim)) { claim = next; machine.setChanged(); machine.touchObservations(); }
        }
        if (formed == current.formed() && Objects.equals(shape, current.shape()) && ports.equals(current.ports())
                && modules.equals(current.modules())) return false;
        clearModules();
        publishedWorking = false;
        var changed = new HashSet<BlockPos>();
        ports.forEach(port -> changed.add(port.position()));
        current.ports().forEach(port -> changed.add(port.position()));
        formed = current.formed(); shape = current.shape(); ports = current.ports(); modules = current.modules();
        bindModules(false);
        changed.stream().filter(level::hasChunkAt).forEach(level::invalidateCapabilities);
        level.invalidateCapabilities(machine.getBlockPos());
        return true;
    }

    /** Visual authority only: a module never owns an inventory, energy buffer or persistent association. */
    boolean moduleWorking(BlockPos modulePosition, boolean visuallyActive) {
        if (!industrial() || !visuallyActive) return false;
        var formation = IndustrialStructure.inspect(machine);
        return formation.formed() && formation.hasModuleAt(modulePosition);
    }

    void publishActivity(boolean active) {
        if (!industrial()) return;
        long gameTime = machine.getLevel().getGameTime();
        if (active || publishedWorking || gameTime % 20 == 0) {
            bindModules(active);
            publishedWorking = active;
        }
    }

    void removed() {
        clearModules();
    }

    private void clearModules() {
        if (!(machine.getLevel() instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        for (var module : modules) if (server.hasChunkAt(module.position())
                && server.getBlockEntity(module.position()) instanceof IndustrialModuleBlockEntity entity) entity.clearBinding(machine);
    }
    private void bindModules(boolean active) {
        if (!(machine.getLevel() instanceof ServerLevel server) || !formed || shape == null) return;
        for (var module : modules) if (server.hasChunkAt(module.position())
                && server.getBlockEntity(module.position()) instanceof IndustrialModuleBlockEntity entity)
            entity.setWorking(machine, active, shape, module);
    }

    void write(CompoundTag tag) {
        if (!industrial()) return;
        tag.putBoolean("industrial_formed", formed);
        tag.putBoolean("structure_preview", previewEnabled);
        if (formed && shape != null) {
            tag.putInt("industrial_width", shape.width()); tag.putInt("industrial_height", shape.height());
            tag.putInt("industrial_depth", shape.depth()); tag.putInt("industrial_column", shape.controllerColumn());
        }
    }
    void read(ValueInput input) {
        if (!industrial()) return;
        formed = input.getBooleanOr("industrial_formed", false); shape = null;
        previewEnabled = input.getBooleanOr("structure_preview", false);
        if (formed) try {
            shape = new IndustrialStructure.Shape(input.getIntOr("industrial_width", 0), input.getIntOr("industrial_height", 0),
                    input.getIntOr("industrial_depth", 0), input.getIntOr("industrial_column", 0));
            if (shape.height() < IndustrialStructure.height(machine.kind())) throw new IllegalArgumentException("Wrong industrial height");
        } catch (IllegalArgumentException invalid) { formed = false; shape = null; }
    }

    void save(ValueOutput output) {
        if (!industrial()) return;
        if (claim != null) output.store("industrial_claim", CompoundTag.CODEC, claim.save());
        if (previewEnabled) output.putBoolean("structure_preview", true);
        if (blueprint != null) output.store("industrial_blueprint", CompoundTag.CODEC, blueprint.save());
    }
    void load(ValueInput input) {
        if (!industrial()) {
            claim = null;
            previewEnabled = false;
            blueprint = null;
            return;
        }
        claim = input.read("industrial_claim", CompoundTag.CODEC)
                .flatMap(IndustrialStructure.ProtectionClaim::restore)
                .filter(saved -> saved.shape().height() >= IndustrialStructure.height(machine.kind())).orElse(null);
        previewEnabled = input.getBooleanOr("structure_preview", false);
        blueprint = input.read("industrial_blueprint", CompoundTag.CODEC)
                .flatMap(raw -> IndustrialBlueprint.restore(machine.kind(), raw)).orElse(null);
    }
}
