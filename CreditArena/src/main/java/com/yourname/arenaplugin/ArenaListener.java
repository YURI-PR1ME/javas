package com.yourname.arenaplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArenaListener implements Listener {
    
    private final ArenaManager arenaManager = ArenaPlugin.getInstance().getArenaManager();
    private final Map<UUID, Location[]> playerSelections = new HashMap<>(); // 存储玩家选择的两个点
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Arena currentArena = arenaManager.getCurrentArena();
        
        if (currentArena != null && currentArena.isFighter(player)) {
            // 处理擂台内选手死亡
            Player killer = player.getKiller();
            if (killer != null && currentArena.isFighter(killer)) {
                // 被对手杀死 - 击杀者保留全部35点
                arenaManager.endMatch(killer, player, "被击杀", true);
            } else {
                // 其他原因死亡 - 对手保留20点（扣除15点）
                Player opponent = currentArena.getOpponent(player);
                if (opponent != null) {
                    arenaManager.endMatch(opponent, player, "死亡", false);
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item != null && isWand(item)) {
            event.setCancelled(true);
            
            if (event.getClickedBlock() != null) {
                handleWandUse(player, event.getClickedBlock().getLocation(), event.getAction());
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Arena currentArena = arenaManager.getCurrentArena();
        
        // 清除玩家的选择点
        playerSelections.remove(player.getUniqueId());
        
        if (currentArena != null && currentArena.isFighter(player)) {
            // 选手退出游戏 - 对手保留20点（扣除15点）
            Player opponent = currentArena.getOpponent(player);
            if (opponent != null) {
                arenaManager.endMatch(opponent, player, "退出游戏", false);
            }
        }
    }
    
    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && 
               meta.getDisplayName().equals(ChatColor.GOLD + "擂台圈地工具");
    }
    
    private void handleWandUse(Player player, Location location, Action action) {
        UUID playerId = player.getUniqueId();
        
        // 初始化选择点数组
        if (!playerSelections.containsKey(playerId)) {
            playerSelections.put(playerId, new Location[2]);
        }
        
        Location[] selections = playerSelections.get(playerId);
        
        if (action == Action.LEFT_CLICK_BLOCK) {
            // 左键选择第一个点
            selections[0] = location;
            player.sendMessage(ChatColor.GREEN + "✅ 第一个点已选择: " + 
                formatLocation(location));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // 右键选择第二个点
            selections[1] = location;
            player.sendMessage(ChatColor.GREEN + "✅ 第二个点已选择: " + 
                formatLocation(location));
        }
        
        // 检查是否两个点都已选择
        if (selections[0] != null && selections[1] != null) {
            player.sendMessage(ChatColor.YELLOW + "⚠ 两个点都已选择！使用 /arena create <名称> 创建擂台");
        }
    }
    
    private String formatLocation(Location location) {
        return String.format("X: %d, Y: %d, Z: %d", 
            (int) location.getX(), (int) location.getY(), (int) location.getZ());
    }
    
    // 获取玩家的选择点
    public Location[] getPlayerSelection(Player player) {
        return playerSelections.get(player.getUniqueId());
    }
    
    // 清除玩家的选择点
    public void clearPlayerSelection(Player player) {
        playerSelections.remove(player.getUniqueId());
    }
}
