package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.energy.EnergyCubeTier;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Portable setup stored on a Configuration Card: an Entangled Link origin, machine setup, or Energy Cube side map. */
public final class ConfigurationCardData {
    public static final int SCHEMA = 1;
    public static final Codec<ConfigurationCardData> CODEC = CompoundTag.CODEC.xmap(ConfigurationCardData::new, ConfigurationCardData::copyTag);
    public static final StreamCodec<ByteBuf, ConfigurationCardData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(ConfigurationCardData::new, ConfigurationCardData::copyTag);
    public static final Supplier<DataComponentType<ConfigurationCardData>> TYPE = () -> Registration.TYPE.get();

    private static final class Registration {
        private static final DeferredRegister.DataComponents COMPONENTS =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReMatter.MOD_ID);
        private static final Supplier<DataComponentType<ConfigurationCardData>> TYPE =
                COMPONENTS.registerComponentType("configuration_card",
                        builder -> builder.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    }

    public static void register(IEventBus bus) { Registration.COMPONENTS.register(bus); }

    public record LinkOrigin(ResourceKey<Level> dimension, BlockPos pos) {
        public LinkOrigin {
            Objects.requireNonNull(dimension);
            pos = pos.immutable();
        }
    }

    public record MachineSetup(MachineKind sourceKind, int packedPorts, RedstoneMode redstoneMode,
                               boolean running, boolean repeat, boolean automation, boolean networkJobs,
                               String selection, MachineSettings settings, MachineSoundLevel soundLevel,
                               @Nullable IndustrialBlueprint blueprint, boolean structurePreview) {
        public MachineSetup(MachineKind sourceKind, int packedPorts, RedstoneMode redstoneMode,
                            boolean running, boolean repeat, boolean automation, boolean networkJobs,
                            String selection, MachineSettings settings) {
            this(sourceKind, packedPorts, redstoneMode, running, repeat, automation, networkJobs, selection, settings,
                    MachineSoundLevel.HIGH, null, false);
        }

        public MachineSetup(MachineKind sourceKind, int packedPorts, RedstoneMode redstoneMode,
                            boolean running, boolean repeat, boolean automation, boolean networkJobs,
                            String selection, MachineSettings settings, boolean soundMuted) {
            this(sourceKind, packedPorts, redstoneMode, running, repeat, automation, networkJobs, selection, settings,
                    soundMuted ? MachineSoundLevel.MUTED : MachineSoundLevel.HIGH, null, false);
        }

        public MachineSetup {
            Objects.requireNonNull(sourceKind);
            Objects.requireNonNull(redstoneMode);
            Objects.requireNonNull(selection);
            Objects.requireNonNull(settings);
            Objects.requireNonNull(soundLevel);
            if (blueprint != null && blueprint.kind() != sourceKind) {
                blueprint = null;
            }
        }

        public boolean soundMuted() {
            return soundLevel.muted();
        }
    }

    public record EnergyCubeSetup(EnergyCubeTier sourceTier, int packedSideModes) {
        public EnergyCubeSetup {
            Objects.requireNonNull(sourceTier);
        }
    }

    public record EmitterSetup(@Nullable SubstanceKey target,
                               long threshold, boolean emitWhenBelow) {
        public EmitterSetup {
            threshold = Math.clamp(threshold, 0L, MatterLevelEmitterBlockEntity.MAX_THRESHOLD);
        }
    }

    public record CellFilterSetup(CellPartitionData.Spec spec) {
        public CellFilterSetup {
            Objects.requireNonNull(spec);
        }
    }

    private final CompoundTag raw;

    public ConfigurationCardData(CompoundTag raw) { this.raw = Objects.requireNonNull(raw).copy(); }
    public CompoundTag copyTag() { return raw.copy(); }

