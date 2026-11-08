package dev.latrox.rematter.compat.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MatterRecoveryItem;
import dev.latrox.rematter.content.ChemistryText;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;

/** A substance identity in AE2; the phase remains part of its primary identity. */
public final class MatterKey extends AEKey {
    private static final Interner<SubstanceKey> INTERN = Interners.newWeakInterner();
    private static final Codec<SubstanceId> ID_CODEC = Codec.string(1, 512).comapFlatMap(id -> {
        try { return DataResult.success(SubstanceId.parse(id)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(() -> "Invalid substance ID"); }
    }, SubstanceId::toString);
    private static final Codec<Phase> PHASE_CODEC = Codec.STRING.comapFlatMap(phase -> {
        try { return DataResult.success(Phase.valueOf(phase)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(() -> "Invalid substance phase"); }
    }, Phase::name);
    public static final MapCodec<MatterKey> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ID_CODEC.fieldOf("substance").forGetter(key -> key.substance.id()),
            PHASE_CODEC.fieldOf("phase").forGetter(key -> key.substance.phase()))
            .apply(instance, (id, phase) -> of(new SubstanceKey(id, phase))));
    public static final AEKeyType TYPE = new AEKeyType(Identifier.fromNamespaceAndPath("rematter", "matter"),
            MatterKey.class, Component.translatable("integration.rematter.matter")) {
        @Override public MapCodec<MatterKey> codec() { return MatterKey.CODEC; }
        @Override public MatterKey readFromPacket(RegistryFriendlyByteBuf buffer) {
            return of(new SubstanceKey(SubstanceId.parse(buffer.readUtf(512)), buffer.readEnum(Phase.class)));
        }
        @Override public int getAmountPerUnit() { return 1; }
        @Override public int getAmountPerOperation() { return 1_000; }
        @Override public String getUnitSymbol() { return "u"; }
    };

    /**
     * AE2 asks for display names on both sides. The server names a key from its own catalogue; the client installs
     * names from its synchronized catalogue. An undefined key keeps its stable ID and is marked unknown.
     */
    private static volatile Function<SubstanceKey, Component> names = key -> Component.translatable(
            "gui.rematter.viewer.substance_phase", ChemistryText.name(ReMatter.chemistry(), key), ChemistryText.phase(key.phase()));
    static void installNames(Function<SubstanceKey, Component> clientNames) { names = Objects.requireNonNull(clientNames); }

    private final SubstanceKey substance;
    private MatterKey(SubstanceKey substance) { this.substance = INTERN.intern(Objects.requireNonNull(substance)); }
    public static MatterKey of(SubstanceKey substance) { return new MatterKey(substance); }
    public SubstanceKey substance() { return substance; }
    @Override public AEKeyType getType() { return TYPE; }
    @Override public MatterKey dropSecondary() { return this; }
    @Override public Object getPrimaryKey() { return substance; }
    @Override public Identifier getId() { return Identifier.parse(substance.id().toString()); }
    @Override public boolean hasComponents() { return false; }
    @Override public void toTag(ValueOutput output) { output.store(CODEC, this); }
    @Override public void writeToPacket(RegistryFriendlyByteBuf output) {
        output.writeUtf(substance.id().toString(), 512);
        output.writeEnum(substance.phase());
    }
    @Override protected Component computeDisplayName() { return names.apply(substance); }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos position) {
        if (amount < 0) throw new IllegalArgumentException("Negative recovery quantity");
        if (amount > 0) drops.add(MatterRecoveryItem.create(substance, amount));
    }
    @Override public boolean equals(Object other) { return other instanceof MatterKey key && substance.equals(key.substance); }
    @Override public int hashCode() { return substance.hashCode(); }
}
