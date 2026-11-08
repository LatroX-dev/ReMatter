package dev.latrox.rematter.content;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Remote menus are valid only while the player stands at their terminal and its full route remains loaded. */
final class TerminalNetwork {
    private TerminalNetwork() {}

    static boolean isValidAnchor(MachineBlockEntity terminal, Player player) {
        return !player.isSpectator() && terminal.kind() == MachineKind.TERMINAL
                && terminal.networkLive() && terminal.getLevel() == player.level()
                && terminal.canAccess(player) && Container.stillValidBlockEntity(terminal, player);
    }

    /** A deep check re-reads every node of the route; requests that act on a device use it. */
    static List<MachineBlockEntity> devices(MachineBlockEntity terminal, Player player, boolean deep) {
        if (!isValidAnchor(terminal, player)) return List.of();
        return terminal.matterNetwork(deep).manageableMachines();
    }

    static boolean canManage(MachineBlockEntity terminal, MachineBlockEntity device, Player player) {
        return isValidAnchor(terminal, player) && device.networkLive() && device.getLevel() == player.level()
                && device.canAccess(player) && terminal.matterNetwork(false).canManage(device);
    }

    static @Nullable MachineBlockEntity resolve(MachineBlockEntity terminal, Player player, BlockPos position) {
        return devices(terminal, player, true).stream().filter(machine -> machine.getBlockPos().equals(position))
                .findFirst().orElse(null);
    }

    static boolean open(MachineBlockEntity terminal, MachineBlockEntity device, ServerPlayer player) {
        return player.openMenu(new SimpleMenuProvider(
                (id, inventory, user) -> canManage(terminal, device, user) ? new MachineMenu(id, inventory, device, terminal) : null,
                device.getDisplayName())).isPresent();
    }
}
