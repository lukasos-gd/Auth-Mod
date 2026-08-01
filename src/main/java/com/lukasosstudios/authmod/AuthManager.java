package com.lukasosstudios.authmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, PlayerAuthData>>() {
    }.getType();
    private static final Path DATA_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("authmod")
            .resolve("accounts.json");

    private static AuthManager instance;

    private final Map<String, PlayerAuthData> accounts;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();

    private AuthManager() {
        this.accounts = load();
    }

    public static AuthManager get() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }

    private static Map<String, PlayerAuthData> load() {
        if (Files.exists(DATA_PATH)) {
            try (Reader reader = Files.newBufferedReader(DATA_PATH, StandardCharsets.UTF_8)) {
                Map<String, PlayerAuthData> loaded = GSON.fromJson(reader, STORE_TYPE);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                AuthMod.LOGGER.error("Failed to read authmod accounts.json", e);
            }
        }
        return new HashMap<>();
    }

    private void save() {
        try {
            Files.createDirectories(DATA_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(DATA_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(accounts, STORE_TYPE, writer);
            }
        } catch (IOException e) {
            AuthMod.LOGGER.error("Failed to save authmod accounts.json", e);
        }
    }

    public boolean isRegistered(UUID uuid) {
        return accounts.containsKey(uuid.toString());
    }

    public PlayerAuthData getAccount(UUID uuid) {
        return accounts.get(uuid.toString());
    }

    public int accountCount() {
        return accounts.size();
    }

    public PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void putSession(UUID uuid, PlayerSession session) {
        sessions.put(uuid, session);
    }

    public void clearSession(UUID uuid) {
        sessions.remove(uuid);
    }

    public Map<UUID, PlayerSession> allSessions() {
        return sessions;
    }

    /** Creates a new account and marks the given session authenticated. Caller must have already validated the password. */
    public void register(UUID uuid, String username, String password, String ip) {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);
        PlayerAuthData data = new PlayerAuthData(username, hash, salt, System.currentTimeMillis());
        data.lastLoginAt = data.registeredAt;
        data.lastLoginIp = ip;
        accounts.put(uuid.toString(), data);
        save();
    }

    public void recordLogin(UUID uuid, String ip) {
        PlayerAuthData data = accounts.get(uuid.toString());
        if (data == null) {
            return;
        }
        data.lastLoginAt = System.currentTimeMillis();
        data.lastLoginIp = ip;
        save();
    }

    public boolean checkPassword(UUID uuid, String password) {
        PlayerAuthData data = accounts.get(uuid.toString());
        if (data == null) {
            return false;
        }
        return PasswordHasher.matches(password, data.salt, data.passwordHash);
    }

    public void changePassword(UUID uuid, String newPassword) {
        PlayerAuthData data = accounts.get(uuid.toString());
        if (data == null) {
            return;
        }
        String salt = PasswordHasher.generateSalt();
        data.salt = salt;
        data.passwordHash = PasswordHasher.hash(newPassword, salt);
        save();
    }

    public void unregister(UUID uuid) {
        accounts.remove(uuid.toString());
        save();
    }
}
