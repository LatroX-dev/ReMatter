package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.AutomationResourceHandler;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.ModContent;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalResource;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Loaded only when Mekanism is present. Compiled against Version Locked 26.1.2-2.11,
 * whose chemical capability is a transactional NeoForge resource handler.
 */
public final class MekanismCompat {
    private MekanismCompat() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MekanismCompat::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        try {
            var capability = Capabilities.CHEMICAL.block();
            // Earlier fork releases share mod version 10.8.0 but expose another
            // capability type. The mod version alone cannot establish compatibility.
            if (!capability.typeClass().equals(ResourceHandler.class)
                    || !capability.contextClass().equals(Direction.class)) {
                ReMatter.LOGGER.warn("Mekanism chemical integration disabled: incompatible chemical capability; expected Version Locked 26.1.2-2.11 API");
                return;
            }
            List<ChemicalResource> resources = new ArrayList<>();
            List<ChemicalMappings.Mapping> mappings = new ArrayList<>();
            for (var mapping : ChemicalMappings.ALL) {
                Identifier id = Identifier.parse(mapping.chemicalId());
                var chemical = MekanismAPI.CHEMICAL_REGISTRY.get(id);
                if (chemical.isEmpty()) {
                    ReMatter.LOGGER.warn("Mekanism chemical mapping unavailable: missing chemical {}", id);
                    continue;
                }
                resources.add(ChemicalResource.of(chemical.get()));
                mappings.add(mapping);
            }
            List<ChemicalResource> registeredResources = List.copyOf(resources);
            List<ChemicalMappings.Mapping> registeredMappings = List.copyOf(mappings);
            // Exactly one handler/journal per block, even when different faces are
            // involved in a nested transaction. Weak owner callbacks avoid retaining
            // unloaded worlds through the capability provider's cache.
            Map<MachineBlockEntity, MekanismChemicalHandler> handlers = new WeakHashMap<>();
            event.registerBlockEntity(capability, ModContent.MACHINE_ENTITY.get(), (machine, side) -> {
                if (side == null || !allowed(machine) || !machine.allowsPort(side, MachinePortMode.CHEMICAL)) return null;
                var delegate = handlers.computeIfAbsent(machine, owner -> handler(owner, registeredResources, registeredMappings));
                var reference = new WeakReference<>(machine);
                var permitted = (java.util.function.BooleanSupplier) () -> {
                    var owner = reference.get();
                    return owner != null && owner.allowsPort(side, MachinePortMode.CHEMICAL);
                };
                return new AutomationResourceHandler<>(delegate, permitted, permitted, delegate.size(), ChemicalResource.EMPTY);
            });
            ReMatter.LOGGER.info("Mekanism chemical integration registered {} exact mappings; each requires its chemistry definition (Version Locked 26.1.2-2.11 API)", registeredMappings.size());
        } catch (LinkageError incompatibleApi) {
            ReMatter.LOGGER.warn("Mekanism chemical integration disabled: incompatible API; expected Version Locked 26.1.2-2.11", incompatibleApi);
        }
    }

    private static MekanismChemicalHandler handler(MachineBlockEntity owner, List<ChemicalResource> resources,
                                                   List<ChemicalMappings.Mapping> mappings) {
        WeakReference<MachineBlockEntity> reference = new WeakReference<>(owner);
        ChemicalTransfers transfers = new ChemicalTransfers(
                () -> {
                    MachineBlockEntity current = reference.get();
                    return current == null ? null : current.matterStore();
                },
                () -> allowed(reference.get()),
                key -> ReMatter.chemistry().isKnown(key),
                mapping -> ChemicalMappings.exportable(ReMatter.chemistry().synthesisPolicy(), mapping),
                () -> {
                    MachineBlockEntity current = reference.get();
                    if (current != null) current.setChanged();
                }, mappings);
        return new MekanismChemicalHandler(transfers, resources);
    }

    private static boolean allowed(MachineBlockEntity machine) {
        if (machine == null || !machine.chemicalAutomationAllowed()) return false;
        var server = machine.getLevel().getServer();
        return server != null && server.isSameThread();
    }
}
