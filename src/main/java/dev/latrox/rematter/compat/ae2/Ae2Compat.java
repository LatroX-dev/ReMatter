package dev.latrox.rematter.compat.ae2;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.AECapabilities;
import dev.latrox.rematter.automation.AutomationBackends;
import dev.latrox.rematter.content.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Loaded only after the optional AE2 dependency has been detected. */
public final class Ae2Compat {
    private Ae2Compat() {}

    public static void register(IEventBus bus) {
        if (AEFluidKey.AMOUNT_BUCKET != 1_000)
            throw new IllegalStateException("Unsupported AE2 fluid unit scale");
        bus.addListener(Ae2Compat::registerKey);
        bus.addListener(Ae2Compat::commonSetup);
        bus.addListener(Ae2Compat::registerCapabilities);
        AutomationBackends.register("ae2", Ae2Bridge::new);
        if (FMLEnvironment.getDist() == Dist.CLIENT) Ae2Client.register(bus);
    }

    private static void registerKey(RegisterEvent event) {
        if (event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) AEKeyTypes.register(MatterKey.TYPE);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> PatternDetailsHelper.registerDecoder(new RecipePatterns()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ModContent.MACHINE_ENTITY.get(), (machine, unused) -> {
            var host = machine.automationHost();
            return host != null && host.adapter("ae2") instanceof Ae2Bridge bridge ? bridge : null;
        });
    }
}
