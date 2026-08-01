package com.lukasosstudios.authmod.command;

import com.lukasosstudios.authmod.ModConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AuthConfigCommand {
    private AuthConfigCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("authconfig")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(literal("show")
                        .executes(ctx -> {
                            ModConfig config = ModConfig.get();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "timeout-auth=" + config.timeoutAuthSeconds + "s, "
                                            + "min-password-length=" + config.minPasswordLength + ", "
                                            + "max-login-attempts=" + config.maxLoginAttempts), false);
                            return 1;
                        }))
                .then(literal("reload")
                        .executes(ctx -> {
                            ModConfig.reload();
                            ctx.getSource().sendSuccess(() -> Component.literal("authmod config.json reloaded."), true);
                            return 1;
                        }))
                .then(literal("timeout-auth")
                        .then(argument("seconds", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                    ModConfig config = ModConfig.get();
                                    config.timeoutAuthSeconds = seconds;
                                    config.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Auth timeout set to " + seconds + " seconds."), true);
                                    return 1;
                                })))
                .then(literal("min-password-length")
                        .then(argument("chars", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int chars = IntegerArgumentType.getInteger(ctx, "chars");
                                    ModConfig config = ModConfig.get();
                                    config.minPasswordLength = chars;
                                    config.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Minimum password length set to " + chars + "."), true);
                                    return 1;
                                })))
                .then(literal("max-login-attempts")
                        .then(argument("attempts", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int attempts = IntegerArgumentType.getInteger(ctx, "attempts");
                                    ModConfig config = ModConfig.get();
                                    config.maxLoginAttempts = attempts;
                                    config.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            attempts == 0
                                                    ? "Max login attempts limit disabled."
                                                    : "Max login attempts set to " + attempts + "."), true);
                                    return 1;
                                }))));
    }
}
