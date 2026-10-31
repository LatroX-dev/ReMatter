package dev.latrox.rematter;

import com.mojang.logging.LogUtils;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.compat.mekanism.MekanismCompat;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.data.ChemistryData;
import dev.latrox.rematter.network.CatalogSync;
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
        ModContent.register(modBus);
        modBus.addListener(CatalogSync::register);
        NeoForge.EVENT_BUS.addListener(ChemistryData::register);
        NeoForge.EVENT_BUS.addListener(CatalogSync::sync);
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
