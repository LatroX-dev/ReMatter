package dev.latrox.rematter.content;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Remote menus stay valid only while the player's wired or wireless terminal anchor and route remain live. */
final class TerminalNetwork {
    private TerminalNetwork() {}

    static boolean isValidAnchor(MachineBlockEntity terminal, Player player) {
        return isValidAnchor(new TerminalAnchor.Wired(terminal), player);
    }

    static boolean isValidAnchor(TerminalAnchor anchor, Player player) {
        if (player.isSpectator()) return false;
        return switch (anchor) {
            case TerminalAnchor.Wired(var terminal) -> terminal.kind() == MachineKind.TERMINAL
                    && terminal.networkLive() && terminal.getLevel() == player.level()
                    && terminal.canAccess(player) && Container.stillValidBlockEntity(terminal, player);
            case TerminalAnchor.Wireless(var ap, int lockedSlot) -> {
                if (!ap.networkLive() || ap.getLevel() != player.level() || !ap.powered() || !ap.canAccess(player)
                        || !ap.withinRange(player)) {
                    yield false;
                }
                if (lockedSlot < 0 || lockedSlot >= player.getInventory().getContainerSize()) yield false;
                ItemStack held = player.getInventory().getItem(lockedSlot);
                if (!(held.getItem() instanceof WirelessMatterTerminalItem)) yield false;
                var link = held.get(WirelessTerminalData.LINK.get());
                var target = link == null ? null : link.target().orElse(null);
                yield target != null && target.dimension().equals(player.level().dimension())
                        && target.pos().equals(ap.getBlockPos());
            }
        };
    }

    /** A deep check re-reads every node of the route; requests that act on a device use it. */
    static List<MachineBlockEntity> devices(TerminalAnchor anchor, Player player, boolean deep) {
        if (!isValidAnchor(anchor, player)) return List.of();
        return anchor.matterNetwork(deep).manageableMachines();
    }

    static boolean canManage(TerminalAnchor anchor, MachineBlockEntity device, Player player) {
        return isValidAnchor(anchor, player) && device.networkLive() && device.getLevel() == player.level()
                && device.canAccess(player) && anchor.matterNetwork(false).canManage(device);
    }

    static @Nullable MachineBlockEntity resolve(TerminalAnchor anchor, Player player, BlockPos position) {
        return devices(anchor, player, true).stream().filter(machine -> machine.getBlockPos().equals(position))
                .findFirst().orElse(null);
    }

    static boolean open(TerminalAnchor anchor, MachineBlockEntity device, ServerPlayer player) {
        int lockedSlot = -1;
        if (anchor instanceof TerminalAnchor.Wireless wireless) {
            lockedSlot = wireless.lockedPlayerSlot();
            ItemStack held = player.getInventory().getItem(lockedSlot);
            if (WirelessMatterTerminalItem.energy(held) <= 0) return false;
        }
        int finalLockedSlot = lockedSlot;
        return player.openMenu(new SimpleMenuProvider(
                (id, inventory, user) -> canManage(anchor, device, user) ? new MachineMenu(id, inventory, device, anchor) : null,
                device.getDisplayName()),
                buf -> MachineMenu.writeMachineInit(buf, device, true, finalLockedSlot)).isPresent();
    }

    static boolean openRoot(TerminalAnchor anchor, ServerPlayer player) {
        if (!isValidAnchor(anchor, player)) return false;
        return switch (anchor) {
            case TerminalAnchor.Wired(var terminal) -> player.openMenu(terminal,
                    buf -> MachineMenu.writeMachineInit(buf, terminal, false, -1)).isPresent();
            case TerminalAnchor.Wireless wireless -> player.openMenu(new SimpleMenuProvider(
                    (id, inventory, user) -> isValidAnchor(wireless, user) ? new MachineMenu(id, inventory, wireless) : null,
                    Component.translatable("item.rematter.wireless_matter_terminal")),
                    buf -> MachineMenu.writeWirelessInit(buf, wireless)).isPresent();
        };
    }
}
