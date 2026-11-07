package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.external.ExternalStorage;
import com.refinedmods.refinedstorage.api.storage.external.ExternalStorageProvider;
import dev.latrox.rematter.automation.AutomationPort;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;

/** RS's actual cache-aware external-storage wrapper, backed by the one authoritative leased port. */
public final class RsMatterStorage implements ExternalStorageProvider {
    private final AutomationPort port;
    private final Predicate<Actor> permitted;
    private final ExternalStorage storage;
    /** The listing feeds a network-wide RS cache, so only transfers can be checked per actor. */
    public RsMatterStorage(AutomationPort port, Predicate<Actor> permitted) {
        this.port = port;
        this.permitted = permitted;
        this.storage = new ExternalStorage(this, (resource, actor) -> {});
        refresh();
    }
    public ExternalStorage storage() { return storage; }
    /** Host calls on every external inventory change and before lease handoff; ticks also refresh. */
    public void refresh() { storage.detectChanges(); }
    @Override public Iterator<ResourceAmount> iterator() {
        if (!port.active()) return Collections.emptyIterator();
        return port.availableEntries().entrySet().stream().filter(entry -> entry.getValue() > 0)
                .map(entry -> new ResourceAmount(new RsMatterKey(entry.getKey()), entry.getValue())).iterator();
    }
    @Override public long insert(ResourceKey resource, long amount, Action action, Actor actor) {
        if (!port.active() || amount <= 0 || !(resource instanceof RsMatterKey key) || !permitted.test(actor)) return 0;
        return port.insert(key.substance(), amount, translate(action));
    }
    @Override public long extract(ResourceKey resource, long amount, Action action, Actor actor) {
        if (!port.active() || amount <= 0 || !(resource instanceof RsMatterKey key) || !permitted.test(actor)) return 0;
        return port.extract(key.substance(), amount, translate(action));
    }
    static AutomationPort.Action translate(Action action) {
        return action == Action.EXECUTE ? AutomationPort.Action.EXECUTE : AutomationPort.Action.SIMULATE;
    }
}
