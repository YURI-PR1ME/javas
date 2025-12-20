package com.yourname.tyrantboss;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TyrantBGMListener implements Listener {
    
    private final TyrantBossPlugin plugin;
    
    public TyrantBGMListener(TyrantBossPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // 玩家离开旧世界
        plugin.getBgmPlayer().onPlayerLeaveBossWorld(event.getPlayer(), event.getFrom());
        // 玩家进入新世界
        plugin.getBgmPlayer().onPlayerEnterBossWorld(event.getPlayer(), event.getPlayer().getWorld());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时，停止其BGM
        plugin.getBgmPlayer().onPlayerLeaveBossWorld(event.getPlayer(), event.getPlayer().getWorld());
    }
}
