---
navigation:
  title: When a machine waits
  icon: rematter:decomposer
  parent: index.md
  position: 10
---
# When a machine waits

Read the status and the displayed storage source before changing ingredients.

## Prepare the matrix first

For a single extraction, put the raw material in the primary input and leave the second input empty. For Matrix Blend, put Silith in the primary input and Argil in the second. Keep room for the entire output.

The Hand Preparator needs repeated **Crank** presses instead of FE. Rhedon, Virel and Lumin require the powered Material Preparator. No preparation machine needs a Cell or matter network. The Industrial Preparator also needs its complete casing structure.

## Choose a source

For the first manual setup, insert a readable Cell in the Decomposer or Synthesizer's dedicated Cell slot.

For network production, remove the processor's local Cell and connect one Drive containing a Cell, or an original Matter Storage. An inserted local Cell takes priority even when empty, full or unreadable; the machine does not quietly take stock from somewhere else.

A Drive needs its own inserted Cell. An original Matter Storage has built-in capacity.

## Start after changing a Cell

Inserting or removing a Cell pauses the processor and resets unfinished progress. Its uncommitted input and FE are kept.

Press **Start** on the Decomposer. On the Synthesizer, choose the product and press **Make once**, or deliberately enable **Repeat**. If a pending job is paused, **Resume** continues it.

Changing the connected storage also pauses work. Confirm the intended source before resuming.

## Supply power

The Material Preparator and both processors need external FE. A Drive needs none, and Matter Pipes do not carry it. The full batch's energy must be available before progress advances.

## Add missing material

Compare needed and available quantities and phases. A partial batch waits: nine nuggets require all nine.

For fluids, empty the Decomposer's item input and supply a complete fluid batch. The optional Analyzer needs a supported, unmodified sample and FE, but no storage.

## Make room

Cells have both a total capacity and a limit of 512 substance/phase pairs. Use or export stock to free space. Swapping to an empty Cell does not move the old Cell's contents into it.

Collect the Synthesizer's output, make room in its output chest, or drain its tank. Enable Automation for hoppers and pipes. A complete batch must fit; different fluids cannot share a tank.

## Check a network or reactor

Use one Drive or Matter Storage per connected network. Keep all owners the same and the route loaded, with no more than 64 machines and pipes.

Industrial chambers are rectangular: width and depth 3–9, height 3–9 for preparation or 4–9 for reactions. Include floor and roof, Casing edges/corners and at least two Ports. Keep the whole interior empty. The front controller sits one block above the base, away from the edge columns. Connect FE and the matter network to outward Port faces, not to the controller or Glass.

## Preserve unreadable contents

Keep a Cell or machine that reports protected data. Ask the server maintainer to restore a compatible version; do not discard it or craft with it.

An Analyzer may need a new scan after its sample or material profile changes. An old result cannot supply ingredients or grant a new recipe.
