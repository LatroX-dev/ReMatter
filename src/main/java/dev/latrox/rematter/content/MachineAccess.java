package dev.latrox.rematter.content;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Multiplayer access to one machine. The player who placed it owns it; a machine item never carries an owner.
 * Operators may use and remove any machine. Automated fake players act only for the owner whose UUID they carry,
 * so a machine one of them places belongs to that UUID. A machine placed without any player, for example by a
 * command, belongs to the first player using it.
 */
final class MachineAccess {
    private MachineAccess() {}

    static boolean allows(@Nullable UUID owner, UUID actor, boolean operator, boolean fakePlayer) {
        Objects.requireNonNull(actor);
        if (fakePlayer) return actor.equals(owner);
        return operator || owner == null || owner.equals(actor);
    }

    /** Only a real, participating player takes over an unowned machine. */
    static boolean claims(@Nullable UUID owner, boolean fakePlayer, boolean spectator) {
        return owner == null && !fakePlayer && !spectator;
    }
}
