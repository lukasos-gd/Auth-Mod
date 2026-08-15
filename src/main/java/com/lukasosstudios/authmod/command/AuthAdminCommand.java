package com.lukasosstudios.authmod.command;

import com.lukasosstudios.authmod.AuthManager;
import com.lukasosstudios.authmod.AuthState;
import com.lukasosstudios.authmod.LoginRecord;
import com.lukasosstudios.authmod.PlayerAuthData;
import com.lukasosstudios.authmod.PlayerSession;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AuthAdminCommand {
    private AuthAdminCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("authadmin")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(literal("unregister")
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    AuthManager manager = AuthManager.get();
                                    manager.unregister(target.getUUID());
                                    PlayerSession session = manager.getSession(target.getUUID());
                                    if (session != null) {
                                        session.state = AuthState.PENDING_REGISTER;
                                        session.failedAttempts = 0;
                                        target.sendSystemMessage(Component.literal(
                                                "An admin unregistered your account. Use /register <password> <confirmPassword> again."));
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Unregistered " + target.getName().getString() + "."), true);
                                    return 1;
                                })))
                .then(literal("resetpassword")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("newPassword", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            String newPassword = StringArgumentType.getString(ctx, "newPassword");
                                            AuthManager manager = AuthManager.get();

                                            if (!manager.isRegistered(target.getUUID())) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        target.getName().getString() + " is not registered."));
                                                return 0;
                                            }

                                            manager.changePassword(target.getUUID(), newPassword);
                                            target.sendSystemMessage(Component.literal(
                                                    "An admin reset your password. Log in again with the new one."));
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Password reset for " + target.getName().getString() + "."), true);
                                            return 1;
                                        }))))
                .then(literal("forcelogin")
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    AuthManager manager = AuthManager.get();
                                    PlayerSession session = manager.getSession(target.getUUID());

                                    if (session == null || session.isAuthenticated()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                target.getName().getString() + " is already authenticated."));
                                        return 0;
                                    }

                                    session.state = AuthState.AUTHENTICATED;
                                    session.authenticatedAtMillis = System.currentTimeMillis();
                                    target.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
                                    target.removeEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
                                    target.sendSystemMessage(Component.literal("An admin has authenticated you."));
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Force-logged-in " + target.getName().getString() + "."), true);
                                    return 1;
                                })))
                .then(literal("info")
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    AuthManager manager = AuthManager.get();
                                    PlayerAuthData data = manager.getAccount(target.getUUID());

                                    if (data == null) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                target.getName().getString() + " is not registered."), false);
                                        return 1;
                                    }

                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            data.username + " | registered " + AuthCommands.formatTimestamp(data.registeredAt)
                                                    + " | last login " + AuthCommands.formatTimestamp(data.lastLoginAt)
                                                    + (data.lastLoginIp != null ? " from " + data.lastLoginIp : "")), false);
                                    return 1;
                                })))
                .then(literal("list")
                        .executes(ctx -> {
                            AuthManager manager = AuthManager.get();
                            StringBuilder pending = new StringBuilder();
                            int pendingCount = 0;
                            for (Map.Entry<UUID, PlayerSession> entry : manager.allSessions().entrySet()) {
                                if (!entry.getValue().isAuthenticated()) {
                                    ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(entry.getKey());
                                    if (p != null) {
                                        if (pendingCount > 0) {
                                            pending.append(", ");
                                        }
                                        pending.append(p.getName().getString())
                                                .append(" (").append(entry.getValue().ticksUntilKick / 20).append("s left)");
                                        pendingCount++;
                                    }
                                }
                            }
                            final int finalPendingCount = pendingCount;
                            final String pendingList = pending.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    manager.accountCount() + " registered account(s). "
                                            + finalPendingCount + " online player(s) not yet authenticated"
                                            + (finalPendingCount > 0 ? ": " + pendingList : ".")), false);
                            return 1;
                        }))
                .then(literal("sessions")
                        .executes(ctx -> {
                            AuthManager manager = AuthManager.get();
                            StringBuilder sb = new StringBuilder();
                            int count = 0;
                            for (Map.Entry<UUID, PlayerSession> entry : manager.allSessions().entrySet()) {
                                PlayerSession session = entry.getValue();
                                if (!session.isAuthenticated()) {
                                    continue;
                                }
                                ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(entry.getKey());
                                if (p == null) {
                                    continue;
                                }
                                PlayerAuthData data = manager.getAccount(entry.getKey());
                                if (count > 0) {
                                    sb.append("\n");
                                }
                                sb.append(p.getName().getString())
                                        .append(" - authenticated ").append(AuthCommands.formatTimestamp(session.authenticatedAtMillis))
                                        .append(data != null && data.lastLoginIp != null ? " from " + data.lastLoginIp : "");
                                count++;
                            }
                            final int finalCount = count;
                            final String finalSb = sb.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    finalCount + " authenticated session(s)."
                                            + (finalCount > 0 ? "\n" + finalSb : "")), false);
                            return 1;
                        }))
                .then(literal("searchip")
                        .then(argument("ip", StringArgumentType.word())
                                .executes(ctx -> {
                                    String ip = StringArgumentType.getString(ctx, "ip");
                                    List<Map.Entry<UUID, PlayerAuthData>> matches = AuthManager.get().searchByIp(ip);

                                    if (matches.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "No accounts found with IP " + ip + "."), false);
                                        return 1;
                                    }

                                    StringBuilder sb = new StringBuilder();
                                    for (Map.Entry<UUID, PlayerAuthData> match : matches) {
                                        if (sb.length() > 0) {
                                            sb.append("\n");
                                        }
                                        sb.append(match.getValue().username)
                                                .append(" (last login ")
                                                .append(AuthCommands.formatTimestamp(match.getValue().lastLoginAt))
                                                .append(")");
                                    }
                                    final String result = sb.toString();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            matches.size() + " account(s) matched IP " + ip + ":\n" + result), false);
                                    return 1;
                                })))
                .then(literal("history")
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    PlayerAuthData data = AuthManager.get().getAccount(target.getUUID());

                                    if (data == null) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                target.getName().getString() + " is not registered."));
                                        return 0;
                                    }

                                    if (data.loginHistory.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "No login history recorded for " + data.username + "."), false);
                                        return 1;
                                    }

                                    StringBuilder sb = new StringBuilder();
                                    for (int i = data.loginHistory.size() - 1; i >= 0; i--) {
                                        LoginRecord record = data.loginHistory.get(i);
                                        sb.append(AuthCommands.formatTimestamp(record.timestamp))
                                                .append(" - ").append(record.ip);
                                        if (i > 0) {
                                            sb.append("\n");
                                        }
                                    }
                                    final String result = sb.toString();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Login history for " + data.username + " (newest first):\n" + result), false);
                                    return 1;
                                })))
                .then(literal("purge")
                        .then(argument("days", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int days = IntegerArgumentType.getInteger(ctx, "days");
                                    long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
                                    int removed = AuthManager.get().purgeInactive(cutoff);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Purged " + removed + " account(s) inactive for " + days + "+ days."), true);
                                    return 1;
                                })));
    }
                                                    }
