package com.yourname.creditsurveillance;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SurveillanceListener implements Listener {
    
    private final CreditSurveillance plugin;
    
    public SurveillanceListener(CreditSurveillance plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 玩家加入时立即检查监管状态
        plugin.getSurveillanceManager().updatePlayerSurveillance(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 玩家退出时清理数据（但保留在map中供稍后清理）
        plugin.getPlayerData(player).removeAllGuardians();
        
        // 定期清理离线玩家数据
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.cleanupOfflinePlayers();
        }, 100L); // 5秒后清理
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        
        // 如果是被玩家杀死的，处理监管者减少
        if (killer != null && killer != player) {
            plugin.getSurveillanceManager().handlePlayerKill(killer);
        }
        
        // 玩家死亡时移除所有监管者
        plugin.getPlayerData(player).removeAllGuardians();
    }
}
