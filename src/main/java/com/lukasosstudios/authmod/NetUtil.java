package com.lukasosstudios.authmod;

import net.minecraft.server.level.ServerPlayer;

public final class NetUtil {
    private NetUtil() {
    }

    public static String getIp(ServerPlayer player) {
        try {
            String raw = player.connection.getRemoteAddress().toString();
            int slash = raw.indexOf('/');
            String noSlash = slash >= 0 ? raw.substring(slash + 1) : raw;
            int colon = noSlash.lastIndexOf(':');
            return colon > 0 ? noSlash.substring(0, colon) : noSlash;
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
