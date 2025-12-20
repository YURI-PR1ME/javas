package com.yourname.eternalfire;

import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

public class EntityCollisionTask extends BukkitRunnable {
    
    private final EternalFire plugin;
    
    public EntityCollisionTask(EternalFire plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void run() {
        // 检查实体碰撞
        plugin.checkEntityCollisions();
    }
}
