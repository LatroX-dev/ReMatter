package dev.latrox.rematter.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.AutomationAdapter;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachinePortMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Native AE2 storage and crafting services backed by one authoritative automation host. */
public final class Ae2Bridge implements AutomationAdapter, IStorageProvider, ICraftingProvider, IInWorldGridNodeHost, IActionHost {
    private final MachineBlockEntity host;
    private final AutomationPort port;
    private final IManagedGridNode node;
    private List<AutomationPort.Recipe> catalogue = List.of();
    private List<IPatternDetails> patterns = List.of();
    private Map<AEItemKey, RecipePatterns.Pattern> definitions = Map.of();
    private Map<SubstanceKey, Long> lastStock = Map.of();
    private boolean lastActive;
    private boolean refreshRequested = true;
    private boolean delivering;
    private boolean created;
    private boolean closed;
    private boolean inspectingNeighbour;
    private Set<Direction> exposed = Set.of();

    public Ae2Bridge(MachineBlockEntity host, AutomationPort port) {
        this.host = Objects.requireNonNull(host);
        this.port = Objects.requireNonNull(port);
        node = GridHelper.createManagedNode(this, new IGridNodeListener<Ae2Bridge>() {
            @Override public void onSaveChanges(Ae2Bridge bridge, IGridNode changed) { bridge.port.changed(); }
            @Override public void onGridChanged(Ae2Bridge bridge, IGridNode changed) { bridge.refreshRequested = true; }
            @Override public void onStateChanged(Ae2Bridge bridge, IGridNode changed, State reason) { bridge.refreshRequested = true; }
        }).setInWorldNode(true).setTagName("ae2_node").setIdlePowerUsage(2).setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(IStorageProvider.class, this).addService(ICraftingProvider.class, this);
    }

    public void create(ServerLevel level, BlockPos position) {
        if (created || closed) throw new IllegalStateException("AE2 node lifecycle already started");
        Objects.requireNonNull(port.ownerId(), "An automation bridge must have an owner");
        node.setOwningPlayerId(IPlayerRegistry.getMapping(level).getPlayerId(port.ownerId()));
        exposed = openFaces();
        node.setExposedOnSides(exposed);
        node.create(level, position);
        created = true;
        refreshRequested = true;
    }
    /** Destroying a grid node never consumes, refunds or clears the host's persisted orders. */
    public void destroy() {
        if (created) node.destroy();
        created = false;
        closed = true;
        lastActive = false;
        refreshRequested = true;
    }
    @Override public String id() { return "ae2"; }
    @Override public void close() { destroy(); }
    @Override public boolean connected() { return created && !closed && node.isActive(); }
    @Override public void load(ValueInput input) { node.deserialize(input); }
    @Override public void save(ValueOutput output) { node.serialize(output); }
    @Override public IGridNode getGridNode(Direction side) { return side != null && foreignNeighbour(side) ? null : node.getNode(); }
    @Override public IGridNode getActionableNode() { return node.getNode(); }

    /** Run after the host advances its workers; never call this from inside pushPattern. */
    @Override public void tick() {
        if (closed || host.isRemoved() || !(host.getLevel() instanceof ServerLevel level)
                || level.getBlockEntity(host.getBlockPos()) != host) return;
        if (!created) {
            if (port.ownerId() == null) return;
            create(level, host.getBlockPos());
        }
        var faces = openFaces();
        if (!faces.equals(exposed)) {
            exposed = faces;
            node.setExposedOnSides(faces);
        }
        boolean active = node.isActive() && port.active();
        var recipes = active ? port.recipes() : List.<AutomationPort.Recipe>of();
        if (recipes.size() > AutomationRecipeCatalogue.MAX_RECIPES)
            throw new IllegalStateException("Automation catalogue exceeds its supported size");
        if (!recipes.equals(catalogue)) {
            catalogue = List.copyOf(recipes);
            var next = new ArrayList<IPatternDetails>(catalogue.size());
            var nextDefinitions = new HashMap<AEItemKey, RecipePatterns.Pattern>();
            for (var recipe : catalogue) {
                var pattern = RecipePatterns.create(recipe);
                next.add(pattern);
                nextDefinitions.put(pattern.getDefinition(), pattern);
            }
            patterns = List.copyOf(next);
            definitions = Map.copyOf(nextDefinitions);
            refreshRequested = true;
        }
        if (lastActive != active) { lastActive = active; refreshRequested = true; }
        if (refreshRequested && node.getNode() != null) {
            refreshRequested = false;
            IStorageProvider.requestUpdate(node);
            ICraftingProvider.requestUpdate(node);
            node.ifPresent(grid -> grid.getStorageService().invalidateCache());
        }
        var stock = active ? port.availableEntries() : Map.<SubstanceKey, Long>of();
        if (!stock.equals(lastStock)) {
            lastStock = Map.copyOf(stock);
            node.ifPresent(grid -> grid.getStorageService().invalidateCache());
        }
        if (active) deliverHeldResources();
    }

