package com.example.godsexecutioner;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class ExecutionerManager {

    /**
     * 创建神之执行者
     */
    public static ItemStack createGodsExecutioner(GodsExecutionerPlugin plugin) {
        ItemStack executioner = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = executioner.getItemMeta();

        if (meta != null) {
            // 设置物品名称和Lore
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "神之执行者" + 
                               ChatColor.DARK_GRAY + " [Gods' Executioner]");
            
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "传说中由众神铸造的武器，",
                ChatColor.GRAY + "融合了暴君之镐的威能、",
                ChatColor.GRAY + "太平洋之风的力量与龙蛋的神秘。",
                "",
                ChatColor.YELLOW + "特殊能力：",
                ChatColor.GOLD + "• 右键：向准星方向冲锋",
                ChatColor.GOLD + "  (路径上的敌人都将受到爆炸伤害)",
                ChatColor.GOLD + "• 潜行+左键：发射凋零骷髅头",
                ChatColor.GOLD + "• 潜行+右键：引力场生成",
                ChatColor.GOLD + "  (拉取敌人，3秒后恢复生命)",
                "",
                ChatColor.DARK_RED + "对亡灵生物伤害 +40",
                ChatColor.DARK_RED + "死亡印记：减少敌人最大生命",
                "",
                ChatColor.DARK_GRAY + "材料：暴君之镐 + 太平洋之风 + 龙蛋"
            ));
            
            // 添加亡灵杀手附魔（显示为20级，实际效果我们会在事件中处理）
            meta.addEnchant(Enchantment.SMITE, 20, true);
            
            // 添加其他视觉效果
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 5, true);
            
            // 隐藏附魔和属性
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, 
                             ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DESTROYS,
                             ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            
            // 设置不可破坏
            meta.setUnbreakable(true);
            
            // 添加自定义数据标签，用于识别
            meta.getPersistentDataContainer().set(plugin.getItemKey(), 
                PersistentDataType.BYTE, (byte) 1);
            
            executioner.setItemMeta(meta);
        }

        return executioner;
    }

    /**
     * 检查物品是否是神之执行者
     */
    public static boolean isGodsExecutioner(ItemStack item, GodsExecutionerPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_HOE || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(plugin.getItemKey(), 
                PersistentDataType.BYTE);
    }
    
    /**
     * 获取物品的冷却标签Key
     */
    public static NamespacedKey getCooldownKey(GodsExecutionerPlugin plugin) {
        return new NamespacedKey(plugin, "executioner_cooldown");
    }
    
    /**
     * 获取引力场标签Key
     */
    public static NamespacedKey getGravityFieldKey(GodsExecutionerPlugin plugin) {
        return new NamespacedKey(plugin, "gravity_field");
    }
}
