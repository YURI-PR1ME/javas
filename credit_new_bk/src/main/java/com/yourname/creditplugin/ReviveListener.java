package com.yourname.creditplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.ArrayList;
import java.util.List;

public class ReviveListener implements Listener {
    
    private final CreditManager creditManager = CreditPlugin.getInstance().getCreditManager();
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        
        if (ReviveItem.isReviveStation(item)) {
            event.setCancelled(true);
            openReviveGUI(event.getPlayer());
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("选择要复活的玩家")) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.PLAYER_HEAD) {
                return;
            }
            
            Player clicker = (Player) event.getWhoClicked();
            SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
            if (meta == null || !meta.hasOwner()) return;
            
            Player target = Bukkit.getPlayer(meta.getOwner());
            if (target == null) {
                clicker.sendMessage(ChatColor.RED + "❌ 玩家不在线！");
                clicker.closeInventory();
                return;
            }
            
            // 只允许复活在地狱的玩家
            if (!creditManager.isInNether(target)) {
                clicker.sendMessage(ChatColor.RED + "❌ 该玩家不需要复活！");
                clicker.closeInventory();
                return;
            }
            
            // 计算复活花费
            int targetCredits = creditManager.getCredits(target);
            int reviveCost = Math.max(0, -targetCredits);
            
            // 执行复活
            if (creditManager.revivePlayer(clicker, target)) {
                clicker.closeInventory();
            } else {
                clicker.sendMessage(ChatColor.RED + "❌ 复活失败！需要 " + reviveCost + " 点信用点");
            }
        }
    }
    
    private void openReviveGUI(Player player) {
        List<Player> netherPlayers = new ArrayList<>();
        
        // 查找所有在地狱的玩家
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (creditManager.isInNether(online)) {
                netherPlayers.add(online);
            }
        }
        
        if (netherPlayers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "⚠ 当前没有需要复活的玩家");
            return;
        }
        
        int size = (int) Math.ceil(netherPlayers.size() / 9.0) * 9;
        size = Math.max(9, Math.min(54, size));
        
        Inventory gui = Bukkit.createInventory(null, size, "选择要复活的玩家");
        
        for (Player netherPlayer : netherPlayers) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            
            int targetCredits = creditManager.getCredits(netherPlayer);
            int reviveCost = Math.max(0, -targetCredits);
            
            meta.setDisplayName("§6" + netherPlayer.getName());
            meta.setOwningPlayer(netherPlayer);
            meta.setLore(List.of(
                "§7点击复活此玩家",
                "§c消耗 " + reviveCost + " 点信用点",
                "§e你的信用点: " + creditManager.getCredits(player),
                "§a目标点数: " + targetCredits
            ));
            
            skull.setItemMeta(meta);
            gui.addItem(skull);
        }
        
        player.openInventory(gui);
    }
}
