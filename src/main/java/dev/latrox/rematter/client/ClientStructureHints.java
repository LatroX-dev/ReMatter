package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.network.StructureHintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

/** A bounded outline persists briefly after closing the controller so players can locate the required block. */
@EventBusSubscriber(modid=ReMatter.MOD_ID,value=Dist.CLIENT)
public final class ClientStructureHints {
    private record RenderHint(AABB box) {}
    private static final ContextKey<RenderHint> KEY=new ContextKey<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"construction_hint"));
    private static StructureHintPayload hint=StructureHintPayload.EMPTY;
    private static ClientLevel level;
    private static long expires;
    private ClientStructureHints() {}
    public static void accept(StructureHintPayload value) {
        hint=value;level=Minecraft.getInstance().level;expires=level==null?0:level.getGameTime()+100;
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        hint=StructureHintPayload.EMPTY;level=null;expires=0;
    }
    @SubscribeEvent public static void extract(ExtractLevelRenderStateEvent event) {
        AABB box=null;
        if(hint.present()&&level==event.getLevel()&&level.getGameTime()<=expires
                &&hint.position().getCenter().distanceToSqr(event.getCamera().position())<128*128) {
            var camera=event.getCamera().position();box=new AABB(hint.position()).inflate(.006).move(-camera.x,-camera.y,-camera.z);
        }
        event.getRenderState().setRenderData(KEY,new RenderHint(box));
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event) {
        var extracted=event.getLevelRenderState().getRenderData(KEY);
        if(extracted==null||extracted.box()==null)return;
        var box=extracted.box();
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.lines(),(pose,vertices)-> {
            for(int axis=0;axis<3;axis++)for(int first=0;first<2;first++)for(int second=0;second<2;second++) {
                float[] a={(float)box.minX,(float)box.minY,(float)box.minZ};
                float[] b={(float)box.minX,(float)box.minY,(float)box.minZ};
                float[] high={(float)box.maxX,(float)box.maxY,(float)box.maxZ};
                int u=(axis+1)%3,v=(axis+2)%3;
                if(first==1)a[u]=b[u]=high[u];if(second==1)a[v]=b[v]=high[v];b[axis]=high[axis];
                var normal=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]).normalize();
                vertices.addVertex(pose,a[0],a[1],a[2]).setColor(UiPalette.STRUCTURE_HINT).setNormal(pose,normal).setLineWidth(2);
                vertices.addVertex(pose,b[0],b[1],b[2]).setColor(UiPalette.STRUCTURE_HINT).setNormal(pose,normal).setLineWidth(2);
            }
        });
    }
}
