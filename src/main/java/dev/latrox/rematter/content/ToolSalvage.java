package dev.latrox.rematter.content;

import dev.latrox.rematter.automation.OrderLedgerCodec;
import dev.latrox.rematter.celltransfer.CellTransferData;
import dev.latrox.rematter.celltransfer.CellTransferStationBlock;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.generation.CrystalBurner;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Safe decomposition validation for unenchanted damageable items and provably empty ReMatter devices.
 * Rejects enchantments, stored items/fluids/energy/matter/fuel/orders, partitions, links, active work,
 * unknown schemas/fields, and durability yields that round down to zero.
 */
public final class ToolSalvage {
    private static final Set<String> SAFE_EMPTY_MACHINE_FIELDS = Set.of(
            "schema", "owner", "security", "automation", "auto_eject", "ports", "network_jobs",
            "running", "repeat", "queued_batches", "selection",
            "target", "target_substance", "target_phase", "vented_gases", "vented_total",
            "redstone_mode", "redstone_pulse", "sound_volume", "sound_muted",
            "structure_preview", "industrial_blueprint", "assay",
            "items", "power", "tank",
            "extension_id", "identity_checkpoint", "checkpointed_cells",
            "energy_receipt_revision", "partial_energy_debt",
            "conversion_target", "conversion_amount", "converting", "conversion_progress", "conversion_id",
            "crystal_burner", "automation_host");

    public record Plan(MaterialProfile profile, Map<SubstanceKey, Long> matter, int damage, int maxDamage) {
        public Plan { matter = Map.copyOf(matter); }
        public boolean damaged() { return damage > 0; }
    }

    private ToolSalvage() {}

