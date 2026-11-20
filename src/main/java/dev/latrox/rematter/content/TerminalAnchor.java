package dev.latrox.rematter.content;

/** Unifies a wired Matter Terminal block and a handheld Wireless Matter Terminal session. */
sealed interface TerminalAnchor permits TerminalAnchor.Wired, TerminalAnchor.Wireless {
    MachineMatterNetwork matterNetwork(boolean deep);

    record Wired(MachineBlockEntity terminal) implements TerminalAnchor {
        @Override public MachineMatterNetwork matterNetwork(boolean deep) {
            return terminal.matterNetwork(deep);
        }
    }

    record Wireless(WirelessAccessPointBlockEntity accessPoint, int lockedPlayerSlot) implements TerminalAnchor {
        @Override public MachineMatterNetwork matterNetwork(boolean deep) {
            return accessPoint.matterNetwork(deep);
        }
    }
}
