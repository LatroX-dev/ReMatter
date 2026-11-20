package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.NetworkComponent;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.ConnectionSink;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RsAdapterLifecycleTest {
    @Test void hostTicksNativeWorkOnceAndNativeEnergyLimitsActiveness() {
        var ticker=new RsAdapter.NativeTicker();var energy=new Energy(8);var node=new WorkingNode();
        node.setNetwork(network(energy));node.callback=()->ticker.tick(100,node,true,true);
        ticker.tick(100,node,true,true);ticker.tick(100,node,true,true);
        assertTrue(node.isActive());assertEquals(1,node.work);assertEquals(4,energy.stored);
        node.callback=()->{};ticker.tick(101,node,true,true);
        assertEquals(2,node.work);assertEquals(0,energy.stored);
        ticker.tick(102,node,true,true);
        assertFalse(node.isActive());assertEquals(2,node.work);assertEquals(0,energy.stored);
        energy.stored=8;ticker.tick(103,node,false,true);
        assertFalse(node.isActive());assertEquals(8,energy.stored);assertEquals(2,node.work);
        node.setNetwork(null);ticker.tick(104,node,true,false);
        assertFalse(node.isActive());assertEquals(2,node.work);
    }
    @Test void nativeEnergyDisabledConfigurationDoesNotCreateEnergy() {
        var ticker=new RsAdapter.NativeTicker();var energy=new Energy(0);var node=new WorkingNode();
        node.setNetwork(network(energy));ticker.tick(1,node,true,false);
        assertTrue(node.isActive());assertEquals(1,node.work);assertEquals(0,energy.stored);
        assertEquals(4,energy.requested);
    }
    @Test void revocationCancelsTheNativeQueuedContainerWithoutRemovingItsReplacement() {
        var hostRemoved=new AtomicBoolean();var oldClosed=new AtomicBoolean();
        var oldNative=new Container(hostRemoved);var replacementNative=new Container(hostRemoved);
        var old=RsAdapter.whileOpen(oldNative,oldClosed::get);
        var replacement=RsAdapter.whileOpen(replacementNative,()->false);
        assertFalse(old.isRemoved());assertFalse(replacement.isRemoved());
        oldClosed.set(true);
        // Refined Storage's deferred initialization checks the container's isRemoved before joining a grid.
        assertTrue(old.isRemoved());assertFalse(hostRemoved.get());assertFalse(replacement.isRemoved());
        assertSame(oldNative.node,old.getNode());assertSame(replacementNative.node,replacement.getNode());
        assertNotSame(old.getNode(),replacement.getNode());
        hostRemoved.set(true);assertTrue(replacement.isRemoved());
    }

    @Test void wrapperPreservesNativeConnectionRulesAndContainerIdentityData() {
        var nativeContainer=new Container(new AtomicBoolean());
        var wrapper=RsAdapter.whileOpen(nativeContainer,()->false);
        assertSame(nativeContainer.key,wrapper.createKey());assertEquals(17,wrapper.getPriority());
        assertEquals("native",wrapper.getName());assertSame(nativeContainer.node,wrapper.getNode());
        assertFalse(wrapper.canAcceptIncomingConnection(Direction.NORTH,null));
        assertTrue(wrapper.canAcceptIncomingConnection(Direction.SOUTH,null));
        wrapper.addOutgoingConnections(null);assertEquals(1,nativeContainer.outgoingCalls);
    }

    @Test void nativeCablesConnectOnlyOnFacesOpenForMatter() {
        var open=java.util.EnumSet.of(Direction.NORTH,Direction.UP);
        var strategy=RsAdapter.faces(BlockPos.ZERO,open::contains);
        var targets=new java.util.ArrayList<String>();
        strategy.addOutgoingConnections(new ConnectionSink() {
            public void tryConnect(GlobalPos position,Class<? extends net.minecraft.world.level.block.Block> type){fail("Cross-dimension link");}
            public void tryConnectInSameDimension(BlockPos position,Direction incoming,Class<? extends net.minecraft.world.level.block.Block> type){
                targets.add(position.toShortString()+"/"+incoming);
            }
        });
        assertEquals(java.util.List.of("0, 1, 0/down","0, 0, -1/south"),targets);
        assertTrue(strategy.canAcceptIncomingConnection(Direction.NORTH,null));
        assertFalse(strategy.canAcceptIncomingConnection(Direction.SOUTH,null));
        open.remove(Direction.NORTH);
        assertFalse(strategy.canAcceptIncomingConnection(Direction.NORTH,null),"A face closed later stops accepting cables");
    }

    @Test void onlyPlayersWhoMayUseTheInterfaceCanBuildNextToIt() {
        var asked=new AtomicBoolean();
        var nativeProvider=new com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider() {
            public Set<InWorldNetworkNodeContainer> getContainers(){return Set.of();}
            public void addContainer(InWorldNetworkNodeContainer container){}
            public boolean canBuild(net.minecraft.server.level.ServerPlayer player){asked.set(true);return true;}
        };
        assertFalse(RsAdapter.ownerBuilt(nativeProvider,player->false).canBuild(null));
        assertFalse(asked.get(),"A foreign player is refused before RS security is consulted");
        assertTrue(RsAdapter.ownerBuilt(nativeProvider,player->true).canBuild(null));
        assertTrue(asked.get());
    }

    private static final class Container implements InWorldNetworkNodeContainer {
        final AtomicBoolean removed;final Object key=new Object();int outgoingCalls;
        final NetworkNode node=new NetworkNode(){
            private Network network;
            public Network getNetwork(){return network;}
            public void setNetwork(Network value){network=value;}
        };
        Container(AtomicBoolean removed){this.removed=removed;}
        public NetworkNode getNode(){return node;}
        public boolean isRemoved(){return removed.get();}
        public Object createKey(){return key;}
        public int getPriority(){return 17;}
        public String getName(){return "native";}
        public GlobalPos getPosition(){return null;}
        public BlockPos getLocalPosition(){return null;}
        public BlockState getBlockState(){return null;}
        public void addOutgoingConnections(ConnectionSink sink){outgoingCalls++;}
        public boolean canAcceptIncomingConnection(Direction side,BlockState state){return side==Direction.SOUTH;}
    }
    private static final class WorkingNode extends AbstractNetworkNode {
        int work;Runnable callback=()->{};
        public long getEnergyUsage(){return 4;}
        @Override public void doWork(){super.doWork();if(isActive()&&getNetwork()!=null){work++;callback.run();}}
    }
    private static final class Energy implements EnergyNetworkComponent {
        long stored,requested;
        Energy(long stored){this.stored=stored;}
        public long getStored(){return stored;}
        public long getCapacity(){return 8;}
        public long extract(long amount){requested+=amount;long taken=Math.min(stored,amount);stored-=taken;return taken;}
    }
    private static Network network(Energy energy) {
        return new Network(){
            public <I extends NetworkComponent>I getComponent(Class<I> type){return type.cast(energy);}
            public void addContainer(NetworkNodeContainer c){}
            public void removeContainer(NetworkNodeContainer c){}
            public void remove(){}
            public void split(Set<Network> networks){}
            public void merge(Network network){}
        };
    }
}
