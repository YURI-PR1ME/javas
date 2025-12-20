package com.yourname.orionboss;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class ApostleListener implements Listener {
    private final OrionBossPlugin plugin;

    public ApostleListener(OrionBossPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onApostleDamage(EntityDamageByEntityEvent event) {
        // 可以在这里添加使徒受到伤害时的特殊效果
    }

    @EventHandler
    public void onMirrorDeath(EntityDeathEvent event) {
        // 镜像死亡时的特殊效果可以在这里添加
    }
}
