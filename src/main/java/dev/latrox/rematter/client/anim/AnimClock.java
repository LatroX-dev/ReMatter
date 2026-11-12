package dev.latrox.rematter.client.anim;

import dev.latrox.rematter.ReMatter;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Real-time client frames: server tick rate, time correction and a frozen world cannot step the mechanism. */
@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class AnimClock {
    private static final FrameClock CLOCK=new FrameClock();
    private AnimClock() {}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        var client = Minecraft.getInstance();
        CLOCK.commit(event.getPartialTick().getRealtimeDeltaTicks(),client.level==null||client.isPaused());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void endFrame(RenderFrameEvent.Post event){
        CLOCK.endFrame();
    }
    public static double now(float partialTick) {
        var client=Minecraft.getInstance();
        // Game partial ticks freeze at1 or slow with /tick rate; only the real-time delta is suitable here.
        return CLOCK.sample(client.getDeltaTracker().getRealtimeDeltaTicks(),client.level==null||client.isPaused());
    }
}
