package com.lukasosstudios.authmod;

public class PlayerAuthData {
    public String username;
    public String passwordHash;
    public String salt;
    public long registeredAt;

    public PlayerAuthData() {
    }

    public PlayerAuthData(String username, String passwordHash, String salt, long registeredAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.registeredAt = registeredAt;
    }
}