    /** Native cables connect only on faces that the owner leaves open for matter connections. */
    private Set<Direction> openFaces() {
        var faces = EnumSet.noneOf(Direction.class);
        for (var side : Direction.values()) if (host.allowsPort(side, MachinePortMode.MATTER) && !foreignNeighbour(side)) faces.add(side);
        return Collections.unmodifiableSet(faces);
    }
    /** AE2 has no security service, so a node that another player placed against the interface never joins the owner's grid. */
    private boolean foreignNeighbour(Direction side) {
        // Two adjacent interfaces ask each other; the inner question sees the plain node.
        if (inspectingNeighbour || !(host.getLevel() instanceof ServerLevel level)) return false;
        var neighbour = host.getBlockPos().relative(side);
        if (!level.isLoaded(neighbour)) return false;
        inspectingNeighbour = true;
        try {
            var other = GridHelper.getExposedNode(level, neighbour, side.getOpposite());
            return other != null && !Objects.equals(port.ownerId(), other.getOwningPlayerProfileId());
        } finally { inspectingNeighbour = false; }
    }

    private boolean allowed(IActionSource source) {
        if (!node.isActive() || !port.active()) return false;
        if (source.player().isPresent() && !source.player().get().getUUID().equals(port.ownerId())) return false;
        if (source.machine().isPresent()) {
            var actor = source.machine().get().getActionableNode();
            if (actor == null || actor.getGrid() != node.getGrid() || !port.ownerId().equals(actor.getOwningPlayerProfileId())) return false;
        }
        return source.player().isPresent() || source.machine().isPresent();
    }

    private final MEStorage stock = new MEStorage() {
        @Override public Component getDescription() {
            return Component.translatable("integration.rematter.storage");
        }
        @Override public long insert(AEKey key, long amount, Actionable action, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, action, source);
            return key instanceof MatterKey matter && allowed(source) ? port.insert(matter.substance(), amount, mode(action)) : 0;
        }
        @Override public long extract(AEKey key, long amount, Actionable action, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, action, source);
            return key instanceof MatterKey matter && allowed(source) ? port.extract(matter.substance(), amount, mode(action)) : 0;
        }
        @Override public void getAvailableStacks(KeyCounter output) {
            if (node.isActive() && port.active()) port.availableEntries().forEach((key, amount) -> output.add(MatterKey.of(key), amount));
        }
    };
    @Override public void mountInventories(IStorageMounts mounts) {
        if (node.isActive() && port.active()) mounts.mount(stock);
    }
    @Override public List<IPatternDetails> getAvailablePatterns() { return node.isActive() && port.active() ? patterns : List.of(); }
    @Override public boolean isBusy() { return !node.isActive() || !port.active() || port.busy(); }

    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] transferred) {
        if (isBusy() || !(pattern instanceof RecipePatterns.Pattern encoded)) return false;
        var current = definitions.get(pattern.getDefinition());
        if (current == null || !current.recipe().equals(encoded.recipe())) return false;
        var expected = current.getInputs();
        if (transferred.length != expected.length) return false;
        var vector = new TreeMap<AutomationPort.Resource, Long>();
        try {
            for (int index = 0; index < transferred.length; index++) {
                if (transferred[index] == null) return false;
                for (var entry : transferred[index]) {
                    if (entry.getLongValue() <= 0 || !expected[index].getPossibleInputs()[0].what().equals(entry.getKey())) return false;
                    vector.merge(RecipePatterns.resource(entry.getKey()), entry.getLongValue(), Math::addExact);
                }
            }
            var recipe = current.recipe();
            if (!vector.equals(recipe.inputs())) return false;
            return port.acceptTransferred(recipe.id(), recipe.revision(), 1, vector, AutomationPort.Action.EXECUTE);
        } catch (IllegalArgumentException | ArithmeticException invalid) { return false; }
    }

    private void deliverHeldResources() {
        if (delivering) return;
        delivering = true;
        try {
            var network = node.getGrid().getStorageService().getInventory();
            for (var delivery : port.pendingOutputs()) if (!deliver(network, delivery, false)) return;
            for (var refund : port.pendingRefunds()) if (!deliver(network, refund, true)) return;
        } finally { delivering = false; }
    }
    /** Returns false when deliveries must stop for this tick. */
    private boolean deliver(MEStorage network, AutomationPort.Delivery delivery, boolean refund) {
        if (!node.isActive() || !port.active()) return false;
        AEKey key;
        // An unregistered item or fluid stays held, unquarantined, until its mod is available again.
        try { key = RecipePatterns.resourceKey(delivery.resource()); }
        catch (RuntimeException unavailable) { return true; }
        boolean started = refund ? port.beginRefund(delivery.orderId(), delivery.resource(), delivery.amount())
                : port.beginDelivery(delivery.orderId(), delivery.resource(), delivery.amount());
        if (!started) return false;
        try {
            long accepted = network.insert(key, delivery.amount(), Actionable.MODULATE, IActionSource.ofMachine(this));
            if (accepted < 0 || accepted > delivery.amount()) throw new IllegalStateException("Invalid output acknowledgement");
            if (refund) port.ackRefund(delivery.orderId(), delivery.resource(), accepted);
            else port.ackOutput(delivery.orderId(), delivery.resource(), accepted);
            return true;
        } catch (RuntimeException uncertain) {
            // An external write may already have happened. Never replay or refund this uncertain delivery.
            port.quarantineDelivery(delivery.orderId(), uncertain.getClass().getName());
            return false;
        }
    }
    private static AutomationPort.Action mode(Actionable action) {
        return action.isSimulate() ? AutomationPort.Action.SIMULATE : AutomationPort.Action.EXECUTE;
    }
}
