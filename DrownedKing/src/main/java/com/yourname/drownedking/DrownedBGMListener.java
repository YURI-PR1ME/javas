package com.yourname.drownedking;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DrownedBGMListener implements Listener {
    
    private final DrownedKingPlugin plugin;
    
    public DrownedBGMListener(DrownedKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // 玩家离开旧世界
        if (plugin.getBgmPlayer() != null) {
            plugin.getBgmPlayer().onPlayerLeaveBossWorld(event.getPlayer(), event.getFrom());
        }
        // 玩家进入新世界
        if (plugin.getBgmPlayer() != null) {
            plugin.getBgmPlayer().onPlayerEnterBossWorld(event.getPlayer(), event.getPlayer().getWorld());
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时，停止其BGM
        if (plugin.getBgmPlayer() != null) {
            plugin.getBgmPlayer().onPlayerLeaveBossWorld(event.getPlayer(), event.getPlayer().getWorld());
        }
    }
}
