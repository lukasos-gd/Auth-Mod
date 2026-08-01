package com.lukasosstudios.authmod;

import com.lukasosstudios.authmod.command.AuthAdminCommand;
import com.lukasosstudios.authmod.command.AuthCommands;
import com.lukasosstudios.authmod.command.AuthConfigCommand;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class AuthMod implements ModInitializer {
    public static final String MOD_ID = "authmod";
    public static final Logger LOGGER = LoggerFactory.getLogger("AuthMod");

    private static final int EFFECT_REFRESH_TICKS = 200;
    private static final int EFFECT_DURATION_TICKS = 220;

    private static final Set<String> COMMAND_ALLOWLIST = Set.of("register", "login", "help");

    @Override
    public void onInitialize() {
        LOGGER.info("AuthMod initializing");

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            onPlayerJoin(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            AuthManager.get().clearSession(player.getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victim && isRestricted(victim)) {
                return false;
            }
            if (source.getEntity() instanceof ServerPlayer attacker && isRestricted(attacker)) {
                return false;
            }
            return true;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                return !isRestricted(serverPlayer);
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && isRestricted(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer && isRestricted(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && isRestricted(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && isRestricted(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isRestricted(sender)) {
                sender.sendSystemMessage(Component.literal("You must authenticate before chatting. Use /login or /register."));
                return false;
            }
            return true;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AuthCommands.register(dispatcher);
            AuthConfigCommand.register(dispatcher);
            AuthAdminCommand.register(dispatcher);
            lockDownCommands(dispatcher);
        });
    }

    private void lockDownCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        for (CommandNode<CommandSourceStack> child : dispatcher.getRoot().getChildren()) {
            if (COMMAND_ALLOWLIST.contains(child.getName())) {
                continue;
            }
            wrapRequirement(child);
        }
    }

    @SuppressWarnings("unchecked")
    private void wrapRequirement(CommandNode<CommandSourceStack> node) {
        try {
            Field field = CommandNode.class.getDeclaredField("requirement");
            field.setAccessible(true);
            Predicate<CommandSourceStack> original = (Predicate<CommandSourceStack>) field.get(node);
            Predicate<CommandSourceStack> wrapped = source -> {
                if (original != null && !original.test(source)) {
                    return false;
                }
                ServerPlayer player = source.getPlayer();
                return player == null || !isRestricted(player);
            };
            field.set(node, wrapped);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Could not lock down command '{}' - brigadier's CommandNode#requirement field may have changed", node.getName(), e);
        }
    }

    private void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        AuthManager manager = AuthManager.get();
        ModConfig config = ModConfig.get();

        AuthState state = manager.isRegistered(uuid) ? AuthState.PENDING_LOGIN : AuthState.PENDING_REGISTER;
        int timeoutTicks = config.timeoutAuthSeconds * 20;

        PlayerSession session = new PlayerSession(state, player.position(), player.getYRot(), player.getXRot(), timeoutTicks);
        manager.putSession(uuid, session);

        applyRestrictionEffects(player);

        if (state == AuthState.PENDING_LOGIN) {
            player.sendSystemMessage(Component.literal(
                    "This account is registered. Type /login <password> within " + config.timeoutAuthSeconds + "s."));
        } else {
            player.sendSystemMessage(Component.literal(
                    "Welcome! Register with /register <password> <confirmPassword> within " + config.timeoutAuthSeconds + "s."));
        }
    }

    private void tick(net.minecraft.server.MinecraftServer server) {
        AuthManager manager = AuthManager.get();
        Iterator<Map.Entry<UUID, PlayerSession>> iterator = manager.allSessions().entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerSession> entry = iterator.next();
            PlayerSession session = entry.getValue();
            if (session.isAuthenticated()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }

            if (player.position().distanceToSqr(session.frozenPosition) > 0.001) {
                player.teleportTo(session.frozenPosition.x, session.frozenPosition.y, session.frozenPosition.z);
            }
            player.setYRot(session.frozenYaw);
            player.setXRot(session.frozenPitch);
            player.setDeltaMovement(0, player.getDeltaMovement().y < 0 ? 0 : player.getDeltaMovement().y, 0);
            player.fallDistance = 0;

            if (session.ticksUntilKick % EFFECT_REFRESH_TICKS == 0) {
                applyRestrictionEffects(player);
            }

            if (session.ticksUntilKick % 20 == 0) {
                int secondsLeft = session.ticksUntilKick / 20;
                String hint = session.state == AuthState.PENDING_LOGIN ? "/login <password>" : "/register <password> <confirm>";
                player.displayClientMessage(Component.literal(secondsLeft + "s to " + hint), true);
            }

            session.ticksUntilKick--;
            if (session.ticksUntilKick <= 0) {
                player.connection.disconnect(Component.literal("You took too long to authenticate."));
                iterator.remove();
            }
        }
    }

    private void applyRestrictionEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_DURATION_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_DURATION_TICKS, 0, false, false, false));
    }

    public static boolean isRestricted(ServerPlayer player) {
        PlayerSession session = AuthManager.get().getSession(player.getUUID());
        return session != null && !session.isAuthenticated();
    }
}
