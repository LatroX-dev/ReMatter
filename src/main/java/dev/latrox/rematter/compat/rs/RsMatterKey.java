package dev.latrox.rematter.compat.rs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.refinedmods.refinedstorage.api.network.node.grid.GridOperations;
import com.refinedmods.refinedstorage.api.network.node.grid.GridExtractMode;
import com.refinedmods.refinedstorage.api.network.node.grid.GridInsertMode;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.ExtractableStorage;
import com.refinedmods.refinedstorage.api.storage.InsertableStorage;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceTag;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceType;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Native RS identity. The quantity is separate, exact long units; no item or fluid alias exists. */
public record RsMatterKey(SubstanceKey substance) implements PlatformResourceKey {
    public RsMatterKey { Objects.requireNonNull(substance); }
    public static final MapCodec<RsMatterKey> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("substance").forGetter(key -> key.substance.id().toString()),
            Codec.STRING.fieldOf("phase").forGetter(key -> key.substance.phase().name().toLowerCase(Locale.ROOT))
    ).apply(instance, (id, phase) -> new RsMatterKey(SubstanceKey.of(id, Phase.valueOf(phase.toUpperCase(Locale.ROOT))))));
    public static final ResourceType TYPE = new MatterType();
    @Override public long getInterfaceExportLimit() { return 16_000; }
    @Override public long getProcessingPatternLimit() { return Long.MAX_VALUE; }
    @Override public List<ResourceTag> getTags() { return List.of(); }
    @Override public ResourceType getResourceType() { return TYPE; }

    private static final class MatterType implements ResourceType {
        @Override public MapCodec<PlatformResourceKey> getMapCodec() {
            return CODEC.xmap(key -> key, key -> (RsMatterKey) key);
        }
        @Override public StreamCodec<RegistryFriendlyByteBuf, PlatformResourceKey> getStreamCodec() {
            return new StreamCodec<>() {
                @Override public PlatformResourceKey decode(RegistryFriendlyByteBuf buffer) {
                    return new RsMatterKey(SubstanceKey.of(buffer.readUtf(512), Phase.valueOf(buffer.readUtf(16))));
                }
                @Override public void encode(RegistryFriendlyByteBuf buffer, PlatformResourceKey value) {
                    RsMatterKey key = (RsMatterKey) value;
                    buffer.writeUtf(key.substance.id().toString(), 512);
                    buffer.writeUtf(key.substance.phase().name(), 16);
                }
            };
        }
        @Override public long normalizeAmount(double amount) {
            if (!Double.isFinite(amount) || amount < 0 || amount > 9_007_199_254_740_991d) {
                throw new IllegalArgumentException("Quantity cannot be represented exactly by this UI");
            }
            return BigDecimal.valueOf(amount).toBigIntegerExact().longValueExact();
        }
        @Override public double getDisplayAmount(long amount) { return amount; }
        @Override public long getInterfaceExportLimit() { return 16_000; }
        @Override public GridOperations createGridOperations(RootStorage root, Actor actor) {
            // Matter is available to recipes, never materialized as a fake cursor ItemStack.
            return new GridOperations() {
                @Override public boolean extract(ResourceKey resource, GridExtractMode mode, InsertableStorage destination) { return false; }
                @Override public boolean insert(ResourceKey resource, GridInsertMode mode, ExtractableStorage source) { return false; }
            };
        }
    }
}
