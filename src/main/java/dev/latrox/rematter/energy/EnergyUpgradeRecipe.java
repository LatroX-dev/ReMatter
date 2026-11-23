package dev.latrox.rematter.energy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.PortableEnergyCellItem;
import dev.latrox.rematter.content.WirelessMatterTerminalItem;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Shaped crafting recipe that preserves stored FE, side modes, and charging/discharging slot contents
 * when upgrading an Energy Cube (or energy-bearing component) to a higher tier, while refusing any
 * corrupted or unknown-schema cube data so protected contents are never discarded.
 */
public final class EnergyUpgradeRecipe extends ShapedRecipe {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ReMatter.MOD_ID);

    public static final MapCodec<ShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(o -> ((EnergyUpgradeRecipe) o).commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> ((EnergyUpgradeRecipe) o).bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> ((EnergyUpgradeRecipe) o).resultTemplate)
    ).apply(i, EnergyUpgradeRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> ((EnergyUpgradeRecipe) o).commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> ((EnergyUpgradeRecipe) o).bookInfo,
            ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
            ItemStackTemplate.STREAM_CODEC, o -> ((EnergyUpgradeRecipe) o).resultTemplate,
            EnergyUpgradeRecipe::new
    );

    public static final Supplier<RecipeSerializer<ShapedRecipe>> SERIALIZER =
            SERIALIZERS.register("energy_upgrade_shaped", () -> new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

    private final ItemStackTemplate resultTemplate;

    public EnergyUpgradeRecipe(Recipe.CommonInfo commonInfo,
                               CraftingRecipe.CraftingBookInfo bookInfo,
                               ShapedRecipePattern pattern,
                               ItemStackTemplate resultTemplate) {
        super(commonInfo, bookInfo, pattern, resultTemplate);
        this.resultTemplate = resultTemplate;
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    public ItemStackTemplate resultTemplate() {
        return resultTemplate;
    }

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return SERIALIZER.get();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) return false;
        HolderLookup.Provider registries = level == null ? null : level.registryAccess();
        EnergyCubeTier targetTier = resultTemplate.item().value() instanceof EnergyCubeItem target ? target.tier() : null;
        int chargeSlotsUsed = 0;
        int dischargeSlotsUsed = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof EnergyCubeItem sourceCube) {
                if (!isValidSourceCube(stack, sourceCube.tier(), registries)) return false;
                if (targetTier != null) {
                    var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                    if (data != null) {
                        var raw = data.copyTagWithoutId();
                        chargeSlotsUsed += nonEmptyEntryCount(raw.get("items"));
                        dischargeSlotsUsed += nonEmptyEntryCount(raw.get("discharge_items"));
                    }
                } else {
                    // Non-cube outputs cannot host Energy Cube item bays: refuse cubes carrying items.
                    var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                    if (data != null) {
                        var raw = data.copyTagWithoutId();
                        if (nonEmptyEntryCount(raw.get("items")) > 0 || nonEmptyEntryCount(raw.get("discharge_items")) > 0) {
                            return false;
                        }
                    }
                }
            }
        }
        if (targetTier != null && (chargeSlotsUsed > targetTier.chargingSlots() || dischargeSlotsUsed > targetTier.dischargingSlots())) {
            return false;
        }
        return true;
    }

    static boolean isValidSourceCube(ItemStack stack, EnergyCubeTier sourceTier, HolderLookup.@Nullable Provider registries) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return true;
        if (data.type() != ModContent.ENERGY_CUBE_ENTITY.get()) return false;
        var raw = data.copyTagWithoutId();
        if (registries != null) {
            var decodedCharge = EnergyCubeData.items(raw, registries);
            var decodedDischarge = EnergyCubeData.dischargeItems(raw, registries);
            boolean hasDischarge = raw.contains("discharge_items");
            int dischargeCount = hasDischarge ? decodedDischarge.map(List::size).orElse(-1) : sourceTier.dischargingSlots();
            return EnergyCubeData.rejection(
                    raw, sourceTier,
                    decodedCharge.isPresent(), decodedCharge.map(List::size).orElse(-1),
                    decodedDischarge.isPresent(), dischargeCount) == null;
        }
        if (!(raw.get("items") instanceof ListTag chargeList) || chargeList.size() != sourceTier.chargingSlots()
                || !allCompoundTags(chargeList)) {
            return false;
        }
        boolean hasDischarge = raw.contains("discharge_items");
        if (hasDischarge && (!(raw.get("discharge_items") instanceof ListTag dischargeList)
                || dischargeList.size() != sourceTier.dischargingSlots() || !allCompoundTags(dischargeList))) {
            return false;
        }
        return EnergyCubeData.rejection(raw, sourceTier, true, sourceTier.chargingSlots(), true, sourceTier.dischargingSlots()) == null;
    }

    private static boolean allCompoundTags(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag)) return false;
        }
        return true;
    }

    private static int nonEmptyEntryCount(@Nullable Tag tag) {
        if (!(tag instanceof ListTag list)) return 0;
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CompoundTag entry && !entry.isEmpty()) count++;
        }
        return count;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = super.assemble(input);
        if (result.isEmpty()) return result;

        long totalEnergy = 0L;
        int defaultModes = EnergyCubeSideMode.OUTPUT.ordinal();
        int sideModes = defaultModes;
        boolean customSideModes = false;
        ListTag chargeItems = new ListTag();
        ListTag dischargeItems = new ListTag();
        boolean hasChargeItems = false;
        boolean hasDischargeItems = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof EnergyCubeItem sourceCube) {
                var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                if (data == null || data.type() != ModContent.ENERGY_CUBE_ENTITY.get()) continue;
                var raw = data.copyTagWithoutId();
                if (!isValidSourceCube(stack, sourceCube.tier(), null)) continue;
                totalEnergy += Math.clamp(raw.getIntOr("energy", 0), 0, sourceCube.tier().capacity());
                int sourceModes = raw.getIntOr("side_modes", defaultModes);
                if (EnergyCubeData.validModes(sourceModes)) {
                    sideModes = sourceModes;
                    if (sideModes != defaultModes) customSideModes = true;
                }
                if (raw.get("items") instanceof ListTag list) {
                    for (int s = 0; s < list.size(); s++) {
                        if (list.get(s) instanceof CompoundTag entry && !entry.isEmpty()) {
                            chargeItems.add(entry.copy());
                            hasChargeItems = true;
                        }
                    }
                }
                if (raw.get("discharge_items") instanceof ListTag list) {
                    for (int s = 0; s < list.size(); s++) {
                        if (list.get(s) instanceof CompoundTag entry && !entry.isEmpty()) {
                            dischargeItems.add(entry.copy());
                            hasDischargeItems = true;
                        }
                    }
                }
            } else if (stack.getItem() instanceof PortableEnergyCellItem) {
                totalEnergy += PortableEnergyCellItem.energy(stack);
            } else if (stack.getItem() instanceof WirelessMatterTerminalItem) {
                totalEnergy += WirelessMatterTerminalItem.energy(stack);
            }
        }

        if (result.getItem() instanceof EnergyCubeItem targetCube) {
            EnergyCubeTier targetTier = targetCube.tier();
            int clampedEnergy = (int) Math.clamp(totalEnergy, 0L, targetTier.capacity());
            if (clampedEnergy > 0 || customSideModes || hasChargeItems || hasDischargeItems) {
                var paddedCharge = new ListTag();
                for (int s = 0; s < targetTier.chargingSlots(); s++) {
                    paddedCharge.add(s < chargeItems.size() ? chargeItems.get(s).copy() : new CompoundTag());
                }
                var tag = new CompoundTag();
                tag.putInt("schema", EnergyCubeData.SCHEMA);
                tag.putInt("energy", clampedEnergy);
                tag.putInt("side_modes", sideModes);
                tag.put("items", paddedCharge);
                if (hasDischargeItems) {
                    var paddedDischarge = new ListTag();
                    for (int s = 0; s < targetTier.dischargingSlots(); s++) {
                        paddedDischarge.add(s < dischargeItems.size() ? dischargeItems.get(s).copy() : new CompoundTag());
                    }
                    tag.put("discharge_items", paddedDischarge);
                }
                result.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(ModContent.ENERGY_CUBE_ENTITY.get(), tag));
            }
        } else if (result.getItem() instanceof PortableEnergyCellItem) {
            int clamped = (int) Math.clamp(totalEnergy, 0L, PortableEnergyCellItem.CAPACITY);
            if (clamped > 0) PortableEnergyCellItem.setEnergy(result, clamped);
        } else if (result.getItem() instanceof WirelessMatterTerminalItem) {
            int clamped = (int) Math.clamp(totalEnergy, 0L, WirelessMatterTerminalItem.CAPACITY);
            if (clamped > 0) WirelessMatterTerminalItem.setEnergy(result, clamped);
        }

        return result;
    }
}