    public static boolean hasOnlyAllowedDamage(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var patch = stack.getComponentsPatch();
        if (patch.isEmpty()) return true;
        if (isEmptyRecoverableDevice(stack)) return true;
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0 || stack.isEnchanted()) return false;
        if (patch.size() != 1) return false;
        var only = patch.entrySet().iterator().next();
        if (!only.getKey().equals(DataComponents.DAMAGE) || only.getValue().isEmpty()) return false;
        int damage = stack.getDamageValue();
        return damage >= 0 && damage < stack.getMaxDamage();
    }

    public static Optional<Plan> plan(ChemistryRegistry registry, ItemStack stack) {
        if (!hasOnlyAllowedDamage(stack)) return Optional.empty();
        var profile = MachineOperations.profile(registry, stack);
        if (profile == null) return Optional.empty();
        return plan(profile, stack);
    }

    public static Optional<Plan> plan(MaterialProfile profile, ItemStack stack) {
        if (!hasOnlyAllowedDamage(stack)) return Optional.empty();
        if (stack.getComponentsPatch().isEmpty() || isEmptyRecoverableDevice(stack)) {
            return Optional.of(new Plan(profile, profile.matter(), 0, stack.getMaxDamage()));
        }
        if (profile.itemCount() != 1) return Optional.empty();
        int max = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        return scaleMatter(profile.matter(), damage, max)
                .map(scaled -> new Plan(profile, scaled, damage, max));
    }

    /**
     * Accepts only ReMatter items whose sole non-default component is verified empty portable state
     * (empty placed-and-broken machine/energy cube/cell transfer station, emptied unpartitioned Matter Cell,
     * or Matter Wrench mode). Anything carrying resources, partitions, links, energy, fuel, active jobs,
     * or unknown data is rejected.
     */
    static boolean isEmptyRecoverableDevice(ItemStack stack) {
        var patch = stack.getComponentsPatch();
        if (patch.size() != 1) return false;
        var only = patch.entrySet().iterator().next();
        if (only.getValue().isEmpty()) return false;

        if (stack.getItem() instanceof MatterWrenchItem) {
            return only.getKey().equals(WrenchMode.TYPE.get());
        }
        if (stack.getItem() instanceof SecurityCardItem) {
            return only.getKey().equals(SecurityMode.TYPE.get());
        }
        if (stack.getItem() instanceof MatterCellItem cell) {
            if (!only.getKey().equals(MatterCellData.TYPE.get())) return false;
            var data = stack.get(MatterCellData.TYPE.get());
            if (data == null) return false;
            var state = data.resolve(cell.capacity());
            return state.isPresent() && state.get().contents().isEmpty();
        }
        if (!only.getKey().equals(DataComponents.BLOCK_ENTITY_DATA)) return false;
        var entityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (entityData == null || !(stack.getItem() instanceof BlockItem blockItem)) return false;

        if (blockItem.getBlock() instanceof MachineBlock machineBlock) {
            return entityData.type() == ModContent.MACHINE_ENTITY.get()
                    && isEmptyMachineTag(entityData.copyTagWithoutId(), machineBlock.kind());
        }
        if (blockItem.getBlock() instanceof EnergyCubeBlock cubeBlock) {
            return entityData.type() == ModContent.ENERGY_CUBE_ENTITY.get()
                    && EnergyCubeData.isEmptyTag(entityData.copyTagWithoutId(), cubeBlock.tier());
        }
        if (blockItem.getBlock() instanceof CellTransferStationBlock) {
            return entityData.type() == ModContent.CELL_TRANSFER_STATION_ENTITY.get()
                    && CellTransferData.isEmptyTag(entityData.copyTagWithoutId());
        }
        return false;
    }

    /** Strict schema-verified empty check for portable MachineBlockEntity NBT; unknown keys are refused. */
    static boolean isEmptyMachineTag(CompoundTag tag, MachineKind kind) {
        if (!SAFE_EMPTY_MACHINE_FIELDS.containsAll(tag.keySet())) return false;
        if (!(tag.get("schema") instanceof IntTag) || tag.getIntOr("schema", -1) != MachineSchema.VERSION) return false;
        if (tag.contains("security") && (!(tag.get("security") instanceof StringTag) || !SecurityMode.validName(tag.getStringOr("security", "")))) {
            return false;
        }

        int expectedSlots = MachineCellAccess.inventorySize(kind);
        var items = tag.getList("items").orElse(null);
        if (items == null || items.size() != expectedSlots) return false;
        for (var item : items) {
            if (!(item instanceof CompoundTag compound) || !compound.isEmpty()) return false;
        }

        var power = tag.getCompound("power").orElse(null);
        if (power == null || !power.keySet().equals(Set.of("energy"))
                || !(power.get("energy") instanceof IntTag) || power.getIntOr("energy", -1) != 0) return false;

        var tank = tag.getCompound("tank").orElse(null);
        if (tank == null || !tank.keySet().equals(Set.of("stacks"))) return false;
        var fluids = tank.getList("stacks").orElse(null);
        if (fluids == null || fluids.size() != 1 || !(fluids.getFirst() instanceof CompoundTag fluid) || !fluid.isEmpty()) {
            return false;
        }

        if (tag.contains("energy_receipt_revision")
                && (!(tag.get("energy_receipt_revision") instanceof LongTag) || tag.getLongOr("energy_receipt_revision", -1L) != 0L)) {
            return false;
        }
        if (tag.contains("partial_energy_debt")
                && (!(tag.get("partial_energy_debt") instanceof LongTag) || tag.getLongOr("partial_energy_debt", -1L) != 0L)) {
            return false;
        }
        if (tag.getBooleanOr("converting", false)) return false;
        if (tag.getIntOr("conversion_progress", 0) != 0) return false;

        if (tag.contains("crystal_burner")) {
            var burner = tag.getCompound("crystal_burner").orElse(null);
            if (burner == null || !burner.keySet().equals(Set.of("schema", "remaining"))
                    || !(burner.get("schema") instanceof IntTag) || burner.getIntOr("schema", -1) != CrystalBurner.SCHEMA
                    || !(burner.get("remaining") instanceof LongTag) || burner.getLongOr("remaining", -1L) != 0L) {
                return false;
            }
        }

        if (tag.contains("automation_host")) {
            var host = tag.getCompound("automation_host").orElse(null);
            if (host == null || !host.keySet().equals(Set.of("schema", "id", "backend", "ledger", "adapters"))
                    || !(host.get("schema") instanceof IntTag) || host.getIntOr("schema", -1) != 1
                    || !(host.get("id") instanceof StringTag) || !(host.get("backend") instanceof StringTag)
                    || !(host.get("ledger") instanceof StringTag) || !(host.get("adapters") instanceof CompoundTag adapters)) {
                return false;
            }
            var parsedLedger = OrderLedgerCodec.read(host.getStringOr("ledger", ""));
            if (!parsedLedger.readable() || !parsedLedger.ledger().orElseThrow().isEmpty()) return false;
            for (String key : adapters.keySet()) {
                if (!(adapters.get(key) instanceof CompoundTag nested) || !nested.isEmpty()) return false;
            }
        }
        return true;
    }

    public static Optional<Map<SubstanceKey, Long>> scaleMatter(Map<SubstanceKey, Long> base, int damage, int maxDamage) {
        long remaining = (long) maxDamage - damage;
        if (maxDamage <= 0 || damage < 0 || remaining <= 0 || base.isEmpty()) return Optional.empty();
        var scaled = new LinkedHashMap<SubstanceKey, Long>();
        for (var entry : base.entrySet()) {
            long amount = Math.multiplyExact(entry.getValue(), remaining) / maxDamage;
            if (amount <= 0) return Optional.empty();
            scaled.put(entry.getKey(), amount);
        }
        return Optional.of(Map.copyOf(scaled));
    }
}
