package dev.latrox.rematter.compat;

import java.util.UUID;
import java.util.Optional;
import net.neoforged.fml.ModList;

/** Optional team membership only. Ownership and network grouping remain unchanged. No cached memberships. */
public final class TeamAccess {
    private TeamAccess() {}
    /** The current authoritative party/server team, including offline owners; personal FTB teams add no shared identity. */
    public static Optional<UUID> currentTeam(UUID player) {
        if (player == null || !ModList.get().isLoaded("ftbteams")) return Optional.empty();
        try {
            var apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            var api = apiClass.getMethod("api").invoke(null);
            var apiContract = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            if (!Boolean.TRUE.equals(apiContract.getMethod("isManagerLoaded").invoke(api))) return Optional.empty();
            var manager = apiContract.getMethod("getManager").invoke(api);
            var managerContract = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            var team = (Optional<?>) managerContract.getMethod("getTeamForPlayerID", UUID.class).invoke(manager, player);
            if (team.isEmpty()) return Optional.empty();
            var contract = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            if (Boolean.TRUE.equals(contract.getMethod("isPlayerTeam").invoke(team.get()))) return Optional.empty();
            return Optional.of((UUID) contract.getMethod("getTeamId").invoke(team.get()));
        } catch (ReflectiveOperationException | LinkageError unavailable) { return Optional.empty(); }
    }
    public static boolean shared(UUID owner, UUID actor) {
        if(owner==null||!ModList.get().isLoaded("ftbteams"))return false;
        try {
            var apiClass=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            var api=apiClass.getMethod("api").invoke(null);
            var apiContract=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            if(!Boolean.TRUE.equals(apiContract.getMethod("isManagerLoaded").invoke(api)))return false;
            var manager=apiContract.getMethod("getManager").invoke(api);
            var contract=Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            return Boolean.TRUE.equals(contract.getMethod("arePlayersInSameTeam",UUID.class,UUID.class).invoke(manager,owner,actor));
        } catch(ReflectiveOperationException|LinkageError unavailable) { return false; }
    }
}
