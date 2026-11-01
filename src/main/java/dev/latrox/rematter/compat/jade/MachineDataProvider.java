package dev.latrox.rematter.compat.jade;

import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** Server-only data collection; no client classes or unguarded container reads. */
final class MachineDataProvider implements IServerDataProvider<BlockAccessor> {
    static final MachineDataProvider INSTANCE = new MachineDataProvider();
    static final String DATA_KEY = "rematter:machine";
    private MachineDataProvider() {}

    @Override public Identifier getUid() { return ReMatterJadePlugin.STATUS; }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MachineBlockEntity machine)) return;
        CompoundTag view = new CompoundTag();
        MachineSnapshot snapshot = machine.snapshotFor(accessor.getPlayer());
        view.putBoolean("locked", snapshot == null);
        if (snapshot != null) {
            view.putString("status", snapshot.status());
            view.putBoolean("processes", machine.kind().processes());
            view.putInt("energy", snapshot.energy());
            view.putInt("energyCapacity", MachineBlockEntity.ENERGY_CAPACITY);
            view.putInt("progress", snapshot.progress());
            view.putInt("duration", snapshot.duration());
            view.putLong("matter", snapshot.used());
            view.putLong("capacity", snapshot.capacity());
            view.putString("storageSource", snapshot.storageSource());
            if (snapshot.structure().width() > 0) {
                view.putInt("structureWidth", snapshot.structure().width());
                view.putInt("structureDepth", snapshot.structure().depth());
                view.putInt("structureHeight", snapshot.structure().height());
                view.putInt("structureRate", snapshot.structure().workRate());
                view.putInt("structureColumns", snapshot.structure().moduleCount());
            }
            view.putString("structureDiagnostic", snapshot.structure().diagnostic());
            view.putBoolean("automation", snapshot.automation());
            view.putInt("fluidAmount", snapshot.fluidAmount());
            view.putString("fluidName", snapshot.fluidName());
            int shown = Math.min(accessor.showDetails() ? 6 : 0, snapshot.entries().size());
            view.putInt("shown", shown);
            view.putInt("substances", snapshot.entries().size());
            for (int i = 0; i < shown; i++) {
                MachineSnapshot.Entry entry = snapshot.entries().get(i);
                CompoundTag line = new CompoundTag();
                line.putString("name", entry.name());
                line.putString("phase", entry.phase());
                line.putLong("amount", entry.amount());
                view.put("entry" + i, line);
            }
        }
        data.put(DATA_KEY, view);
    }
}
