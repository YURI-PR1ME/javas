package com.example.godsexecutioner;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class CraftingManager {
    
    private final GodsExecutionerPlugin plugin;
    private NamespacedKey recipeKey;
    
    public CraftingManager(GodsExecutionerPlugin plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "gods_executioner_recipe");
    }
    
    /**
     * 注册合成配方
     */
    public void registerRecipe() {
        // 创建神明处决者
        ItemStack result = ExecutionerManager.createGodsExecutioner(plugin);
        
        // 创建合成配方
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        
        // 设置合成形状
        recipe.shape("TWD", " NH", "   ");
        
        // 设置合成材料
        recipe.setIngredient('T', Material.NETHERITE_PICKAXE); // 暴君之镐
        recipe.setIngredient('W', Material.TRIDENT);           // 太平洋之风
        recipe.setIngredient('D', Material.DRAGON_EGG);        // 龙蛋
        recipe.setIngredient('N', Material.NETHERITE_HOE);     // 下界合金锄
        recipe.setIngredient('H', Material.HEART_OF_THE_SEA);  // 海洋之心（可选，如果没有可以去掉）
        
        // 注册配方
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("§6神明处决者合成配方已注册!");
    }
    
    /**
     * 检查物品是否是暴君之镐
     */
    public boolean isTyrantPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // 尝试通过NBT检查
        // 暴君之镐的NBT键通常是 "tyrant_pickaxe"
        try {
            // 检查是否包含暴君之镐的标签
            NamespacedKey tyrantKey = new NamespacedKey("tyrantpickaxe", "tyrant_pickaxe");
            return meta.getPersistentDataContainer().has(tyrantKey, PersistentDataType.BYTE);
        } catch (Exception e) {
            // 如果无法创建NamespacedKey，尝试通过其他方式检查
            if (meta.hasDisplayName() && meta.getDisplayName().contains("暴君")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查物品是否是太平洋之风
     */
    public boolean isPacificWind(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // 尝试通过NBT检查
        // 太平洋之风的NBT键通常是 "pacific_wind"
        try {
            NamespacedKey pacificKey = new NamespacedKey("pacificwind", "pacific_wind");
            return meta.getPersistentDataContainer().has(pacificKey, PersistentDataType.BYTE);
        } catch (Exception e) {
            // 如果无法创建NamespacedKey，尝试通过其他方式检查
            if (meta.hasDisplayName() && meta.getDisplayName().contains("太平洋")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否可以合成神明处决者
     */
    public boolean canCraftGodsExecutioner(ItemStack tyrantPickaxe, ItemStack pacificWind, ItemStack dragonEgg) {
        return isTyrantPickaxe(tyrantPickaxe) && 
               isPacificWind(pacificWind) && 
               dragonEgg != null && 
               dragonEgg.getType() == Material.DRAGON_EGG;
    }
    
    /**
     * 取消注册合成配方
     */
    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }
}