    public static ConfigurationCardData fromCellFilter(CellPartitionData.Spec spec) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("kind", "cell_filter");
        tag.put("partition", CellPartitionData.from(spec).copyTag());
        return new ConfigurationCardData(tag);
    }

    public static ConfigurationCardData fromLink(ResourceKey<Level> dimension, BlockPos pos) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("kind", "link");
        tag.putString("dimension", dimension.identifier().toString());
        tag.putLong("pos", pos.asLong());
        return new ConfigurationCardData(tag);
    }

    public static ConfigurationCardData fromMachine(MachineSetup setup) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("kind", "machine");
        tag.putString("source", setup.sourceKind().id());
        tag.putInt("ports", setup.packedPorts());
        tag.putString("redstone", setup.redstoneMode().getSerializedName());
        tag.putBoolean("running", setup.running());
        tag.putBoolean("repeat", setup.repeat());
        tag.putBoolean("automation", setup.automation());
        tag.putBoolean("network_jobs", setup.networkJobs());
        tag.putInt("sound_volume", setup.soundLevel().level());
        tag.putBoolean("sound_muted", setup.soundMuted());
        tag.putString("selection", setup.selection());
        tag.putLong("target", setup.settings().target());
        tag.putString("target_substance", setup.settings().targetSubstance());
        tag.putString("target_phase", setup.settings().targetPhase());
        var gases = new net.minecraft.nbt.ListTag();
        setup.settings().ventedGases().stream().sorted().forEach(g -> gases.add(net.minecraft.nbt.StringTag.valueOf(g)));
        tag.put("vented_gases", gases);
        if (setup.blueprint() != null) {
            tag.put("blueprint", setup.blueprint().save());
        }
        if (setup.structurePreview()) {
            tag.putBoolean("structure_preview", true);
        }
        return new ConfigurationCardData(tag);
    }

    public static ConfigurationCardData fromEnergyCube(EnergyCubeTier tier, int packedSideModes) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("kind", "energy_cube");
        tag.putString("source", tier.id());
        tag.putInt("sides", packedSideModes);
        return new ConfigurationCardData(tag);
    }

    public static ConfigurationCardData fromLevelEmitter(EmitterSetup setup) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("kind", "level_emitter");
        if (setup.target() != null) {
            tag.putString("target_id", setup.target().id().toString());
            tag.putString("target_phase", setup.target().phase().name().toLowerCase(java.util.Locale.ROOT));
        }
        tag.putLong("threshold", setup.threshold());
        tag.putBoolean("emit_below", setup.emitWhenBelow());
        return new ConfigurationCardData(tag);
    }

    public String kind() {
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != SCHEMA) return "invalid";
        return raw.getStringOr("kind", "invalid");
    }

    public Optional<LinkOrigin> linkOrigin() {
        if (!kind().equals("link")) return Optional.empty();
        try {
            String dim = raw.getString("dimension").orElseThrow();
            var key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
            return Optional.of(new LinkOrigin(key, BlockPos.of(raw.getLong("pos").orElseThrow())));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    public Optional<MachineSetup> machineSetup() {
        if (!kind().equals("machine")) return Optional.empty();
        try {
            String sourceId = raw.getString("source").orElseThrow();
            MachineKind kind = null;
            for (var candidate : MachineKind.values()) if (candidate.id().equals(sourceId)) { kind = candidate; break; }
            if (kind == null) return Optional.empty();
            int ports = raw.getIntOr("ports", 0);
            RedstoneMode redstone = RedstoneMode.load(raw.getStringOr("redstone", "ignored"));
            var gasList = raw.getList("vented_gases").orElseGet(net.minecraft.nbt.ListTag::new);
            Set<String> gases = gasList.stream().map(Tag::asString).flatMap(Optional::stream).collect(java.util.stream.Collectors.toUnmodifiableSet());
            var settings = new MachineSettings(
                    raw.getLongOr("target", 0),
                    raw.getStringOr("target_substance", ""),
                    raw.getStringOr("target_phase", ""),
                    gases);
            int fallbackVolume = raw.getBooleanOr("sound_muted", false) ? 0 : MachineSoundLevel.HIGH.level();
            MachineSoundLevel soundLevel = MachineSoundLevel.byLevel(raw.getIntOr("sound_volume", fallbackVolume));
            MachineKind finalKind = kind;
            IndustrialBlueprint blueprint = raw.getCompound("blueprint")
                    .flatMap(tag -> IndustrialBlueprint.restore(finalKind, tag))
                    .orElse(null);
            return Optional.of(new MachineSetup(
                    kind, ports, redstone,
                    raw.getBooleanOr("running", false),
                    raw.getBooleanOr("repeat", false),
                    raw.getBooleanOr("automation", false),
                    raw.getBooleanOr("network_jobs", false),
                    MachineSchema.selection(raw.getStringOr("selection", "")),
                    settings,
                    soundLevel,
                    blueprint,
                    raw.getBooleanOr("structure_preview", false)));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    public Optional<EnergyCubeSetup> energyCubeSetup() {
        if (!kind().equals("energy_cube")) return Optional.empty();
        try {
            String sourceId = raw.getString("source").orElseThrow();
            EnergyCubeTier tier = null;
            for (var candidate : EnergyCubeTier.values()) if (candidate.id().equals(sourceId)) { tier = candidate; break; }
            if (tier == null) return Optional.empty();
            int sides = raw.getIntOr("sides", 0);
            if (sides < 0 || (sides >>> 12) != 0) return Optional.empty();
            return Optional.of(new EnergyCubeSetup(tier, sides));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    public Optional<EmitterSetup> levelEmitterSetup() {
        if (!kind().equals("level_emitter")) return Optional.empty();
        try {
            dev.latrox.rematter.chemistry.SubstanceKey target = null;
            var rawId = raw.getString("target_id");
            var rawPhase = raw.getString("target_phase");
            if (rawId.isPresent() && rawPhase.isPresent() && rawId.get().length() <= CellPartitionData.MAX_ID_LENGTH) {
                target = new dev.latrox.rematter.chemistry.SubstanceKey(
                        dev.latrox.rematter.chemistry.SubstanceId.parse(rawId.get()),
                        dev.latrox.rematter.chemistry.Phase.valueOf(rawPhase.get().toUpperCase(java.util.Locale.ROOT)));
            }
            long threshold = Math.clamp(raw.getLongOr("threshold", MatterLevelEmitterBlockEntity.DEFAULT_THRESHOLD),
                    0L, MatterLevelEmitterBlockEntity.MAX_THRESHOLD);
            boolean emitBelow = raw.getBooleanOr("emit_below", false);
            return Optional.of(new EmitterSetup(target, threshold, emitBelow));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    public Optional<CellFilterSetup> cellFilterSetup() {
        if (!kind().equals("cell_filter")) return Optional.empty();
        try {
            var partitionTag = raw.getCompound("partition").orElse(null);
            if (partitionTag == null) return Optional.empty();
            return new CellPartitionData(partitionTag).open().map(CellFilterSetup::new);
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    public static boolean canPartitionCell(net.minecraft.world.item.ItemStack stack) {
        if (stack.getCount() != 1) return false;
        if (stack.getItem() instanceof VoidMatterCellItem) {
            return CellPartitionData.read(stack).isPresent();
        }
        if (stack.getItem() instanceof MatterCellItem cell) {
            return CellPartitionData.read(stack).isPresent() && cell.open(stack).isPresent();
        }
        return false;
    }

    public static boolean applyCellPartition(net.minecraft.world.item.ItemStack stack, CellPartitionData.Spec spec) {
        if (!canPartitionCell(stack)) return false;
        if (!spec.restricted()) {
            stack.remove(CellPartitionData.TYPE.get());
        } else {
            stack.set(CellPartitionData.TYPE.get(), CellPartitionData.from(spec));
        }
        return true;
    }

    @Override public boolean equals(Object other) { return other instanceof ConfigurationCardData data && raw.equals(data.raw); }
    @Override public int hashCode() { return raw.hashCode(); }
}
