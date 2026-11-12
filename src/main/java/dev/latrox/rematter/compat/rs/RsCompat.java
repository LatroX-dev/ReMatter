package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import dev.latrox.rematter.automation.AutomationBackends;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.MachinePortMode;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Loaded only when Refined Storage is installed. */
public final class RsCompat {
    private RsCompat() {}
    public static void register(IEventBus bus) {
        AutomationBackends.register("rs",RsAdapter::new);
        if(net.neoforged.fml.loading.FMLEnvironment.getDist()==net.neoforged.api.distmarker.Dist.CLIENT) bus.addListener(RsClient::setup);
        bus.addListener(RsCompat::setup);bus.addListener(RsCompat::capabilities);
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(()->{
            var id=Identifier.parse("rematter:matter");
            RefinedStorageApi.INSTANCE.getResourceTypeRegistry().register(id,RsMatterKey.TYPE);
            RefinedStorageApi.INSTANCE.getGridResourceTypeRegistry().register(id,RsMatterGrid.TYPE);
        });
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),ModContent.MACHINE_ENTITY.get(),
                (machine,side)->{
                    if(side==null||!machine.allowsPort(side,MachinePortMode.MATTER))return null;
                    var host=machine.automationHost();return host!=null&&host.adapter("rs") instanceof RsAdapter rs?rs.provider():null;
                });
    }
}
