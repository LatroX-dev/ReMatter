---
navigation:
  title: When a machine waits
  icon: rematter:decomposer
  parent: index.md
  position: 12
---
# When a machine waits

Read the status and the displayed storage source before changing ingredients.

## Prepare the matrix first

For a single extraction, put the raw material in the left input and leave the other ingredient slots empty. For Matrix Blend, put Silith on the left and Argil in the middle. Alloy and ceramic mixtures use the [three-slot order](metallurgy.md). Keep room for the entire output.

The Hand Preparator needs repeated **Crank** presses instead of FE. Rhedon, Virel and Lumin require the powered Material Preparator. No preparation machine needs a Cell or matter network. The Industrial Preparator also needs its complete casing structure.

## Choose a source

Insert a readable Cell in a Matter Drive and connect the Drive to the processors. A touching row is enough. Use Matter Cables to bridge gaps. Each Drive accepts up to six Cells.

## After changing a Cell

Changing a Drive's Cell, a Drive or the cables that connect your devices restarts an unfinished batch. The status explains the reason, for example **Storage changed · batch restarts**, and work continues by itself once the network is valid. Uncommitted inputs and FE are kept.

Press **Start** on the Decomposer. On the Synthesizer, open **Choose recipe** or **Recipe**, choose the product, confirm with **Use recipe** and press **Start**. For continuous production, use **Configuration → Run nonstop**. **Stop repeat** stops that mode. If a pending job is paused, **Resume** continues the same order.

## Supply power

The Material Preparator, Sinter Furnace and chemistry processors need external FE. Matter Cables forward FE that a generator or energy store pushes into them; nothing can pull FE out of a cable. The Drive needs no FE. Hover the lightning symbol on a powered machine for its stored FE. The full batch's energy must be stored before progress advances; every batch fits the 2,400,000 FE buffer, even with four Speed Upgrades.

## Add missing material

Compare needed and available quantities and phases. A partial batch waits: nine nuggets require all nine.

For fluids, empty the Decomposer's item input and supply a complete fluid batch. The optional Analyzer needs a supported, unmodified sample and FE, but no storage.

## Unknown substances

A row named **Unknown substance** with an ID and phase belongs to a substance whose definition is missing on this server, for example after a data pack was removed. The stock stays in its Cell but cannot be processed or sent to AE2 or Refined Storage until the definition returns.

## Make room

Cells have both a total capacity and a limit of 512 substance/phase pairs. Use or export stock to free space. Swapping to an empty Cell does not move the old Cell's contents into it.

Collect the Synthesizer's output, make room in its output chest, or drain its tank. Enable Automation for hoppers and pipes. A complete batch must fit; different fluids cannot share a tank.

## Check a network or reactor

A network includes only your own machines; keep its route loaded. It supports up to 64 Drives. Check the [cable tiers](automation.md) for its total node limit. If the network grows past its weakest cable's limit, cables of that tier are left out and the devices only they reach drop off the network.

Industrial chambers have width and depth 3–9. The Industrial Preparator needs at least a **3×4 or 4×3** footprint and a height of **5–9**. Reactors need a height of **4–9**. Use Casing for the edges and corners, and across the entire Reactor floor and roof. The front controller sits one block above the base, away from the edge columns. Use at least two Ports and connect their outward faces.

Once the chamber forms, a Port can connect from its outward face. Check its configured role if a Matter Cable or other connection fails.

Check the reason shown by the controller:

- **Too short:** raise the Preparator to at least five blocks, or Reactor to four.
- **Frame, wall or Port fault:** complete the shell and provide two separate Ports away from its edges.
- **Missing or incomplete modules:** fit at least one of every required module type.
- **Wrong module or position:** each matching type can mount on the inside floor or directly under the roof. Click a support's top or bottom face. Keep the middle clear.
- **Blocked interior:** remove unrelated blocks; space between the installed floor and ceiling parts must be air.
- **Unloaded structure:** keep the chamber loaded.
- **Chambers share a port:** give each chamber its own Machine Ports. A port inside another player's chamber cannot be used.
- **Overlaps another player's chamber:** build outside the space that another player's chamber already claims.

A larger shell without additional working parts does not work faster. Pause before rebuilding.

## Preserve unreadable contents

A Cell or machine that reports protected or unsupported data holds saved data that ReMatter cannot verify. ReMatter keeps that data unchanged and does not process it. Mine the machine to carry its data along, keep the item, and report it to the server maintainer; do not discard it or craft with it.

An Analyzer may need a new scan after its sample or material profile changes. An old result cannot supply ingredients or grant a new recipe.
