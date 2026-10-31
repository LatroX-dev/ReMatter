---
navigation:
  title: When a machine waits
  icon: rematter:analyzer
  parent: index.md
  position: 9
---
# When a machine waits

The status below the FE bar explains why a machine is waiting.

- **Link storage:** connect exactly one Matter Storage through face-adjacent machines or Matter Pipes. Check that the owners match, the route is loaded, and the network has no more than 64 nodes.
- **Needs FE:** supply enough energy to the processing machine. Matter Pipes do not carry FE. The machine needs the whole operation's energy available before it progresses.
- **Insert sample:** put a supported, unmodified item in the Analyzer input.
- **Needs input:** check the Decomposer's input or the selected process. For water or lava imports, leave the item input empty and supply at least 1,000 mB.
- **Check matter:** confirm the required substances and phases are present, and the resulting stock fits into storage.
- **Output full:** remove output items or drain the machine's fluid tank. Different fluid types cannot share its tank.
- **Needs casing:** complete the Industrial Reactor's eight-casing ring on the controller's own level.
- **Paused:** press Run when you are ready. An Analyzer pauses after a successful scan.
- **Data protected:** the machine is preserving storage data it cannot safely interpret. Keep the block and ask the server maintainer to restore a compatible mod or data version.

Recipe and material availability can differ between servers. Your machine's recipe selector and supported analysis results reflect the active definitions. A missing profile does not grant permission to synthesize an item.
