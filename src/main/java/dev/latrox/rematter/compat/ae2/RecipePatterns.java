package dev.latrox.rematter.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.ResourceAmounts;
import dev.latrox.rematter.content.ModContent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Self-contained CPU patterns. A provider must still validate the current recipe before accepting work. */
public final class RecipePatterns implements IPatternDetailsDecoder {
    private static final Set<String> FIELDS = Set.of("schema", "recipe", "revision", "worker", "inputs", "outputs", "energy", "ticks");
    private static final Set<String> RESOURCE_FIELDS = Set.of("kind", "id", "phase", "amount");

    public static Pattern create(AutomationPort.Recipe recipe) {
        var stack = new ItemStack(ModContent.AUTOMATION_PATTERN.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(encode(recipe)));
        return new Pattern(recipe, Objects.requireNonNull(AEItemKey.of(stack)));
    }

    @Override public boolean isEncodedPattern(ItemStack stack) {
        return stack.is(ModContent.AUTOMATION_PATTERN.get()) && stack.has(DataComponents.CUSTOM_DATA);
    }

    @Override public IPatternDetails decodePattern(AEItemKey key, Level level) {
        if (!key.is(ModContent.AUTOMATION_PATTERN.get())) return null;
        var data = key.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        try { return new Pattern(decode(data.copyTag()), key); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) { return null; }
    }

    static CompoundTag encode(AutomationPort.Recipe recipe) {
        var data = new CompoundTag();
        data.putInt("schema", 1);
        data.putString("recipe", recipe.id());
        data.putString("revision", recipe.revision());
        data.putString("worker", recipe.workerKind());
        data.put("inputs", encodeAmounts(recipe.inputs()));
        data.put("outputs", encodeAmounts(recipe.outputs()));
        data.putLong("energy", recipe.energy());
        data.putInt("ticks", recipe.ticks());
        return data;
    }

    static AutomationPort.Recipe decode(CompoundTag data) {
        if (!data.keySet().equals(FIELDS) || !(data.get("schema") instanceof IntTag)
                || data.getInt("schema").orElseThrow() != 1 || !(data.get("energy") instanceof LongTag)
                || !(data.get("ticks") instanceof IntTag)) throw new IllegalArgumentException("Unsupported automation pattern");
        return new AutomationPort.Recipe(data.getString("recipe").orElseThrow(), data.getString("revision").orElseThrow(),
                data.getString("worker").orElseThrow(), decodeAmounts(data.getList("inputs").orElseThrow()),
                decodeAmounts(data.getList("outputs").orElseThrow()), data.getLong("energy").orElseThrow(),
                data.getInt("ticks").orElseThrow());
    }

    private static ListTag encodeAmounts(Map<AutomationPort.Resource, Long> amounts) {
        var rows = new ListTag();
        new TreeMap<>(amounts).forEach((resource, amount) -> {
            var row = new CompoundTag();
            row.putString("kind", resource.kind().name());
            row.putString("id", resource.id());
            row.putString("phase", resource.phase());
            row.putLong("amount", amount);
            rows.add(row);
        });
        return rows;
    }

    private static Map<AutomationPort.Resource, Long> decodeAmounts(ListTag rows) {
        if (rows.size() > ResourceAmounts.MAX_RESOURCES) throw new IllegalArgumentException("Too many pattern resources");
        var amounts = new TreeMap<AutomationPort.Resource, Long>();
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.getCompound(index).orElseThrow();
            if (!row.keySet().equals(RESOURCE_FIELDS) || !(row.get("amount") instanceof LongTag))
                throw new IllegalArgumentException("Invalid pattern resource");
            var key = new AutomationPort.Resource(AutomationPort.Kind.valueOf(row.getString("kind").orElseThrow()),
                    row.getString("id").orElseThrow(), row.getString("phase").orElseThrow());
            if (amounts.putIfAbsent(key, row.getLong("amount").orElseThrow()) != null)
                throw new IllegalArgumentException("Duplicate pattern resource");
        }
        return ResourceAmounts.copy(amounts);
    }

    public static AEKey resourceKey(AutomationPort.Resource resource) {
        var id = Identifier.parse(resource.id());
        return switch (resource.kind()) {
            case MATTER -> MatterKey.of(resource.matterKey());
            case ITEM -> {
                if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item in automation pattern");
                yield Objects.requireNonNull(AEItemKey.of(BuiltInRegistries.ITEM.getValue(id)), "Empty item in automation pattern");
            }
            case FLUID -> {
                if (!BuiltInRegistries.FLUID.containsKey(id)) throw new IllegalArgumentException("Unknown fluid in automation pattern");
                yield Objects.requireNonNull(AEFluidKey.of(BuiltInRegistries.FLUID.getValue(id)), "Empty fluid in automation pattern");
            }
        };
    }

    /** Exact comparison to a default native key also rejects additional item/fluid components. */
    public static AutomationPort.Resource resource(AEKey key) {
        if (key instanceof MatterKey matter) return AutomationPort.Resource.matter(matter.substance());
        AutomationPort.Resource resource;
        if (key instanceof AEItemKey item) resource = AutomationPort.Resource.item(item.getId().toString());
        else if (key instanceof AEFluidKey fluid) resource = AutomationPort.Resource.fluid(fluid.getId().toString());
        else throw new IllegalArgumentException("Unsupported automation resource");
        if (!key.equals(resourceKey(resource))) throw new IllegalArgumentException("Component-bearing automation input");
        return resource;
    }

    public static final class Pattern implements IPatternDetails {
        private final AutomationPort.Recipe recipe;
        private final AEItemKey definition;
        private final IInput[] inputs;
        private final List<GenericStack> outputs;
        private Pattern(AutomationPort.Recipe recipe, AEItemKey definition) {
            this.recipe = recipe;
            this.definition = definition;
            this.inputs = recipe.inputs().entrySet().stream()
                    .map(entry -> new Input(new GenericStack(resourceKey(entry.getKey()), entry.getValue())))
                    .toArray(IInput[]::new);
            this.outputs = recipe.outputs().entrySet().stream()
                    .map(entry -> new GenericStack(resourceKey(entry.getKey()), entry.getValue())).toList();
        }
        public AutomationPort.Recipe recipe() { return recipe; }
        @Override public AEItemKey getDefinition() { return definition; }
        @Override public IInput[] getInputs() { return inputs.clone(); }
        @Override public List<GenericStack> getOutputs() { return outputs; }
        @Override public boolean supportsPushInputsToExternalInventory() { return false; }
        @Override public boolean equals(Object other) { return other instanceof Pattern pattern && definition.equals(pattern.definition); }
        @Override public int hashCode() { return definition.hashCode(); }
    }

    private record Input(GenericStack exact) implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] { exact }; }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey key, Level level) { return exact.what().equals(key); }
        @Override public AEKey getRemainingKey(AEKey template) { return null; }
    }
}
