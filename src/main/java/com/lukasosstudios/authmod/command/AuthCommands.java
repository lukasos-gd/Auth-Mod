package com.lukasosstudios.authmod.command;

import com.lukasosstudios.authmod.AuthManager;
import com.lukasosstudios.authmod.AuthMod;
import com.lukasosstudios.authmod.AuthState;
import com.lukasosstudios.authmod.ModConfig;
import com.lukasosstudios.authmod.PlayerSession;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AuthCommands {
    private AuthCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("register")
                .then(argument("password", PasswordArgumentType.password())
                        .then(argument("confirmPassword", PasswordArgumentType.password())
                                .executes(ctx -> handleRegister(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "password"),
                                        StringArgumentType.getString(ctx, "confirmPassword"))))));

        dispatcher.register(literal("login")
                .then(argument("password", PasswordArgumentType.password())
                        .executes(ctx -> handleLogin(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "password")))));

        dispatcher.register(literal("changepassword")
                .then(argument("oldPassword", PasswordArgumentType.password())
                        .then(argument("newPassword", PasswordArgumentType.password())
                                .executes(ctx -> handleChangePassword(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "oldPassword"),
                                        StringArgumentType.getString(ctx, "newPassword"))))));

        dispatcher.register(literal("unregister")
                .then(argument("password", PasswordArgumentType.password())
                        .executes(ctx -> handleUnregister(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "password")))));

        dispatcher.register(literal("authinfo")
                .executes(ctx -> handleAuthInfo(ctx.getSource())));
    }

    private static int handleRegister(CommandSourceStack source, String password, String confirmPassword) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuthManager manager = AuthManager.get();
        PlayerSession session = manager.getSession(player.getUUID());

        if (session == null || session.state != AuthState.PENDING_REGISTER) {
            source.sendFailure(Component.literal("This account is already registered. Use /login instead."));
            return 0;
        }

        if (!password.equals(confirmPassword)) {
            source.sendFailure(Component.literal("Passwords do not match."));
            return 0;
        }

        int minLength = ModConfig.get().minPasswordLength;
        if (password.length() < minLength) {
            source.sendFailure(Component.literal("Password must be at least " + minLength + " characters."));
            return 0;
        }

        manager.register(player.getUUID(), player.getName().getString(), password, com.lukasosstudios.authmod.NetUtil.getIp(player));
        session.state = AuthState.AUTHENTICATED;
        clearRestrictionEffects(player);
        source.sendSuccess(() -> Component.literal("Registered and logged in! Welcome, " + player.getName().getString() + "."), false);
        return 1;
    }

    private static int handleLogin(CommandSourceStack source, String password) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuthManager manager = AuthManager.get();
        PlayerSession session = manager.getSession(player.getUUID());

        if (session == null || session.state != AuthState.PENDING_LOGIN) {
            source.sendFailure(Component.literal("You are not awaiting login. Are you already logged in?"));
            return 0;
        }

        if (manager.checkPassword(player.getUUID(), password)) {
            session.state = AuthState.AUTHENTICATED;
            manager.recordLogin(player.getUUID(), com.lukasosstudios.authmod.NetUtil.getIp(player));
            clearRestrictionEffects(player);
            source.sendSuccess(() -> Component.literal("Login successful. Welcome back, " + player.getName().getString() + "!"), false);
            return 1;
        }

        session.failedAttempts++;
        int maxAttempts = ModConfig.get().maxLoginAttempts;
        if (maxAttempts > 0 && session.failedAttempts >= maxAttempts) {
            player.connection.disconnect(Component.literal("Too many failed login attempts."));
            return 0;
        }

        if (maxAttempts > 0) {
            int remaining = maxAttempts - session.failedAttempts;
            source.sendFailure(Component.literal("Incorrect password. " + remaining + " attempt(s) remaining before kick."));
        } else {
            source.sendFailure(Component.literal("Incorrect password."));
        }
        return 0;
    }

    private static int handleChangePassword(CommandSourceStack source, String oldPassword, String newPassword) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuthManager manager = AuthManager.get();
        PlayerSession session = manager.getSession(player.getUUID());

        if (session == null || session.state != AuthState.AUTHENTICATED) {
            source.sendFailure(Component.literal("You must be logged in to change your password."));
            return 0;
        }

        if (!manager.checkPassword(player.getUUID(), oldPassword)) {
            source.sendFailure(Component.literal("Incorrect current password."));
            return 0;
        }

        int minLength = ModConfig.get().minPasswordLength;
        if (newPassword.length() < minLength) {
            source.sendFailure(Component.literal("Password must be at least " + minLength + " characters."));
            return 0;
        }

        manager.changePassword(player.getUUID(), newPassword);
        source.sendSuccess(() -> Component.literal("Password changed."), false);
        return 1;
    }

    private static int handleUnregister(CommandSourceStack source, String password) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuthManager manager = AuthManager.get();
        PlayerSession session = manager.getSession(player.getUUID());

        if (session == null || session.state != AuthState.AUTHENTICATED) {
            source.sendFailure(Component.literal("You must be logged in to unregister."));
            return 0;
        }

        if (!manager.checkPassword(player.getUUID(), password)) {
            source.sendFailure(Component.literal("Incorrect password."));
            return 0;
        }

        manager.unregister(player.getUUID());
        source.sendSuccess(() -> Component.literal("Account unregistered. You'll need to /register again next join."), false);
        return 1;
    }

    private static int handleAuthInfo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuthManager manager = AuthManager.get();
        com.lukasosstudios.authmod.PlayerAuthData data = manager.getAccount(player.getUUID());

        if (data == null) {
            source.sendFailure(Component.literal("You are not registered yet. Use /register <password> <confirmPassword>."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Account: " + data.username), false);
        source.sendSuccess(() -> Component.literal("Registered: " + formatTimestamp(data.registeredAt)), false);
        source.sendSuccess(() -> Component.literal("Last login: " + formatTimestamp(data.lastLoginAt)
                + (data.lastLoginIp != null ? " from " + data.lastLoginIp : "")), false);
        return 1;
    }

    static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return "never";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(epochMillis));
    }

    private static void clearRestrictionEffects(ServerPlayer player) {
        player.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
        player.removeEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
    }
}
