package dev.latrox.rematter.research;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.latrox.rematter.ReMatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator/quest tools. Grants are deliberately distinguished from earned discoveries. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ResearchCommands {
    private ResearchCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var research = Commands.literal("research").requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)));
        for (boolean grant : new boolean[]{true, false}) {
            research.then(Commands.literal(grant ? "grant" : "revoke")
                    .then(Commands.argument("players", EntityArgument.players()).then(Commands.argument("material", StringArgumentType.greedyString())
                            .suggests((context, builder) -> { ResearchData.current().families().forEach(f -> builder.suggest(f.id())); return builder.buildFuture(); })
                            .executes(context -> change(context, grant)))));
        }
        research.then(Commands.literal("grant_all").then(Commands.argument("players", EntityArgument.players()).executes(context -> {
            int count = 0; var server = context.getSource().getServer();
            for (var player : EntityArgument.getPlayers(context, "players")) for (var family : ResearchData.current().families()) if (ResearchService.adminGrant(server, player.getUUID(), family.id())) count++;
            return report(context, count, " material grants applied.");
        })));
        research.then(Commands.literal("discovery_override").then(Commands.argument("players", EntityArgument.players()).then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
            var server = context.getSource().getServer(); var players = EntityArgument.getPlayers(context, "players");
            for (var player : players) ResearchService.ledger(server).overrideDiscovery(player.getUUID(), BoolArgumentType.getBool(context, "enabled"));
            return report(context, players.size(), " Discovery prerequisite overrides updated.");
        }))));
        research.then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
            var server = context.getSource().getServer(); var player = EntityArgument.getPlayer(context, "player"); var view = ResearchService.context(server, player.getUUID()); var ledger = ResearchService.ledger(server);
            context.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + ": " + ledger.knownFamilies(view).size() + " materials, " + ledger.earnedFamilies(view) + " earned; identity " + view.identity().key()), false);
            return ledger.knownFamilies(view).size();
        })));
        event.getDispatcher().register(Commands.literal("rematter").then(research));
    }
    private static int change(CommandContext<CommandSourceStack> context, boolean grant) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "material"); if (!id.contains(":")) id = "rematter:" + id;
        var family = ResearchData.current().family(id);
        if (family.isEmpty()) { context.getSource().sendFailure(Component.literal("Unknown material family: " + id)); return 0; }
        int count = 0; var server = context.getSource().getServer();
        for (var player : EntityArgument.getPlayers(context, "players")) {
            if (grant) { if (ResearchService.adminGrant(server, player.getUUID(), family.get().id())) count++; }
            else for (var identity : ResearchService.context(server, player.getUUID()).readable()) if (ResearchService.ledger(server).revoke(identity, family.get().id())) count++;
        }
        return report(context, count, grant ? " material grants applied." : " material grants removed from the current knowledge view.");
    }
    private static int report(CommandContext<CommandSourceStack> context, int count, String suffix) {
        context.getSource().sendSuccess(() -> Component.literal(count + suffix), true); return count;
    }
}
