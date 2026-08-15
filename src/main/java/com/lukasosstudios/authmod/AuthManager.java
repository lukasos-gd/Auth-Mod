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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Iterator;

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

    private static final int MAX_HISTORY = 20;

    public void register(UUID uuid, String username, String password, String ip) {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);
        PlayerAuthData data = new PlayerAuthData(username, hash, salt, System.currentTimeMillis());
        data.lastLoginAt = data.registeredAt;
        data.lastLoginIp = ip;
        data.loginHistory.add(new LoginRecord(data.registeredAt, ip));
        accounts.put(uuid.toString(), data);
        save();
    }

    public void recordLogin(UUID uuid, String ip) {
        PlayerAuthData data = accounts.get(uuid.toString());
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        data.lastLoginAt = now;
        data.lastLoginIp = ip;
        data.loginHistory.add(new LoginRecord(now, ip));
        while (data.loginHistory.size() > MAX_HISTORY) {
            data.loginHistory.remove(0);
        }
        save();
    }

    public List<Map.Entry<UUID, PlayerAuthData>> searchByIp(String ip) {
        List<Map.Entry<UUID, PlayerAuthData>> matches = new ArrayList<>();
        for (Map.Entry<String, PlayerAuthData> entry : accounts.entrySet()) {
            PlayerAuthData data = entry.getValue();
            boolean matched = ip.equals(data.lastLoginIp);
            if (!matched) {
                for (LoginRecord record : data.loginHistory) {
                    if (ip.equals(record.ip)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (matched) {
                matches.add(Map.entry(UUID.fromString(entry.getKey()), data));
            }
        }
        return matches;
    }

    public int purgeInactive(long cutoffMillis) {
        int removed = 0;
        Iterator<Entry<String, PlayerAuthData>> iterator = accounts.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, PlayerAuthData> entry = iterator.next();
            if (entry.getValue().lastLoginAt < cutoffMillis) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
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
