package dev.latrox.rematter.content;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Unifies a wired Matter Terminal block and a handheld/Curios Wireless Matter Terminal session. */
sealed interface TerminalAnchor permits TerminalAnchor.Wired, TerminalAnchor.Wireless {
    MachineMatterNetwork matterNetwork(boolean deep);
    @Nullable UUID owner();

    record Wired(MachineBlockEntity terminal) implements TerminalAnchor {
        @Override public MachineMatterNetwork matterNetwork(boolean deep) {
            return terminal.matterNetwork(deep);
        }
        @Override public @Nullable UUID owner() {
            return terminal.networkOwner();
        }
    }

    record Wireless(WirelessAccessPointBlockEntity accessPoint, int lockedPlayerSlot,
                    @Nullable String curioSlot, int curioIndex) implements TerminalAnchor {
        Wireless(WirelessAccessPointBlockEntity accessPoint, int lockedPlayerSlot) {
            this(accessPoint, lockedPlayerSlot, null, -1);
        }

        ItemStack resolveStack(Player player) {
            if (curioSlot != null && curioIndex >= 0) {
                return dev.latrox.rematter.compat.CuriosSupport.curioStack(player, curioSlot, curioIndex);
            }
            if (lockedPlayerSlot < 0 || lockedPlayerSlot >= player.getInventory().getContainerSize()) return ItemStack.EMPTY;
            return player.getInventory().getItem(lockedPlayerSlot);
        }

        @Override public MachineMatterNetwork matterNetwork(boolean deep) {
            return accessPoint.matterNetwork(deep);
        }

        @Override public @Nullable UUID owner() {
            return accessPoint.owner();
        }
    }
}
