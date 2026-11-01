package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskSnapshot;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskState;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskImpl;
import com.refinedmods.refinedstorage.api.autocrafting.task.Task;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceListImpl;
import com.refinedmods.refinedstorage.api.resource.list.ResourceList;
import com.refinedmods.refinedstorage.api.storage.Actor;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SubstanceStack;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RsTaskRecoveryTest {
    private record OtherResource(String id) implements ResourceKey {}
    private static final RsMatterKey GAS = new RsMatterKey(SubstanceKey.of("rematter:water", Phase.GAS));
    private static final RsMatterKey LIQUID = new RsMatterKey(SubstanceKey.of("rematter:water", Phase.LIQUID));

    @Test void onlyTheActualInternalMatterIsRecoverable() {
        var requested = MutableResourceListImpl.orderPreserving();
        requested.add(GAS, 900);
        var owned = MutableResourceListImpl.orderPreserving();
        owned.add(GAS, 12);
        owned.add(LIQUID, 8);
        owned.add(new OtherResource("minecraft:iron_ingot"), 64);
        var recovered = RsTaskRecovery.ownedMatter(snapshot(requested, owned));
        assertEquals(List.of(new SubstanceStack(GAS.substance(), 12), new SubstanceStack(LIQUID.substance(), 8)), recovered);
        assertEquals(12, owned.get(GAS));
        assertEquals(900, requested.get(GAS));
    }

    @Test void initialRequirementsNeverBecomeRecoveredResources() {
        var requested = MutableResourceListImpl.orderPreserving();
        requested.add(GAS, 1_000);
        assertTrue(RsTaskRecovery.ownedMatter(snapshot(requested, MutableResourceListImpl.orderPreserving())).isEmpty());
    }

    @Test void eachTaskRetainsItsExactLongQuantityWithoutAnAggregateOverflow() {
        var owned = MutableResourceListImpl.orderPreserving();
        owned.add(GAS, Long.MAX_VALUE);
        var first = new TaskImpl(snapshot(MutableResourceListImpl.orderPreserving(), owned));
        var second = new TaskImpl(snapshot(MutableResourceListImpl.orderPreserving(), owned));
        var result = RsTaskRecovery.ownedMatterTasks(List.of(first, second));
        assertEquals(2, result.size());
        assertEquals(Long.MAX_VALUE, result.getFirst().quantity());
        assertEquals(Long.MAX_VALUE, result.getLast().quantity());
    }

    @Test void duplicateTaskReferencesAndUnsupportedTasksDoNotDuplicateOrSuppressKnownResources() {
        var owned = MutableResourceListImpl.orderPreserving();
        owned.add(GAS, 42);
        var known = new TaskImpl(snapshot(MutableResourceListImpl.orderPreserving(), owned));
        var unsupported = (Task) java.lang.reflect.Proxy.newProxyInstance(Task.class.getClassLoader(), new Class<?>[] { Task.class },
                (proxy, method, arguments) -> { throw new AssertionError("An unsupported task must not be queried"); });
        assertEquals(List.of(new SubstanceStack(GAS.substance(), 42)),
                RsTaskRecovery.ownedMatterTasks(List.of(known, unsupported, known, unsupported)));
    }

    private static TaskSnapshot snapshot(ResourceList requested, ResourceList owned) {
        return new TaskSnapshot(TaskId.create(), GAS, 1, Actor.EMPTY, false, 0, Map.of(), List.of(),
                requested, owned, TaskState.RUNNING, false);
    }
}
