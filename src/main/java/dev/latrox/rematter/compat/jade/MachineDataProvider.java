package dev.latrox.rematter.compat.jade;

import dev.latrox.rematter.content.IndustrialStructure;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** Server-only data collection from a short machine summary; names are sent as IDs and translated by the client. */
final class MachineDataProvider implements IServerDataProvider<BlockAccessor> {
    static final MachineDataProvider INSTANCE = new MachineDataProvider();
    static final String DATA_KEY = "rematter:machine";
    static final int DETAIL_ROWS = 6;
    private MachineDataProvider() {}

    @Override public Identifier getUid() { return ReMatterJadePlugin.STATUS; }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MachineBlockEntity machine)) return;
        CompoundTag view = new CompoundTag();
        var summary = machine.summaryFor(accessor.getPlayer(), accessor.showDetails() ? DETAIL_ROWS : 0);
        view.putBoolean("locked", summary == null);
        if (summary != null) {
            view.putString("status", summary.status());
            view.putInt("staleOrders", summary.staleOrders());
            view.putInt("heldOrders", summary.heldOrders());
            view.putBoolean("processes", machine.kind().processes());
            view.putInt("energy", summary.energy());
            view.putInt("energyCapacity", MachineBlockEntity.ENERGY_CAPACITY);
            view.putInt("progress", summary.progress());
            view.putInt("duration", summary.duration());
            view.putLong("matter", summary.used());
            view.putLong("capacity", summary.capacity());
            view.putString("storageSource", summary.storageSource());
            if (machine.kind() == MachineKind.DRIVE) {
                view.putInt("cellBays", summary.cells().size());
                view.putInt("cellsInstalled", (int) summary.cells().stream().filter(MachineSnapshot.CellView::installed).count());
                view.putBoolean("cellUnreadable", summary.cells().stream().anyMatch(cell -> cell.installed() && !cell.readable()));
            }
            var structure = summary.structure();
            boolean industrial = IndustrialStructure.isIndustrial(machine.kind());
            if (industrial && structure.width() > 0) {
                view.putInt("structureWidth", structure.width());
                view.putInt("structureDepth", structure.depth());
                view.putInt("structureHeight", structure.height());
                view.putInt("structureRate", structure.workRate());
                view.putInt("structureColumns", structure.moduleCount());
                view.putInt("structureSaving", structure.energySavingPercent());
                view.putInt("grinding", structure.grinding());
                view.putInt("mixing", structure.mixing());
                view.putInt("separation", structure.separation());
                view.putInt("coils", structure.coils());
                view.putInt("chambers", structure.chambers());
            }
            if (industrial) view.putString("structureDiagnostic", structure.diagnostic());
            view.putBoolean("automation", summary.automation());
            view.putInt("fluidAmount", summary.fluidAmount());
            view.putString("fluidId", summary.fluidId());
            view.putInt("shown", summary.entries().size());
            view.putInt("substances", summary.types());
            for (int i = 0; i < summary.entries().size(); i++) {
                MachineSnapshot.Entry entry = summary.entries().get(i);
                CompoundTag line = new CompoundTag();
                line.putString("id", entry.id());
                line.putString("phase", entry.phase());
                line.putLong("amount", entry.amount());
                line.putBoolean("known", entry.known());
                view.put("entry" + i, line);
            }
        }
        data.put(DATA_KEY, view);
    }
}
