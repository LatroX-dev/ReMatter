package dev.latrox.rematter.content;

import dev.latrox.rematter.compat.TeamAccess;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

/**
 * Multiplayer access control for ReMatter devices and shared networks.
 * Devices default to {@link SecurityMode#PUBLIC} (like Mekanism, AE2 and RS); only the owner or an operator may
 * change the security mode. {@link SecurityMode#TRUSTED} grants access to FTB Teams teammates and players added via
 * {@code /rematter trust}; {@link SecurityMode#PRIVATE} restricts access to the owner and server operators.
 */
public final class MachineAccess {
    private MachineAccess() {}

    /** Pure check for whether {@code actor} may operate, configure, wrench, or remove a device. */
    public static boolean allows(@Nullable UUID owner, SecurityMode mode, UUID actor,
                                 boolean operator, boolean fakePlayer, boolean trustedOrTeam) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(actor);
        if (fakePlayer) {
            if (owner == null) return false;
            if (actor.equals(owner)) return true;
            return switch (mode) {
                case PUBLIC -> true;
                case TRUSTED -> trustedOrTeam;
                case PRIVATE -> false;
            };
        }
        if (operator || owner == null || owner.equals(actor)) return true;
        return switch (mode) {
            case PUBLIC -> true;
            case TRUSTED -> trustedOrTeam;
            case PRIVATE -> false;
        };
    }

    /** Convenience check using live player permissions, FTB Teams membership, and the server trusted list. */
    public static boolean allows(@Nullable Level level, @Nullable UUID owner, SecurityMode mode, Player player) {
        boolean fake = player instanceof FakePlayer;
        boolean operator = !fake && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        boolean trusted = mode == SecurityMode.TRUSTED && owner != null && !owner.equals(player.getUUID())
                && isTrusted(level, owner, player.getUUID());
        return allows(owner, mode, player.getUUID(), operator, fake, trusted);
    }

    /** Only the device's owner or a real server operator may change its {@link SecurityMode}. */
    public static boolean canManage(@Nullable UUID owner, UUID actor, boolean operator, boolean fakePlayer) {
        Objects.requireNonNull(actor);
        if (fakePlayer) return false;
        return operator || owner == null || owner.equals(actor);
    }

    public static boolean canManage(@Nullable UUID owner, Player player) {
        boolean fake = player instanceof FakePlayer;
        boolean operator = !fake && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return canManage(owner, player.getUUID(), operator, fake);
    }

    /** Whether {@code actor} is trusted by {@code owner} via FTB Teams or {@link SecurityTrustSavedData}. */
    public static boolean isTrusted(@Nullable Level level, @Nullable UUID owner, @Nullable UUID actor) {
        if (owner == null || actor == null) return false;
        if (owner.equals(actor)) return true;
        if (TeamAccess.shared(owner, actor)) return true;
        return level instanceof ServerLevel server && SecurityTrustSavedData.isTrusted(server, owner, actor);
    }

    /**
     * Pure network-linking check between two owned nodes/cables. Same owner always connects; different owners
     * connect when both sides permit the partner's owner ({@link SecurityMode#PRIVATE} isolates completely).
     */
    public static boolean canConnect(@Nullable UUID ownerA, SecurityMode modeA,
                                     @Nullable UUID ownerB, SecurityMode modeB,
                                     boolean aTrustsB, boolean bTrustsA) {
        Objects.requireNonNull(modeA);
        Objects.requireNonNull(modeB);
        if (ownerA == null || ownerB == null) return false;
        if (ownerA.equals(ownerB)) return true;
        return permitsPartner(modeA, aTrustsB) && permitsPartner(modeB, bTrustsA);
    }

    public static boolean canConnect(@Nullable Level level,
                                     @Nullable UUID ownerA, SecurityMode modeA,
                                     @Nullable UUID ownerB, SecurityMode modeB) {
        if (ownerA == null || ownerB == null) return false;
        if (ownerA.equals(ownerB)) return true;
        boolean aTrustsB = modeA == SecurityMode.TRUSTED && isTrusted(level, ownerA, ownerB);
        boolean bTrustsA = modeB == SecurityMode.TRUSTED && isTrusted(level, ownerB, ownerA);
        return canConnect(ownerA, modeA, ownerB, modeB, aTrustsB, bTrustsA);
    }

    private static boolean permitsPartner(SecurityMode mode, boolean trusted) {
        return switch (mode) {
            case PUBLIC -> true;
            case TRUSTED -> trusted;
            case PRIVATE -> false;
        };
    }

    /** Resolves the initial security mode for a newly placed device from the placing player's personal default. */
    public static SecurityMode placementDefault(@Nullable Level level, @Nullable LivingEntity placer) {
        if (!(placer instanceof Player player) || player instanceof FakePlayer) return SecurityMode.PUBLIC;
        return SecurityTrustSavedData.defaultMode(level, player.getUUID());
    }

    /** Only a real, participating player takes over an unowned machine. */
    public static boolean claims(@Nullable UUID owner, boolean fakePlayer, boolean spectator) {
        return owner == null && !fakePlayer && !spectator;
    }
}
