package com.yourname.assassinplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class AssassinGUIListener implements Listener {
    
    private final AssassinManager assassinManager = AssassinPlugin.getInstance().getAssassinManager();
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();
        
        // 只处理我们的GUI
        if (!title.equals("§8暗网买凶系统") && 
            !title.startsWith("§8选择杀手等级 - ") && 
            !title.equals("§8确认合约")) {
            return;
        }
        
        // 如果点击的是GUI外部（玩家背包），不取消事件
        if (event.getRawSlot() >= inventory.getSize()) {
            return;
        }
        
        event.setCancelled(true);
        
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // 主菜单
        if (title.equals("§8暗网买凶系统")) {
            handleMainMenuClick(player, clicked);
        }
        // 等级选择菜单
        else if (title.startsWith("§8选择杀手等级 - ")) {
            handleTierSelectionClick(player, title, clicked);
        }
        // 确认菜单
        else if (title.equals("§8确认合约")) {
            handleConfirmationClick(player, clicked, inventory);
        }
    }
    
    private void handleMainMenuClick(Player player, ItemStack clicked) {
        // 点击玩家头颅
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.hasOwner()) {
                Player target = Bukkit.getPlayer(meta.getOwner());
                if (target != null && target != player) {
                    AssassinGUI.openTierSelection(player, target);
                } else {
                    player.sendMessage("§c❌ 目标玩家不在线");
                    player.closeInventory();
                }
            }
        }
    }
    
    private void handleTierSelectionClick(Player player, String title, ItemStack clicked) {
        String targetName = title.replace("§8选择杀手等级 - ", "");
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage("§c❌ 目标玩家已离线");
            player.closeInventory();
            return;
        }
        
        // 点击返回按钮
        if (clicked.getType() == Material.ARROW && clicked.getItemMeta().getDisplayName().equals("§7返回")) {
            AssassinGUI.openMainMenu(player);
            return;
        }
        
        // 点击等级选项
        int tier = -1;
        if (clicked.getType() == Material.IRON_SWORD) tier = 1;
        else if (clicked.getType() == Material.DIAMOND_SWORD) tier = 2;
        else if (clicked.getType() == Material.BOW) tier = 3;
        
        if (tier != -1) {
            AssassinGUI.openConfirmation(player, target, tier);
        }
    }
    
    private void handleConfirmationClick(Player player, ItemStack clicked, Inventory inventory) {
        if (clicked.getType() == Material.LIME_WOOL && clicked.getItemMeta().getDisplayName().equals("§a✅ 确认发布合约")) {
            // 确认发布合约
            ItemStack targetInfo = inventory.getItem(4);
            if (targetInfo != null && targetInfo.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) targetInfo.getItemMeta();
                if (meta != null && meta.hasOwner()) {
                    Player target = Bukkit.getPlayer(meta.getOwner());
                    if (target != null) {
                        // 从物品描述中提取等级信息
                        String lore = meta.getLore().toString();
                        int tier = extractTierFromLore(lore);
                        
                        if (tier != -1) {
                            boolean success = assassinManager.createContract(player, target, tier);
                            player.closeInventory();
                            
                            if (success) {
                                player.sendMessage("§a✅ 买凶合约已发布！");
                            } else {
                                player.sendMessage("§c❌ 发布合约失败");
                            }
                        } else {
                            player.sendMessage("§c❌ 无法确定杀手等级");
                        }
                    } else {
                        player.sendMessage("§c❌ 目标玩家已离线");
                    }
                }
            }
        } else if (clicked.getType() == Material.RED_WOOL && clicked.getItemMeta().getDisplayName().equals("§c❌ 取消")) {
            // 取消操作
            player.closeInventory();
            player.sendMessage("§7❌ 已取消买凶操作");
        }
    }
    
    private int extractTierFromLore(String lore) {
        if (lore.contains("普通杀手")) return 1;
        if (lore.contains("精英杀手")) return 2;
        if (lore.contains("骷髅狙击手")) return 3;
        return -1;
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 确保玩家库存正确更新
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Bukkit.getScheduler().runTaskLater(AssassinPlugin.getInstance(), () -> {
                player.updateInventory();
            }, 1L);
        }
    }
}
