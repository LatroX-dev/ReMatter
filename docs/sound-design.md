# Sound design

ReMatter's sounds follow one rule: **what a device sounds like is what it visibly and logically does.** A player
who hears a machine without looking at it should be able to tell which one it is and what it is doing. This page
states the rules, describes how the pieces fit together, and explains how sound sets are stored and switched.

## Rules

- **A1. Sound follows the picture.** A sound belongs to something the device shows or does: a stroke, a sweep, a
  pulse, a latch, a flow. A device with no mechanism gets a steady sound and, at most, sparse irregular details.
- **A2. Three parts per working machine.** A quiet *bed* (the steady state: drive, liquid, air, heat or field,
  with no strokes baked in), *motion cues* (one clip per visible mechanism cycle, holding exactly the events of
  that cycle at the same offsets as the animation) and optional *accents* (irregular details where the picture is
  irregular too). The motion cue carries the identity; the bed stays underneath.
- **A3. Cues are fired by the animation, never by a timer.** The client plays a motion cue when the machine's own
  mechanism phase passes the cue's phase, and plays it at the animation tempo, so it stays locked with Speed
  Upgrades. A fixed-length loop must not contain rhythmic events, because it cannot stay in step.
- **A4. Warm, calm, distinct.** Body between 150 Hz and 1.5 kHz. Brightness only in short transients. Nothing
  sustained above 2.5 kHz, no steady pure tone above 1.2 kHz, no dense harmonic comb on a mains-like pitch
  (100 or 120 Hz), no noise used as a bed, and nothing essential below 90 Hz, where small loudspeakers stop.
  Every machine has its own register and rhythm.
- **A5. No melodies.** Variants of a clip differ in detail, not in musical pitch; a row of machines must not play
  a tune.
- **A6. One sound per meaning.** An action does not borrow the sound of another action. Inserting a cell,
  filling a canister and placing a terminal order sound different because they are different things.
- **A7. ReMatter's own sounds for ReMatter's actions.** No vanilla sound event is played for a mod action; a
  familiar vanilla sound on an unfamiliar device confuses. (Block placing and breaking, GUI clicks and bucket
  sounds are standard game behaviour and stay.)
- **A8. Earlier sets stay restorable.** A sound set is never deleted or overwritten. A new round is a new set.

## How a machine plays

`assets/rematter/machine_audio.json` describes, per machine, its bed loop (event, pitch, fades), its accents
(interval, jitter) and its motion cues:

| Field | Meaning |
| --- | --- |
| `period` | length of the mechanism cycle in animation ticks, as in the renderer |
| `phase` | point in the cycle at which the clip starts (0 to 1) |
| `stretch` | play at the animation tempo (1.0 to 1.4 with Speed Upgrades) |
| `min_drive` | the mechanism must be running at least this strongly |
| `progress_pitch` | pitch range following batch progress (the Material Synthesizer's product audibly grows) |
| `mixing` | only for recipes that mix, or only for recipes that press |
| `source` | `machine` for the bench mechanism, `chamber` for the modules of a multiblock |
| `position` | `block`, or `chamber` to emit at the centre of a formed structure |

The file is generated from the active sound set. The engine reads it when resources load.

Current mechanism timings that the cues are written against (animation ticks at tempo 1):

| Machine | Visible cycle | Period | Cue |
| --- | --- | --- | --- |
| Analyzer | scan ring down and back up | 48 | sweep whose level follows the ring's speed and whose colour follows its height |
| Decomposer | rams close, hold, open | 32 | strain, crack at closing, scatter, falling drain |
| Material Preparator | pestle accelerates down, holds, lifts | 24 | rising whirr, impact with crunch at 0.36 s, lift |
| Material Synthesizer | a pulse lands every 10 ticks | 20 | two deposits, the second a tone higher |
| Chemical Processor | liquid swaps between two columns | 80 | starts at a turning point: tink, surge, lower tink, return surge |
| Atmospheric Collector | a pulse enters the core every 12 ticks | 24 | two hollow intake puffs |
| Chemical Reactor | module beam pulses | 32 | soft field pulse |
| Preparation Reactor | Resonance Cutter blade | 24 | swish, resonant chop at 0.3 s, forming tick |
| Deep-Core Extractor | bore bit plunges and lifts | 24 | smooth rotary bore + hydraulic pressure plunge at 0.55 s (click-free) |
| Transmutation Reactor | chamber emitter beams pulse | 32 | deep magnetic convergence swell cresting at 0.70 s |
| Primal Reactor | prismatic gimbal & orbiting motes | 40 | deep gravitational orbital breath cresting at 0.95 s |
| Energy Reactor | thermal-induction power chamber pulses | 32 | deep power-vault bed + warm thermal-induction flux pulse at 0.72 s |
| Energy Matrix | silent storage multiblock | — | silent by design, like Energy Cubes and Matter Drive |

When a mechanism's timing changes in the renderer, the cue for it changes in the same change.

## Sound sets

All clips live in `artwork/sound-sets/<set>/`: `clips/**.ogg` and `set.json` with the events, the machine audio
profile, a per-clip record (checksum, loudness, measurements) and the provenance.

| Set | Contents |
| --- | --- |
| `studio-v10` | the set that shipped until 2026-11-17, archived unchanged, with the timer-accent behaviour it had |
| `workshop-v11` | beds, motion cues and dedicated interaction sounds, archived unchanged |
| `workshop-v12` | first synthetic draft of the four endgame roles, archived unchanged |
| `workshop-v13` | deep MOSS-SoundEffect v2.0 + studio builds for the four endgame roles, archived unchanged |
| `workshop-v14` | the current set: click-free Smooth Rotary Bore for Deep-Core Extractor, dedicated deep Energy Reactor role, and silent Energy Matrix |

`tools/generate_sound_assets.py` installs the selection into the resource folder and writes `sounds.json`,
`machine_audio.json` and `artwork/sound-rendered.json`. Three constants at its top select what is installed:

```python
ACTIVE_SET = "workshop-v14"
ROLE_OVERRIDES = {}      # machine role -> set, for example {"generator": "studio-v10"}
EVENT_OVERRIDES = {}     # interaction event -> set, for example {"wrench_configure": "studio-v10"}
```

Setting `ACTIVE_SET = "studio-v10"` and running the tool restores the earlier sounds together with their earlier
behaviour. A role override swaps one machine completely (loop, cues, accents and engine profile). Interaction
events that an earlier set did not have play what their action used at that time; the two that had no sound at all
keep the current one. Registered events that the selection does not use (an earlier set's accents, another set's
motion cues) are written to `sounds.json` with an empty list, so the game does not warn about them at start.

`python3 tools/generate_sound_assets.py --check` verifies that the installed files match the selection and the
recorded checksums, that every clip is mono 44.1 kHz Ogg Vorbis, that loops are seamless, audible and free of
whistles according to their recorded measurements, and that the Java registry and the tool agree on the events.

## Adding or changing a sound

1. Decide what the device shows and does (A1). If it has a mechanism, take the cycle length and the event times
   from the renderer.
2. Produce the clip so that it meets A4 and A5: mono, 44.1 kHz, loops exactly periodic with no fade in the file,
   one-shots starting at their first sample.
3. Add it to a **new** set folder (A8) with its record, set the profile entry, select it and run the generator.
4. Add a subtitle in both languages for every new event.
5. Run `python3 tools/check_all.py`.

Measurements cannot replace listening. A new or changed sound is finished when someone has heard it in the game
next to the device.
