package com.example.godsexecutioner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {
    
    private final GodsExecutionerPlugin plugin;
    private final CraftingManager craftingManager;
    
    public CraftingListener(GodsExecutionerPlugin plugin, CraftingManager craftingManager) {
        this.plugin = plugin;
        this.craftingManager = craftingManager;
    }
    
    /**
     * 准备合成时检查材料
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        
        // 检查是否是神之执行者的合成配方
        if (matrix.length < 9) return;
        
        // 检查材料
        ItemStack tyrantPickaxe = null;
        ItemStack pacificWind = null;
        ItemStack dragonEgg = null;
        ItemStack netheriteHoe = null;
        
        // 根据我们的配方形状检查材料
        // 配方形状:
        // [T] [W] [D]
        // [ ] [N] [H]
        // [ ] [ ] [ ]
        
        // 位置0: T (暴君之镐)
        if (matrix[0] != null && matrix[0].getType() == Material.NETHERITE_PICKAXE) {
            tyrantPickaxe = matrix[0];
        }
        
        // 位置1: W (太平洋之风)
        if (matrix[1] != null && matrix[1].getType() == Material.TRIDENT) {
            pacificWind = matrix[1];
        }
        
        // 位置2: D (龙蛋)
        if (matrix[2] != null && matrix[2].getType() == Material.DRAGON_EGG) {
            dragonEgg = matrix[2];
        }
        
        // 位置4: N (下界合金锄)
        if (matrix[4] != null && matrix[4].getType() == Material.NETHERITE_HOE) {
            netheriteHoe = matrix[4];
        }
        
        // 位置5: H (海洋之心)
        // 这个是可选的，我们主要检查前四个
        
        // 检查是否满足合成条件
        boolean canCraft = (tyrantPickaxe != null && craftingManager.isTyrantPickaxe(tyrantPickaxe)) &&
                          (pacificWind != null && craftingManager.isPacificWind(pacificWind)) &&
                          (dragonEgg != null) &&
                          (netheriteHoe != null);
        
        if (!canCraft) {
            // 如果不是正确的材料，清除结果
            inventory.setResult(null);
        }
    }
    
    /**
     * 合成完成时给予物品
     */
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        
        // 检查是否是神之执行者
        if (result == null || result.getType() != Material.NETHERITE_HOE) {
            return;
        }
        
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        
        // 检查材料
        ItemStack tyrantPickaxe = matrix[0];
        ItemStack pacificWind = matrix[1];
        ItemStack dragonEgg = matrix[2];
        ItemStack netheriteHoe = matrix[4];
        
        // 验证材料
        boolean isValid = (tyrantPickaxe != null && craftingManager.isTyrantPickaxe(tyrantPickaxe)) &&
                         (pacificWind != null && craftingManager.isPacificWind(pacificWind)) &&
                         (dragonEgg != null && dragonEgg.getType() == Material.DRAGON_EGG) &&
                         (netheriteHoe != null && netheriteHoe.getType() == Material.NETHERITE_HOE);
        
        if (!isValid) {
            event.setCancelled(true);
            
            // 如果是玩家，发送消息
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                player.sendMessage(ChatColor.RED + "合成失败! 需要以下材料:");
                player.sendMessage(ChatColor.YELLOW + "• 暴君之镐");
                player.sendMessage(ChatColor.YELLOW + "• 太平洋之风");
                player.sendMessage(ChatColor.YELLOW + "• 龙蛋");
                player.sendMessage(ChatColor.YELLOW + "• 下界合金锄");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }
        
        // 成功合成，创建真正的神之执行者
        ItemStack godsExecutioner = ExecutionerManager.createGodsExecutioner(plugin);
        inventory.setResult(godsExecutioner);
        
        // 如果是玩家，发送成功消息
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            player.sendMessage(ChatColor.GOLD + "§l恭喜! 你合成了神之执行者!");
            player.sendMessage(ChatColor.YELLOW + "这把武器融合了暴君之镐的威能、");
            player.sendMessage(ChatColor.YELLOW + "太平洋之风的力量和龙蛋的神秘!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 0.8f);
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
            
            // 全服公告
            Bukkit.broadcastMessage(ChatColor.GOLD + "§l[神之执行者] " + 
                                   ChatColor.YELLOW + player.getName() + 
                                   ChatColor.GOLD + " 成功合成了传说中的武器!");
        }
    }
}
