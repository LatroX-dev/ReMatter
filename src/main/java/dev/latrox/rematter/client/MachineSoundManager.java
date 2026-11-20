package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.client.anim.AnimClock;
import dev.latrox.rematter.client.anim.ChamberFocus;
import dev.latrox.rematter.client.anim.ChamberMotions;
import dev.latrox.rematter.client.anim.MachineMotions;
import dev.latrox.rematter.client.anim.MotionCue;
import dev.latrox.rematter.client.anim.WorkEnvelope;
import dev.latrox.rematter.content.IndustrialStructure;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineClientAudio;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.ModSounds;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Plays what a working machine sounds like, as described per machine kind by the generated resource
 * {@code assets/rematter/machine_audio.json} (see {@link MachineAudioProfile}):
 * <ul>
 * <li>one seamless loop while the machine is active, with the profile's pitch, fade-in, hold and fade-out;</li>
 * <li>motion cues, one clip each time the machine's own mechanism passes the cue's point of its cycle. The phase is
 * the {@link WorkEnvelope} the renderer draws from ({@link MachineMotions}; the chamber modules' envelope from
 * {@link ChamberMotions} for the two reactors), sampled here every client tick so it also runs behind the player;</li>
 * <li>an optional accent on a free-running timer, for pictures that are irregular themselves.</li>
 * </ul>
 * A formed reactor sounds from the centre of its chamber, every other machine from its block. Nearby machines
 * attenuate each other, and the machine's own four-step volume scales everything. A missing or unusable profile
 * leaves the kind with its plain loop.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class MachineSoundManager {
    public static final Identifier PROFILES = Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "machine_audio.json");
    private static final float MASTER_GAIN = 0.86F;
    private static final int MUTE_FADE_TICKS = 4;
    private static final int MAX_LIVE_CUES = 3;
    private static final double MAX_HEARING_DISTANCE_SQR = 18.0 * 18.0;
    private static final double CLUSTER_DISTANCE_SQR = 14.0 * 14.0;

    private static final Map<BlockPos, MachineAudioController> ACTIVE = new HashMap<>();
    private static volatile Map<MachineKind, Voice> voices = Map.of();
    private static volatile boolean voicesReplaced;

    /** A profile with its sound events resolved; {@code cues} runs parallel to the profile's cue list. */
    private record Voice(MachineAudioProfile profile, SoundEvent loop, @Nullable SoundEvent accent, List<SoundEvent> cues) {
        static @Nullable Voice plain(MachineKind kind) {
            var working = ModSounds.workingSound(kind);
            return working == null || !working.isBound() ? null
                    : new Voice(MachineAudioProfile.fallback(working.getId().toString()), working.get(), null, List.of());
        }

        /** Null when an event of the profile is not registered. */
        static @Nullable Voice of(MachineAudioProfile profile) {
            SoundEvent loop = event(profile.loop().event());
            SoundEvent accent = profile.accent() == null ? null : event(profile.accent().event());
            var cues = new ArrayList<SoundEvent>(profile.cues().size());
            for (var cue : profile.cues()) cues.add(event(cue.event()));
            return loop == null || profile.accent() != null && accent == null || cues.contains(null) ? null
                    : new Voice(profile, loop, accent, List.copyOf(cues));
        }

        private static @Nullable SoundEvent event(String id) {
            Identifier key = Identifier.tryParse(id);
            return key == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(key);
        }
    }

    /** Where a machine sounds from, and the chamber its modules aim at when it is a formed structure. */
    private record Spot(double x, double y, double z, @Nullable ChamberFocus focus) {
        static Spot of(BlockPos pos, BlockState state, @Nullable MachineBlockEntity machine, Voice voice) {
            ChamberFocus focus = null;
            if (machine != null && IndustrialStructure.isIndustrial(machine.kind()) && machine.industrialFormed()
                    && machine.industrialWidth() > 0 && state.getBlock() instanceof MachineBlock) {
                var facing = state.getValue(MachineBlock.FACING);
                focus = ChamberFocus.ofController(pos.getX(), pos.getY(), pos.getZ(), facing.getStepX(), facing.getStepZ(),
                        machine.industrialWidth(), machine.industrialHeight(), machine.industrialDepth(), machine.industrialControllerColumn());
            }
            return voice.profile().chamber() && focus != null ? new Spot(focus.x(), focus.y(), focus.z(), focus)
                    : new Spot(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, focus);
        }
    }

    private MachineSoundManager() {}

    public static void register() {
        MachineClientAudio.install(MachineSoundManager::onActiveTick);
    }

    /** Client resource reload, also the first one at startup. Never throws; whatever is unusable is logged once. */
    public static void reload(ResourceManager resources) {
        var next = new EnumMap<MachineKind, Voice>(MachineKind.class);
        try {
            String json = null;
            var resource = resources.getResource(PROFILES);
            if (resource.isPresent()) {
                try (var reader = resource.get().openAsReader()) {
                    json = reader.lines().collect(Collectors.joining("\n"));
                }
            }
            var parsed = MachineAudioProfile.parse(json);
            if (parsed.failure() != null) {
                ReMatter.LOGGER.warn("Machine audio profiles {} are unusable ({}); every machine keeps its plain working loop",
                        PROFILES, json == null ? "resource missing" : parsed.failure());
            }
            for (MachineKind kind : MachineKind.values()) {
                Voice plain = Voice.plain(kind);
                if (plain == null) continue;
                var choice = MachineAudioProfile.choose(parsed, kind.id(), plain.profile().loop().event(), id -> Voice.event(id) != null);
                if (choice.problem() != null && parsed.failure() == null) {
                    ReMatter.LOGGER.warn("Machine audio profile for {} is unusable ({}); it keeps its plain working loop", kind.id(), choice.problem());
                }
                Voice voice = choice.problem() == null ? Voice.of(choice.profile()) : null;
                next.put(kind, voice == null ? plain : voice);
            }
        } catch (IOException | RuntimeException failed) {
            ReMatter.LOGGER.warn("Machine audio profiles {} could not be read; machines keep their plain working loop", PROFILES, failed);
            next.clear();
        }
        voices = Map.copyOf(next);
        voicesReplaced = true;
    }

    private static @Nullable Voice voice(MachineKind kind) {
        Voice voice = voices.get(kind);
        return voice != null ? voice : Voice.plain(kind);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (voicesReplaced) {
            // A reload silences the sound engine; controllers start over with the profiles that are valid now.
            voicesReplaced = false;
            clearAll();
        }
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clearAll();
            return;
        }
        if (mc.isPaused()) return;

        // Ticks run before this frame's real-time delta is measured; the committed frame time is what was drawn last.
        double clock = AnimClock.lastFrame();
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            MachineAudioController controller = entry.getValue();
            float clusterGain = 1.0F / (float) Math.sqrt(nearbyClusterSize(mc.level, entry.getKey()));
            controller.tick(mc, clusterGain, clock);
            if (controller.isFinished()) {
                iterator.remove();
            }
        }
    }

    private static void clearAll() {
        for (MachineAudioController controller : ACTIVE.values()) {
            controller.stopImmediately();
        }
        ACTIVE.clear();
    }

    private static void onActiveTick(Level level, BlockPos pos, MachineKind kind) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != clientLevel || mc.player == null) return;
        Voice voice = voice(kind);
        if (voice == null) return;
        MachineBlockEntity machine = clientLevel.getBlockEntity(pos) instanceof MachineBlockEntity found ? found : null;
        Spot spot = Spot.of(pos, clientLevel.getBlockState(pos), machine, voice);
        if (mc.player.distanceToSqr(spot.x(), spot.y(), spot.z()) > MAX_HEARING_DISTANCE_SQR) {
            return;
        }
        if (machine != null && machine.soundGain() <= 0.0F) {
            return;
        }

        BlockPos key = pos.immutable();
        MachineAudioController existing = ACTIVE.get(key);
        if (existing != null && existing.matches(clientLevel, kind)) {
            existing.keepAlive();
            return;
        }
        if (existing != null) {
            existing.stopImmediately();
        }
        int startStagger = nearbyClusterSize(clientLevel, key) > 1
                ? (int) Math.floorMod(key.asLong() * 31L, 5L)
                : 0;
        ACTIVE.put(key, new MachineAudioController(clientLevel, key, kind, voice, spot, startStagger));
    }

    private static int nearbyClusterSize(ClientLevel level, BlockPos center) {
        int count = 0;
        for (var entry : ACTIVE.entrySet()) {
            MachineAudioController controller = entry.getValue();
            if (controller.level == level && controller.isAudible() && entry.getKey().distSqr(center) <= CLUSTER_DISTANCE_SQR) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static final class MachineAudioController {
        private final ClientLevel level;
        private final BlockPos pos;
        private final MachineKind kind;
        private final Voice voice;
        private final MachineAudioProfile.Loop loop;
        private final MotionCue[] cues;
        /** The envelope each cue listened to last; a cue that changes its mechanism starts over without a sound. */
        private final WorkEnvelope[] heard;
        private @Nullable LoopingBedInstance primaryLoop;
        private final List<ClipInstance> liveAccents = new ArrayList<>(2);
        private final List<ClipInstance> liveCues = new ArrayList<>(MAX_LIVE_CUES);
        private Spot spot;
        private int startDelayTicks;
        private int accentCooldown;
        private int holdTicks;
        private boolean fastStop;
        private float lastMachineGain = 1.0F;
        private float currentGain = MASTER_GAIN;

        private MachineAudioController(ClientLevel level, BlockPos pos, MachineKind kind, Voice voice, Spot spot, int startDelayTicks) {
            this.level = level;
            this.pos = pos;
            this.kind = kind;
            this.voice = voice;
            this.loop = voice.profile().loop();
            this.spot = spot;
            var profiles = voice.profile().cues();
            this.cues = new MotionCue[profiles.size()];
            for (int i = 0; i < cues.length; i++) cues[i] = new MotionCue(profiles.get(i).period(), profiles.get(i).phase());
            this.heard = new WorkEnvelope[cues.length];
            this.holdTicks = loop.hold();
            this.startDelayTicks = startDelayTicks;
            this.accentCooldown = voice.profile().accent() == null ? 0 : voice.profile().accent().offset();
        }

        private boolean matches(ClientLevel currentLevel, MachineKind expectedKind) {
            return this.level == currentLevel && this.kind == expectedKind && !this.fastStop;
        }

        private boolean isAudible() {
            return holdTicks > 0 || primaryLoop != null && !primaryLoop.isStopped() || !liveAccents.isEmpty() || !liveCues.isEmpty();
        }

        private boolean isFinished() {
            return holdTicks <= 0 && (primaryLoop == null || primaryLoop.isStopped()) && liveAccents.isEmpty() && liveCues.isEmpty();
        }

        private void keepAlive() {
            if (!fastStop) {
                this.holdTicks = loop.hold();
            }
        }

        private void stopImmediately() {
            this.holdTicks = 0;
            this.fastStop = true;
            if (primaryLoop != null) {
                primaryLoop.forceStop();
                primaryLoop = null;
            }
            for (ClipInstance clip : liveAccents) {
                clip.forceStop();
            }
            liveAccents.clear();
            for (ClipInstance clip : liveCues) {
                clip.forceStop();
            }
            liveCues.clear();
        }

        private void tick(Minecraft mc, float clusterGain, double clock) {
            if (mc.level != level || mc.player == null) {
                stopImmediately();
                return;
            }

            BlockState state = level.getBlockState(pos);
            boolean blockMatches = state.getBlock() instanceof MachineBlock machineBlock && machineBlock.kind() == kind;
            if (!blockMatches) {
                stopImmediately();
                return;
            }

            MachineBlockEntity machine = level.getBlockEntity(pos) instanceof MachineBlockEntity found ? found : null;
            // A structure that forms or breaks while it sounds moves its source between the chamber and the controller.
            this.spot = Spot.of(pos, state, machine, voice);
            if (mc.player.distanceToSqr(spot.x(), spot.y(), spot.z()) > MAX_HEARING_DISTANCE_SQR * 1.35) {
                stopImmediately();
                return;
            }

            float machineGain = machine != null ? machine.soundGain() : 1.0F;
            if (machineGain > 0.0F) {
                lastMachineGain = machineGain;
            }
            this.currentGain = MASTER_GAIN * clusterGain * lastMachineGain;

            if (machineGain <= 0.0F) {
                holdTicks = 0;
                fastStop = true;
            } else if (state.getValue(MachineBlock.ACTIVE)) {
                holdTicks = loop.hold();
                fastStop = false;
            } else if (holdTicks > 0) {
                holdTicks--;
            }

            if (primaryLoop != null && primaryLoop.isStopped()) {
                primaryLoop = null;
            }
            // A clip that plays to its end is dropped by the sound engine without ever being marked as stopped.
            liveAccents.removeIf(clip -> clip.isStopped() || !mc.getSoundManager().isActive(clip));
            liveCues.removeIf(clip -> clip.isStopped() || !mc.getSoundManager().isActive(clip));

            if (holdTicks <= 0) {
                // The mechanism moves on unheard; a controller that resumes takes its first sample as a new one does.
                for (MotionCue cue : cues) cue.reset();
                int fadeTicks = fastStop ? MUTE_FADE_TICKS : loop.fadeOut();
                if (primaryLoop != null) {
                    primaryLoop.beginFadeOut(fadeTicks);
                }
                for (ClipInstance clip : liveAccents) {
                    clip.beginFadeOut(fadeTicks);
                }
                for (ClipInstance clip : liveCues) {
                    clip.beginFadeOut(fadeTicks);
                }
                return;
            }

            if (startDelayTicks > 0) {
                startDelayTicks--;
                return;
            }

            if (primaryLoop == null) {
                primaryLoop = new LoopingBedInstance(this, voice.loop(), loop);
                mc.getSoundManager().play(primaryLoop);
            } else {
                primaryLoop.resumeIfFading();
            }

            if (machine != null) {
                followMotion(mc, machine, clock);
            }

            var accent = voice.profile().accent();
            if (accent != null && voice.accent() != null) {
                if (accentCooldown <= 0) {
                    if (accent.mixing() == null || machine != null && machine.preparationMixing() == accent.mixing()) {
                        float pitch = Math.clamp(loop.pitch() * (0.97F + ThreadLocalRandom.current().nextFloat() * 0.06F), 0.5F, 2.0F);
                        play(mc, liveAccents, voice.accent(), accent.gain(), pitch);
                    }
                    int jitterTicks = accent.jitter() == 0 ? 0 : ThreadLocalRandom.current().nextInt(-accent.jitter(), accent.jitter() + 1);
                    accentCooldown = Math.max(20, accent.interval() + jitterTicks);
                } else {
                    accentCooldown--;
                }
            }
        }

        /** Advances the shared mechanism phase, also for a machine nobody looks at, and plays the cues it passes. */
        private void followMotion(Minecraft mc, MachineBlockEntity machine, double clock) {
            WorkEnvelope own = MachineMotions.sample(machine, clock);
            WorkEnvelope chamber = null;
            boolean chamberSampled = false;
            var profiles = voice.profile().cues();
            for (int i = 0; i < cues.length; i++) {
                var cue = profiles.get(i);
                WorkEnvelope work = own;
                if (cue.chamber()) {
                    if (!chamberSampled) {
                        chamberSampled = true;
                        chamber = spot.focus() == null ? null : ChamberMotions.resample(level, spot.focus(), clock);
                    }
                    // Until a module of the chamber has been drawn there is no blade to follow; the rings keep the time.
                    if (chamber != null) work = chamber;
                }
                if (heard[i] != work) {
                    heard[i] = work;
                    cues[i].reset();
                }
                if (!cues[i].advance(work.turns(), work.drive(), cue.minDrive())) continue;
                if (cue.mixing() != null && machine.preparationMixing() != cue.mixing()) continue;
                if (liveCues.size() >= MAX_LIVE_CUES) continue;
                float pitch = MotionCue.pitch(loop.pitch(), cue.stretch(), work.tempo(), cue.glides(), cue.pitchFrom(), cue.pitchTo(), work.progress());
                play(mc, liveCues, voice.cues().get(i), cue.gain(), pitch);
            }
        }

        private void play(Minecraft mc, List<ClipInstance> live, SoundEvent event, float gain, float pitch) {
            ClipInstance clip = new ClipInstance(this, event, gain, pitch);
            if (mc.getSoundManager().play(clip) != SoundEngine.PlayResult.NOT_STARTED) live.add(clip);
        }
    }

    private static final class LoopingBedInstance extends AbstractTickableSoundInstance {
        private final MachineAudioController owner;
        private final float layerScale;
        private final float fadeInStep;
        private float fadeEnvelope;
        private float fadeOutStep = 0.0F;

        private LoopingBedInstance(MachineAudioController owner, SoundEvent event, MachineAudioProfile.Loop loop) {
            super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.owner = owner;
            this.layerScale = loop.gain();
            this.fadeInStep = 1.0F / loop.fadeIn();
            this.fadeEnvelope = fadeInStep;
            this.looping = true;
            this.delay = 0;
            this.pitch = loop.pitch();
            this.volume = Math.max(0.02F, owner.currentGain * layerScale * fadeEnvelope);
            this.x = owner.spot.x();
            this.y = owner.spot.y();
            this.z = owner.spot.z();
        }

        private void beginFadeOut(int ticks) {
            float step = 1.0F / Math.max(1, ticks);
            if (step > this.fadeOutStep) {
                this.fadeOutStep = step;
            }
        }

        private void resumeIfFading() {
            this.fadeOutStep = 0.0F;
        }

        private void forceStop() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public float getVolume() {
            return this.sound == null ? 0.0F : super.getVolume();
        }

        @Override
        public void tick() {
            if (fadeOutStep > 0.0F) {
                fadeEnvelope = Math.max(0.0F, fadeEnvelope - fadeOutStep);
                if (fadeEnvelope <= 0.01F) {
                    stop();
                    return;
                }
            } else if (fadeEnvelope < 1.0F) {
                fadeEnvelope = Math.min(1.0F, fadeEnvelope + fadeInStep);
            }
            this.volume = Math.max(0.01F, owner.currentGain * layerScale * fadeEnvelope);
            this.x = owner.spot.x();
            this.y = owner.spot.y();
            this.z = owner.spot.z();
        }
    }

    /** One accent or motion cue: follows its machine's gain and place, and fades out with the loop. */
    private static final class ClipInstance extends AbstractTickableSoundInstance {
        private final MachineAudioController owner;
        private final float layerScale;
        private float fadeEnvelope = 1.0F;
        private float fadeOutStep = 0.0F;

        private ClipInstance(MachineAudioController owner, SoundEvent event, float layerScale, float clipPitch) {
            super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.owner = owner;
            this.layerScale = layerScale;
            this.looping = false;
            this.delay = 0;
            this.pitch = clipPitch;
            this.volume = Math.max(0.015F, owner.currentGain * layerScale);
            this.x = owner.spot.x();
            this.y = owner.spot.y();
            this.z = owner.spot.z();
        }

        private void beginFadeOut(int ticks) {
            float step = 1.0F / Math.max(1, ticks);
            if (step > this.fadeOutStep) {
                this.fadeOutStep = step;
            }
        }

        private void forceStop() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public float getVolume() {
            return this.sound == null ? 0.0F : super.getVolume();
        }

        @Override
        public void tick() {
            if (fadeOutStep > 0.0F) {
                fadeEnvelope = Math.max(0.0F, fadeEnvelope - fadeOutStep);
                if (fadeEnvelope <= 0.01F) {
                    stop();
                    return;
                }
            }
            this.volume = Math.max(0.01F, owner.currentGain * layerScale * fadeEnvelope);
            this.x = owner.spot.x();
            this.y = owner.spot.y();
            this.z = owner.spot.z();
        }
    }
}
