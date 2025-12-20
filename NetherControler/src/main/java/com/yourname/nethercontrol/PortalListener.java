package com.yourname.nethercontrol;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalListener implements Listener {
    
    private final NetherControlManager controlManager = NetherControlPlugin.getInstance().getControlManager();
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // 如果已经解锁，不进行任何限制
        if (controlManager.isUnlocked()) {
            return;
        }
        
        Player player = event.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        
        // 检查玩家是否从主世界尝试进入地狱
        if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
            // 获取目标环境（地狱）
            World.Environment targetEnvironment = event.getTo().getWorld().getEnvironment();
            
            if (targetEnvironment == World.Environment.NETHER) {
                // 修复：检查所有可能的传送原因，包括末影珍珠
                boolean shouldBlock = false;
                
                // 地狱门传送
                if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
                    shouldBlock = true;
                }
                // 末影珍珠传送（可能被用于激活地狱门）
                else if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                    shouldBlock = true;
                }
                // 其他可能绕过限制的传送方式
                else if (cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
                    shouldBlock = true;
                }
                // 插件传送（可能被其他插件用于绕过）
                else if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                    shouldBlock = true;
                }
                // 命令传送
                else if (cause == PlayerTeleportEvent.TeleportCause.COMMAND) {
                    shouldBlock = true;
                }
                
                if (shouldBlock) {
                    // 阻止从主世界进入地狱
                    event.setCancelled(true);
                    
                    String message = NetherControlPlugin.getInstance().getConfig().getString("messages.portal-blocked", 
                        "&c❌ 地狱门访问已被封锁！使用沉星物品解锁。");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
        }
        
        // 允许从地狱返回主世界（不取消事件）
        // 允许其他维度的传送
    }
}
