package com.yourname.creditplugin;

import org.bukkit.Bukkit; // 添加这行导入
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalListener implements Listener {
    
    private final CreditManager creditManager = CreditPlugin.getInstance().getCreditManager();
    
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        int credits = creditManager.getCredits(player);
        
        // 如果信用点为负数，阻止穿过地狱门
        if (credits < 0) {
            // 检查是否是地狱门传送
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || 
                event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
                
                // 阻止传送
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "❌ 你的信用点为负数，无法穿过传送门！");
                player.sendMessage(ChatColor.YELLOW + "💡 你需要将信用点恢复到正数才能离开地狱");
                
                // 如果玩家在主世界或末地，强制传送回地狱
                if (!isInNether(player)) {
                    teleportToNether(player);
                }
            }
        }
    }
    
    // 检查玩家是否在地狱
    private boolean isInNether(Player player) {
        return player.getWorld().getEnvironment() == World.Environment.NETHER;
    }
    
    // 传送玩家到地狱
    private void teleportToNether(Player player) {
        World nether = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NETHER)
                .findFirst()
                .orElse(null);
        
        if (nether != null) {
            // 传送到地狱的安全位置
            Location netherSpawn = nether.getSpawnLocation();
            netherSpawn.setY(nether.getHighestBlockYAt(netherSpawn) + 1);
            
            player.teleport(netherSpawn);
            player.sendMessage(ChatColor.RED + "⚡ 你被强制传送回地狱！");
        }
    }
}
