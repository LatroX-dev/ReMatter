package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.MatterStore;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.AutomationResourceHandler;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.ModSounds;
import dev.latrox.rematter.content.PressurizedCanisterData;
import dev.latrox.rematter.content.PressurizedCanisterItem;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalResource;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Loaded only when Mekanism is present. Compiled against Version Locked 26.1.2-2.11,
 * whose chemical capability is a transactional NeoForge resource handler.
 */
public final class MekanismCompat {
    private static volatile List<ChemicalResource> activeResources = List.of();
    private static volatile List<ChemicalMappings.Mapping> activeMappings = List.of();

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
            var itemCapability = Capabilities.CHEMICAL.item();
            if (itemCapability.typeClass().equals(ResourceHandler.class)
                    && itemCapability.contextClass().equals(ItemAccess.class)) {
                activeResources = registeredResources;
                activeMappings = registeredMappings;
                event.registerItem(
                        itemCapability,
                        (stack, access) -> new MekanismCanisterHandler(
                                access,
                                registeredResources,
                                registeredMappings,
                                key -> ReMatter.chemistry().isKnown(key),
                                mapping -> ChemicalMappings.exportable(ReMatter.chemistry().synthesisPolicy(), mapping)),
                        ModContent.PRESSURIZED_CANISTER.get());
            }
            ReMatter.LOGGER.info("Mekanism chemical integration registered {} exact mappings; each requires its chemistry definition (Version Locked 26.1.2-2.11 API)", registeredMappings.size());
        } catch (LinkageError incompatibleApi) {
            ReMatter.LOGGER.warn("Mekanism chemical integration disabled: incompatible API; expected Version Locked 26.1.2-2.11", incompatibleApi);
        }
    }

    /**
     * Allows handheld Mekanism chemical items (such as Gauge Droppers and Chemical Tanks) to deposit or
     * extract mapped gases directly on ReMatter machines and Machine Ports, obeying synthesis export rules.
     */
    @SuppressWarnings("unchecked")
    public static InteractionResult interactWithMachine(
            ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand, @Nullable MachineBlockEntity machine) {
        if (stack.isEmpty() || stack.getItem() instanceof PressurizedCanisterItem || player.isSpectator()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        List<ChemicalResource> resources = activeResources;
        List<ChemicalMappings.Mapping> mappings = activeMappings;
        if (resources.isEmpty() || mappings.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (machine != null && machine.kind() == MachineKind.TERMINAL && !player.isShiftKeyDown()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        ResourceHandler<ChemicalResource> itemHandler;
        try {
            itemHandler = (ResourceHandler<ChemicalResource>)
                    ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.CHEMICAL.item());
        } catch (LinkageError | ClassCastException incompatible) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (itemHandler == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (machine == null || !machine.interact(player) || Transaction.getCurrentOpenedTransaction() != null) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            if (machine.kind() == MachineKind.TERMINAL && tryDepositChemical(level, pos, player, machine, itemHandler, resources, mappings)) {
                return InteractionResult.SUCCESS;
            }
            tryExtractChemical(level, pos, player, machine, itemHandler, resources, mappings);
            return InteractionResult.SUCCESS;
        }
        tryDepositChemical(level, pos, player, machine, itemHandler, resources, mappings);
        return InteractionResult.SUCCESS;
    }

    private static boolean tryDepositChemical(
            Level level, BlockPos pos, Player player, MachineBlockEntity machine,
            ResourceHandler<ChemicalResource> itemHandler,
            List<ChemicalResource> resources, List<ChemicalMappings.Mapping> mappings) {
        for (int slot = 0; slot < itemHandler.size(); slot++) {
            ChemicalResource stored = itemHandler.getResource(slot);
            if (stored.isEmpty() || stored.isRadioactive()) continue;
            int mappingIndex = resources.indexOf(stored);
            if (mappingIndex < 0) continue;
            var mapping = mappings.get(mappingIndex);
            if (!ReMatter.chemistry().isKnown(mapping.matter())) continue;
            long spaceUnits = availableSpace(machine, mapping.matter());
            int maxChem = (int) Math.min(
                    PressurizedCanisterData.CAPACITY / mapping.unitsPerChemical(),
                    spaceUnits / mapping.unitsPerChemical());
            if (maxChem <= 0) continue;
            int simChem;
            try (var sim = Transaction.openRoot()) {
                simChem = itemHandler.extract(slot, stored, maxChem, sim);
            }
            if (simChem <= 0) continue;
            int extractedChem;
            try (var tx = Transaction.openRoot()) {
                extractedChem = itemHandler.extract(slot, stored, simChem, tx);
                if (extractedChem <= 0) continue;
                tx.commit();
            }
            int units = Math.toIntExact(Math.multiplyExact((long) extractedChem, mapping.unitsPerChemical()));
            var deposit = PressurizedCanisterItem.depositWithReceipt(machine, player, mapping.matter(), units);
            int unusedUnits = units - deposit.accepted();
            if (unusedUnits > 0) {
                int refundChem = (int) ((unusedUnits + mapping.unitsPerChemical() - 1) / mapping.unitsPerChemical());
                try (var refund = Transaction.openRoot()) {
                    itemHandler.insert(slot, stored, refundChem, refund);
                    refund.commit();
                }
            }
            if (deposit.accepted() > 0) {
                deposit.show(player, mapping.matter());
                ModSounds.play(level, pos, ModSounds.CANISTER_EMPTY);
                return true;
            }
        }
        return false;
    }

    private static boolean tryExtractChemical(
            Level level, BlockPos pos, Player player, MachineBlockEntity machine,
            ResourceHandler<ChemicalResource> itemHandler,
            List<ChemicalResource> resources, List<ChemicalMappings.Mapping> mappings) {
        for (int m = 0; m < mappings.size(); m++) {
            var mapping = mappings.get(m);
            ChemicalResource chem = resources.get(m);
            if (chem.isRadioactive()
                    || !ReMatter.chemistry().isKnown(mapping.matter())
                    || !ChemicalMappings.exportable(ReMatter.chemistry().synthesisPolicy(), mapping)) {
                continue;
            }
            long stockUnits = availableStock(machine, mapping.matter());
            int maxChem = (int) Math.min(
                    PressurizedCanisterData.CAPACITY / mapping.unitsPerChemical(),
                    stockUnits / mapping.unitsPerChemical());
            if (maxChem <= 0) continue;
            for (int slot = 0; slot < itemHandler.size(); slot++) {
                int simChem;
                try (var sim = Transaction.openRoot()) {
                    simChem = itemHandler.insert(slot, chem, maxChem, sim);
                }
                if (simChem <= 0) continue;
                int unitsToExtract = Math.toIntExact(Math.multiplyExact((long) simChem, mapping.unitsPerChemical()));
                var extracted = PressurizedCanisterItem.extractFromMachine(machine, player, mapping.matter(), unitsToExtract);
                if (extracted.isEmpty()) continue;
                int chemToInsert = (int) (extracted.get().amount() / mapping.unitsPerChemical());
                int insertedChem = 0;
                if (chemToInsert > 0) {
                    try (var tx = Transaction.openRoot()) {
                        insertedChem = itemHandler.insert(slot, chem, chemToInsert, tx);
                        tx.commit();
                    }
                }
                int usedUnits = Math.toIntExact(Math.multiplyExact((long) insertedChem, mapping.unitsPerChemical()));
                int leftoverUnits = extracted.get().amount() - usedUnits;
                if (leftoverUnits > 0) {
                    PressurizedCanisterItem.depositIntoMachine(machine, player, mapping.matter(), leftoverUnits);
                }
                if (usedUnits > 0) {
                    PressurizedCanisterItem.notifyExtraction(level, pos, player, mapping.matter(), usedUnits);
                    return true;
                }
            }
        }
        return false;
    }

    private static long availableSpace(MachineBlockEntity machine, SubstanceKey key) {
        return PressurizedCanisterItem.availableSpace(machine, key);
    }

    private static long availableStock(MachineBlockEntity machine, SubstanceKey key) {
        return PressurizedCanisterItem.availableStock(machine, key);
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
