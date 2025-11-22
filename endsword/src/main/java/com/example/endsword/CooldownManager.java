package com.example.endsword;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class CooldownManager {
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private static final long THUNDERSTORM_COOLDOWN = 30 * 1000; // 30秒，以毫秒为单位

    public void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isOnCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        
        long lastUsed = cooldowns.get(player.getUniqueId());
        return System.currentTimeMillis() - lastUsed < THUNDERSTORM_COOLDOWN;
    }

    public long getRemainingCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) {
            return 0;
        }
        
        long lastUsed = cooldowns.get(player.getUniqueId());
        long elapsed = System.currentTimeMillis() - lastUsed;
        long remaining = THUNDERSTORM_COOLDOWN - elapsed;
        
        return Math.max(0, remaining) / 1000; // 返回剩余秒数
    }
}
