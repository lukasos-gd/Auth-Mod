package com.lukasosstudios.authmod.command;

import com.lukasosstudios.authmod.AuthManager;
import com.lukasosstudios.authmod.AuthState;
import com.lukasosstudios.authmod.PlayerAuthData;
import com.lukasosstudios.authmod.PlayerSession;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

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
                                .then(argument("newPassword", StringArgumentType.string())
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
                        })));
    }
                                       }
