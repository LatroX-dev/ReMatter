package dev.latrox.rematter.content;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.latrox.rematter.ReMatter;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Per-player trusted list commands for {@link SecurityMode#TRUSTED}. Available to all players without OP. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class SecurityCommands {
    private SecurityCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var trust = Commands.literal("trust")
                .requires(source -> source.getEntity() instanceof net.minecraft.server.level.ServerPlayer);

        trust.then(Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            var owner = context.getSource().getPlayerOrException();
                            var target = EntityArgument.getPlayer(context, "player");
                            if (owner.getUUID().equals(target.getUUID())) {
                                context.getSource().sendFailure(Component.translatable("command.rematter.trust.self"));
                                return 0;
                            }
                            var data = SecurityTrustSavedData.get(context.getSource().getServer());
                            if (data.isTrusted(owner.getUUID(), target.getUUID())) {
                                context.getSource().sendSuccess(() -> Component.translatable("command.rematter.trust.already", target.getName()), false);
                                return 0;
                            }
                            if (!data.add(owner.getUUID(), target.getUUID(), target.getGameProfile().name())) {
                                context.getSource().sendFailure(Component.translatable("command.rematter.trust.full", SecurityTrustSavedData.MAX_TRUSTED_PER_OWNER));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.translatable("command.rematter.trust.added", target.getName()), false);
                            return 1;
                        })));

        trust.then(Commands.literal("remove")
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            var player = context.getSource().getPlayer();
                            if (player != null) {
                                var data = SecurityTrustSavedData.get(context.getSource().getServer());
                                data.trustedEntries(player.getUUID()).values().forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var owner = context.getSource().getPlayerOrException();
                            String query = StringArgumentType.getString(context, "target");
                            var server = context.getSource().getServer();
                            var data = SecurityTrustSavedData.get(server);
                            var online = server.getPlayerList().getPlayerByName(query);
                            var targetId = online != null ? java.util.Optional.of(online.getUUID()) : data.findTrusted(owner.getUUID(), query);
                            if (targetId.isEmpty() || !data.remove(owner.getUUID(), targetId.get())) {
                                context.getSource().sendFailure(Component.translatable("command.rematter.trust.not_found", query));
                                return 0;
                            }
                            String label = online != null ? online.getName().getString() : data.nameOf(targetId.get()).orElse(query);
                            context.getSource().sendSuccess(() -> Component.translatable("command.rematter.trust.removed", label), false);
                            return 1;
                        })));

        trust.then(Commands.literal("list")
                .executes(context -> {
                    var owner = context.getSource().getPlayerOrException();
                    var data = SecurityTrustSavedData.get(context.getSource().getServer());
                    var entries = data.trustedEntries(owner.getUUID());
                    if (entries.isEmpty()) {
                        context.getSource().sendSuccess(() -> Component.translatable("command.rematter.trust.empty"), false);
                    } else {
                        String names = String.join(", ", entries.values());
                        context.getSource().sendSuccess(() -> Component.translatable("command.rematter.trust.list", entries.size(), names), false);
                    }
                    if (ModList.get().isLoaded("ftbteams")) {
                        context.getSource().sendSuccess(() -> Component.translatable("command.rematter.trust.ftb_note"), false);
                    }
                    return entries.size();
                }));

        trust.then(Commands.literal("clear")
                .executes(context -> {
                    var owner = context.getSource().getPlayerOrException();
                    int removed = SecurityTrustSavedData.get(context.getSource().getServer()).clear(owner.getUUID());
                    context.getSource().sendSuccess(() -> Component.translatable("command.rematter.trust.cleared", removed), false);
                    return removed;
                }));

        var securityDefault = Commands.literal("default")
                .executes(context -> {
                    var owner = context.getSource().getPlayerOrException();
                    SecurityMode current = SecurityTrustSavedData.get(context.getSource().getServer()).defaultMode(owner.getUUID());
                    context.getSource().sendSuccess(() -> Component.translatable("command.rematter.security.default_current", current.coloredLabel()), false);
                    return 1;
                });
        for (SecurityMode mode : SecurityMode.values()) {
            securityDefault.then(Commands.literal(mode.getSerializedName())
                    .executes(context -> {
                        var owner = context.getSource().getPlayerOrException();
                        var data = SecurityTrustSavedData.get(context.getSource().getServer());
                        if (!data.setDefaultMode(owner.getUUID(), mode)) {
                            context.getSource().sendFailure(Component.translatable("command.rematter.security.unavailable"));
                            return 0;
                        }
                        context.getSource().sendSuccess(() -> Component.translatable("command.rematter.security.default_set", mode.coloredLabel()), false);
                        return 1;
                    }));
        }
        var security = Commands.literal("security")
                .requires(source -> source.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                .then(securityDefault);

        event.getDispatcher().register(Commands.literal("rematter").then(trust).then(security));
    }
}
