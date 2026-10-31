package dev.latrox.rematter;

import com.mojang.logging.LogUtils;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.data.ChemistryData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ReMatter.MOD_ID)
public final class ReMatter {
    public static final String MOD_ID = "rematter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReMatter(IEventBus modBus, ModContainer container) {
        ModContent.register(modBus);
        NeoForge.EVENT_BUS.addListener(ChemistryData::register);
    }

    public static ChemistryRegistry chemistry() {
        return ChemistryData.current();
    }
}
