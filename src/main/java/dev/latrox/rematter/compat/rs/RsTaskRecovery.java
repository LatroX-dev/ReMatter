package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.autocrafting.task.TaskImpl;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskSnapshot;
import com.refinedmods.refinedstorage.api.autocrafting.task.Task;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceStack;
import dev.latrox.rematter.content.MatterRecoveryItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;

/** Destruction recovery reads only resources that the native task currently owns. */
public final class RsTaskRecovery {
    private static final Set<Class<?>> WARNED_TASK_TYPES = ConcurrentHashMap.newKeySet();
    private RsTaskRecovery() {}

    public static List<ItemStack> drops(AutocrafterBlockEntity machine) {
        var nativeTasks = new ArrayList<Task>();
        var nodes = Collections.newSetFromMap(new IdentityHashMap<PatternProviderNetworkNode, Boolean>());
        for (var container : machine.getContainerProvider().getContainers()) {
            if (!(container.getNode() instanceof PatternProviderNetworkNode node) || !nodes.add(node)) continue;
            nativeTasks.addAll(node.getTasks());
        }
        // Prepare everything before spawning anything. Quantities in different tasks need not fit one long sum.
        return ownedMatterTasks(nativeTasks).stream().map(stack -> MatterRecoveryItem.create(stack.key(), stack.quantity())).toList();
    }

    static List<SubstanceStack> ownedMatterTasks(Iterable<? extends Task> nativeTasks) {
        var quantities = new ArrayList<SubstanceStack>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Task, Boolean>());
        for (var task : nativeTasks) {
            if (task == null || !seen.add(task)) continue;
            if (task instanceof TaskImpl nativeTask) quantities.addAll(ownedMatter(nativeTask.createSnapshot()));
            else if (WARNED_TASK_TYPES.add(task.getClass()))
                ReMatter.LOGGER.warn("Cannot inspect stored matter in unsupported RS task type {}", task.getClass().getName());
        }
        return List.copyOf(quantities);
    }

    static List<SubstanceStack> ownedMatter(TaskSnapshot snapshot) {
        var result = new ArrayList<SubstanceStack>();
        for (var entry : snapshot.internalStorage().copyState()) {
            if (entry == null) throw new IllegalArgumentException("Missing native task resource");
            if (entry.resource() instanceof RsMatterKey key) {
                if (entry.amount() <= 0) throw new IllegalArgumentException("Invalid native matter quantity");
                result.add(new SubstanceStack(key.substance(), entry.amount()));
            }
        }
        return List.copyOf(result);
    }
}
