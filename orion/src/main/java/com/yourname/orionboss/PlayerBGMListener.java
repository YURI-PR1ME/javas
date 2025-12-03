package com.yourname.orionboss;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerBGMListener implements Listener {
    
    private final OrionBossPlugin plugin;
    
    public PlayerBGMListener(OrionBossPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        BGMPlayer bgmPlayer = plugin.getBgmPlayer();
        
        if (bgmPlayer == null) return;
        
        // 检查玩家是否从其他世界进入末地
        if (player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            // 玩家进入末地，播放BGM（如果正在播放）
            bgmPlayer.onPlayerEnterEnd(player);
        } else if (event.getFrom().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            // 玩家离开末地，停止BGM
            bgmPlayer.onPlayerLeaveEnd(player);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BGMPlayer bgmPlayer = plugin.getBgmPlayer();
        
        if (bgmPlayer != null) {
            bgmPlayer.stopBGMForPlayer(player);
        }
    }
}
