package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Every sound event of the mod; all clips are bespoke ReMatter audio listed in the generated sounds.json, none is vanilla.
 * <ul>
 * <li>Tool and interface one-shots are played by the server through {@link #play}, one event per kind of action.</li>
 * <li>Each machine role has a {@code _work} loop, an {@code _accent} and a {@code _cycle} motion cue. The client plays
 * them as the generated machine_audio.json describes per machine kind; {@link #workingSound} only says whether a kind
 * has audio at all and names the loop used when that description is unusable.</li>
 * </ul>
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ReMatter.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_CONFIGURE = register("wrench_configure");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_DISMANTLE = register("wrench_dismantle");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORT_CYCLE = register("port_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONFIG_CARD_READ = register("config_card_read");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONFIG_CARD_WRITE = register("config_card_write");
    public static final DeferredHolder<SoundEvent, SoundEvent> CELL_INSERT = register("cell_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> CELL_EXTRACT = register("cell_extract");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAND_CRANK = register("hand_crank");
    public static final DeferredHolder<SoundEvent, SoundEvent> SINTER_WORK = register("sinter_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> SINTER_ACCENT = register("sinter_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_WORK = register("analyzer_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_ACCENT = register("analyzer_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> DECOMPOSER_WORK = register("decomposer_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> DECOMPOSER_ACCENT = register("decomposer_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> SYNTHESIZER_WORK = register("synthesizer_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> SYNTHESIZER_ACCENT = register("synthesizer_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROCESSOR_WORK = register("processor_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROCESSOR_ACCENT = register("processor_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEMICAL_REACTOR_WORK = register("chemical_reactor_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEMICAL_REACTOR_ACCENT = register("chemical_reactor_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAND_PREPARATOR_WORK = register("hand_preparator_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAND_PREPARATOR_ACCENT = register("hand_preparator_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> PREPARATOR_WORK = register("preparator_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> PREPARATOR_ACCENT = register("preparator_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> PREPARATION_REACTOR_WORK = register("preparation_reactor_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> PREPARATION_REACTOR_ACCENT = register("preparation_reactor_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_WORK = register("generator_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_ACCENT = register("generator_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONVERTER_WORK = register("converter_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONVERTER_ACCENT = register("converter_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATMOSPHERIC_WORK = register("atmospheric_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATMOSPHERIC_ACCENT = register("atmospheric_accent");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_COMPLETE = register("analyzer_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENTANGLED_PAIR = register("entangled_pair");
    public static final DeferredHolder<SoundEvent, SoundEvent> CELL_TRANSFER = register("cell_transfer");
    // Motion cues: one clip per visible mechanism cycle, fired by the client from the machine's animation phase.
    public static final DeferredHolder<SoundEvent, SoundEvent> SINTER_CYCLE = register("sinter_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_CYCLE = register("analyzer_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DECOMPOSER_CYCLE = register("decomposer_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SYNTHESIZER_CYCLE = register("synthesizer_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROCESSOR_CYCLE = register("processor_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEMICAL_REACTOR_CYCLE = register("chemical_reactor_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAND_PREPARATOR_CYCLE = register("hand_preparator_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> PREPARATOR_CYCLE = register("preparator_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> PREPARATION_REACTOR_CYCLE = register("preparation_reactor_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_CYCLE = register("generator_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONVERTER_CYCLE = register("converter_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATMOSPHERIC_CYCLE = register("atmospheric_cycle");
    // Interactions that used to borrow another action's sound.
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_ROTATE = register("wrench_rotate");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_MODE = register("wrench_mode");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONFIG_CARD_CLEAR = register("config_card_clear");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANISTER_FILL = register("canister_fill");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANISTER_EMPTY = register("canister_empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> SYNTHESIZER_COMPLETE = register("synthesizer_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRELESS_LINK = register("wireless_link");
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_ORDER = register("terminal_order");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENERGY_CELL_ON = register("energy_cell_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENERGY_CELL_OFF = register("energy_cell_off");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRUCTURE_FORMED = register("structure_formed");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACTION_DENIED = register("action_denied");

    private ModSounds() {}

    public static @Nullable DeferredHolder<SoundEvent, SoundEvent> workingSound(MachineKind kind) {
        return switch (kind) {
            case ANALYZER -> ANALYZER_WORK;
            case DECOMPOSER -> DECOMPOSER_WORK;
            case SYNTHESIZER -> SYNTHESIZER_WORK;
            case REACTOR -> PROCESSOR_WORK;
            case INDUSTRIAL_REACTOR, INDUSTRIAL_DECOMPOSER, INDUSTRIAL_SYNTHESIZER -> CHEMICAL_REACTOR_WORK;
            case HAND_PREPARATOR -> HAND_PREPARATOR_WORK;
            case PREPARATOR -> PREPARATOR_WORK;
            case INDUSTRIAL_PREPARATOR -> PREPARATION_REACTOR_WORK;
            case SINTER_FURNACE -> SINTER_WORK;
            case CRYSTAL_GENERATOR, INDUSTRIAL_CRYSTAL_GENERATOR -> GENERATOR_WORK;
            case PRIMAL_CONVERTER -> CONVERTER_WORK;
            case ATMOSPHERIC_COLLECTOR -> ATMOSPHERIC_WORK;
            case DRIVE, TERMINAL, NETWORK_INTERFACE -> null;
        };
    }

    public static @Nullable DeferredHolder<SoundEvent, SoundEvent> accentSound(MachineKind kind) {
        return switch (kind) {
            case ANALYZER -> ANALYZER_ACCENT;
            case DECOMPOSER -> DECOMPOSER_ACCENT;
            case SYNTHESIZER -> SYNTHESIZER_ACCENT;
            case REACTOR -> PROCESSOR_ACCENT;
            case INDUSTRIAL_REACTOR, INDUSTRIAL_DECOMPOSER, INDUSTRIAL_SYNTHESIZER -> CHEMICAL_REACTOR_ACCENT;
            case HAND_PREPARATOR -> HAND_PREPARATOR_ACCENT;
            case PREPARATOR -> PREPARATOR_ACCENT;
            case INDUSTRIAL_PREPARATOR -> PREPARATION_REACTOR_ACCENT;
            case SINTER_FURNACE -> SINTER_ACCENT;
            case CRYSTAL_GENERATOR, INDUSTRIAL_CRYSTAL_GENERATOR -> GENERATOR_ACCENT;
            case PRIMAL_CONVERTER -> CONVERTER_ACCENT;
            case ATMOSPHERIC_COLLECTOR -> ATMOSPHERIC_ACCENT;
            case DRIVE, TERMINAL, NETWORK_INTERFACE -> null;
        };
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, name)));
    }

    public static void play(@Nullable Level level, BlockPos pos, DeferredHolder<SoundEvent, SoundEvent> sound) {
        play(level, pos, sound, 1.0F, 1.0F);
    }

    public static void play(@Nullable Level level, BlockPos pos, DeferredHolder<SoundEvent, SoundEvent> sound, float volume, float pitch) {
        if (volume > 0.001F && level instanceof ServerLevel server && sound.isBound()) {
            server.playSound(null, pos, sound.get(), SoundSource.BLOCKS, volume, pitch);
        }
    }
}
