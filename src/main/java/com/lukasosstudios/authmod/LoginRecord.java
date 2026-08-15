package com.lukasosstudios.authmod;

public class LoginRecord {
    public long timestamp;
    public String ip;

    public LoginRecord() {
    }

    public LoginRecord(long timestamp, String ip) {
        this.timestamp = timestamp;
        this.ip = ip;
    }
}
