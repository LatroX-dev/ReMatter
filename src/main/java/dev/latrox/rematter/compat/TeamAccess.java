package dev.latrox.rematter.compat;

import java.util.UUID;
import net.neoforged.fml.ModList;

/** Optional team membership only. Ownership and network grouping remain unchanged. No cached memberships. */
public final class TeamAccess {
    private TeamAccess() {}
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
