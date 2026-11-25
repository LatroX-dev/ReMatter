package dev.latrox.rematter;

import com.mojang.logging.LogUtils;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.compat.mekanism.MekanismCompat;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.data.ChemistryData;
import dev.latrox.rematter.network.CatalogSync;
import dev.latrox.rematter.network.MatterCablePulsePayload;
import dev.latrox.rematter.network.ReleaseHeldOrderPayload;
import dev.latrox.rematter.network.SelectProcessPayload;
import dev.latrox.rematter.network.StockQueryPayload;
import dev.latrox.rematter.network.TerminalDeviceActionPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ReMatter.MOD_ID)
public final class ReMatter {
    public static final String MOD_ID = "rematter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReMatter(IEventBus modBus, ModContainer container) {
        dev.latrox.rematter.research.ResearchConfig.register(container);
        dev.latrox.rematter.chemistry.SynthesisConfig.register(container);
        dev.latrox.rematter.content.ChunkLoadingConfig.register(container);
        ModContent.register(modBus);
        dev.latrox.rematter.primal.PrimalContent.register(modBus);
        modBus.addListener(dev.latrox.rematter.content.ChunkAnchorManager::registerTicketController);
        modBus.addListener(dev.latrox.rematter.network.MachineActionPayload::register);
        modBus.addListener(dev.latrox.rematter.network.ResearchKnowledgePayload::register);
        modBus.addListener(dev.latrox.rematter.network.ResearchLearnedPayload::register);
        modBus.addListener(CatalogSync::register);
        modBus.addListener(dev.latrox.rematter.network.CrystalFuelSync::register);
        modBus.addListener(dev.latrox.rematter.network.EnergyCubePayload::register);
        modBus.addListener(dev.latrox.rematter.network.CellTransferConfigPayload::register);
        modBus.addListener(dev.latrox.rematter.network.MatterLevelEmitterPayload::register);
        modBus.addListener(dev.latrox.rematter.network.MatterCableRelayPayload::register);
        modBus.addListener(dev.latrox.rematter.network.SpatialAnchorPayload::register);
        modBus.addListener(dev.latrox.rematter.network.ChunkMapPayload::register);
        modBus.addListener(dev.latrox.rematter.network.EntangledLinkPayload::register);
        modBus.addListener(dev.latrox.rematter.network.DimensionalLinkPayload::register);
        modBus.addListener(dev.latrox.rematter.network.WirelessAccessPointPayload::register);
        modBus.addListener(dev.latrox.rematter.network.NetworkReservesPayload::register);
        modBus.addListener(MatterCablePulsePayload::register);
        modBus.addListener(ReleaseHeldOrderPayload::register);
        modBus.addListener(SelectProcessPayload::register);
        modBus.addListener(StockQueryPayload::register);
        modBus.addListener(dev.latrox.rematter.network.MachineConfigurationPayload::register);
        modBus.addListener(dev.latrox.rematter.network.StructureHintPayload::register);
        modBus.addListener(TerminalDeviceActionPayload::register);
        modBus.addListener(TerminalDevicesPayload::register);
        modBus.addListener(dev.latrox.rematter.network.StockFavoritesSyncPayload::register);
        modBus.addListener(dev.latrox.rematter.network.StockFavoriteTogglePayload::register);
        modBus.addListener(dev.latrox.rematter.network.TerminalCanisterActionPayload::register);
        modBus.addListener(dev.latrox.rematter.network.WrenchDiagnosticPayload::register);
        modBus.addListener(dev.latrox.rematter.network.WrenchDiagnosticQueryPayload::register);
        modBus.addListener(dev.latrox.rematter.network.TerminalMonitorPayload::register);
        modBus.addListener(dev.latrox.rematter.network.TerminalBatchRequestPayload::register);
        modBus.addListener(dev.latrox.rematter.network.TerminalBatchPlanPayload::register);
        modBus.addListener(dev.latrox.rematter.network.OpenWirelessTerminalPayload::register);
        NeoForge.EVENT_BUS.addListener(dev.latrox.rematter.content.generation.CrystalFuelData::register);
        NeoForge.EVENT_BUS.addListener(ChemistryData::register);
        NeoForge.EVENT_BUS.addListener(CatalogSync::sync);
        NeoForge.EVENT_BUS.addListener(dev.latrox.rematter.network.CrystalFuelSync::sync);
        NeoForge.EVENT_BUS.addListener(dev.latrox.rematter.network.NetworkReservesPayload::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(dev.latrox.rematter.content.ChunkAnchorManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(dev.latrox.rematter.content.ChunkAnchorManager::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(dev.latrox.rematter.content.ChunkAnchorManager::onPlayerLogout);
        if (ModList.get().isLoaded("ae2")) dev.latrox.rematter.compat.ae2.Ae2Compat.register(modBus);
        if (ModList.get().isLoaded("refinedstorage")) dev.latrox.rematter.compat.rs.RsCompat.register(modBus);
        dev.latrox.rematter.compat.CuriosSupport.register(modBus);
        if (ModList.get().isLoaded("mekanism")) {
            try {
                MekanismCompat.register(modBus);
            } catch (LinkageError incompatibleApi) {
                LOGGER.warn("Mekanism chemical integration is unavailable for this API version", incompatibleApi);
            }
        }
    }

    public static ChemistryRegistry chemistry() {
        return ChemistryData.current();
    }
}
