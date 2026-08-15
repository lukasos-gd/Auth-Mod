package com.lukasosstudios.authmod;

import net.minecraft.world.phys.Vec3;

public class PlayerSession {
    public AuthState state;
    public Vec3 frozenPosition;
    public float frozenYaw;
    public float frozenPitch;
    public int ticksUntilKick;
    public int failedAttempts = 0;
    public long authenticatedAtMillis = 0;

    public PlayerSession(AuthState state, Vec3 frozenPosition, float frozenYaw, float frozenPitch, int ticksUntilKick) {
        this.state = state;
        this.frozenPosition = frozenPosition;
        this.frozenYaw = frozenYaw;
        this.frozenPitch = frozenPitch;
        this.ticksUntilKick = ticksUntilKick;
    }

    public boolean isAuthenticated() {
        return state == AuthState.AUTHENTICATED;
    }
}
