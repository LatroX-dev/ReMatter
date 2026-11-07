package dev.latrox.rematter.content;

import dev.latrox.rematter.automation.AutomationPort;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Cached, component-free display stacks and process progress for renderers. Derived from the
 * authoritative state, never persisted, and never a view of real inventory contents.
 */
final class MachineVisuals {
    private ItemStack input = ItemStack.EMPTY;
    private ItemStack secondary = ItemStack.EMPTY;
    private ItemStack tertiary = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private ItemStack processInput = ItemStack.EMPTY;
    private ItemStack processOutput = ItemStack.EMPTY;
    private int progress, duration, publishedProgress, publishedDuration;
    private boolean captured, publishedActive;
    private String publishedInput = "", publishedOutput = "";

    ItemStack input() { return input; }
    ItemStack secondary() { return secondary; }
    ItemStack tertiary() { return tertiary; }
    ItemStack output() { return output; }
    ItemStack processInput() { return processInput; }
    ItemStack processOutput() { return processOutput; }
    int progress() { return progress; }
    int duration() { return duration; }

    static boolean showsItems(MachineKind kind) {
        return switch (kind) {
            case ANALYZER, DECOMPOSER, SYNTHESIZER, PREPARATOR, HAND_PREPARATOR, SINTER_FURNACE, INDUSTRIAL_PREPARATOR -> true;
            default -> false;
        };
    }
    static boolean showsSecondaryItems(MachineKind kind) {
        return kind == MachineKind.PREPARATOR || kind == MachineKind.HAND_PREPARATOR || kind == MachineKind.INDUSTRIAL_PREPARATOR;
    }

    void beginTick() { captured = false; }

    void capture(ItemStack inputStack, ItemStack outputStack, int workProgress, int workDuration) {
        processInput = plain(processInput, inputStack);
        processOutput = plain(processOutput, outputStack);
        duration = Math.max(0, workDuration);
        progress = Math.clamp(workProgress, 0, duration);
        captured = true;
    }

    /** True when the client needs a block update for the current process state. */
    boolean publishProcess(boolean active, int workProgress, int workDuration, long gameTime) {
        if (!captured) {
            duration = Math.max(0, workDuration);
            progress = Math.clamp(workProgress, 0, duration);
            if (duration == 0) { processInput = ItemStack.EMPTY; processOutput = ItemStack.EMPTY; }
        }
        String inputId = id(processInput), outputId = id(processOutput);
        boolean important = publishedActive != active || publishedDuration != duration
                || !publishedInput.equals(inputId) || !publishedOutput.equals(outputId)
                || duration > 0 && progress == duration;
        if (!important && (publishedProgress == progress || gameTime % 4 != 0)) return false;
        publishedActive = active; publishedProgress = progress; publishedDuration = duration;
        publishedInput = inputId; publishedOutput = outputId;
        return true;
    }

    /** True when any visible slot item changed. */
    boolean refreshItems(MachineKind kind, IntFunction<ItemStack> slot) {
        boolean items = showsItems(kind), extra = showsSecondaryItems(kind);
        ItemStack nextInput = plain(input, items ? slot.apply(0) : ItemStack.EMPTY);
        ItemStack nextSecondary = plain(secondary, extra ? slot.apply(3) : ItemStack.EMPTY);
        ItemStack nextTertiary = plain(tertiary, extra ? slot.apply(4) : ItemStack.EMPTY);
        ItemStack nextOutput = plain(output, items ? slot.apply(1) : ItemStack.EMPTY);
        boolean changed = nextInput != input || nextSecondary != secondary || nextTertiary != tertiary || nextOutput != output;
        input = nextInput;
        secondary = nextSecondary;
        tertiary = nextTertiary;
        output = nextOutput;
        return changed;
    }

    void write(CompoundTag tag, MachineKind kind) {
        tag.putInt("visual_progress", progress); tag.putInt("visual_duration", duration);
        tag.putString("process_input", id(processInput));
        tag.putString("process_output", id(processOutput));
        // Reading this cache must not consume a pending change or observe provisional transfer slots.
        if (showsItems(kind)) {
            tag.putString("visible_input", id(input));
            tag.putString("visible_secondary", id(secondary));
            tag.putString("visible_tertiary", id(tertiary));
            tag.putString("visible_output", id(output));
        }
    }

    void read(ValueInput tag, MachineKind kind) {
        duration = Math.max(0, tag.getIntOr("visual_duration", 0));
        progress = Math.clamp(tag.getIntOr("visual_progress", 0), 0, duration);
        processInput = read(processInput, tag.getStringOr("process_input", ""));
        processOutput = read(processOutput, tag.getStringOr("process_output", ""));
        boolean items = showsItems(kind), extra = showsSecondaryItems(kind);
        input = read(input, items ? tag.getStringOr("visible_input", "") : "");
        secondary = read(secondary, extra ? tag.getStringOr("visible_secondary", "") : "");
        tertiary = read(tertiary, extra ? tag.getStringOr("visible_tertiary", "") : "");
        output = read(output, items ? tag.getStringOr("visible_output", "") : "");
    }

    static ItemStack firstItem(Map<AutomationPort.Resource, Long> resources) {
        return resources.keySet().stream().filter(key -> key.kind() == AutomationPort.Kind.ITEM).sorted()
                .findFirst().map(key -> read(ItemStack.EMPTY, key.id())).orElse(ItemStack.EMPTY);
    }

    private static ItemStack plain(ItemStack previous, ItemStack actual) {
        if (actual.isEmpty()) return ItemStack.EMPTY;
        return !previous.isEmpty() && previous.getItem() == actual.getItem() ? previous : new ItemStack(actual.getItem(), 1);
    }
    private static String id(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    private static ItemStack read(ItemStack previous, String value) {
        if (value.isEmpty() || value.length() > 512) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(value);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || id.toString().equals("minecraft:air")) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return !previous.isEmpty() && previous.getItem() == item ? previous : new ItemStack(item, 1);
    }
}
