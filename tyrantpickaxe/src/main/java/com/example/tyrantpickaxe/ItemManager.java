package com.example.tyrantpickaxe;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class ItemManager {

    /**
     * 创建 暴君之镐
     * @param plugin 主插件实例 (用于获取 Key)
     * @return 暴君之镐 ItemStack
     */
    public static ItemStack createTyrantPickaxe(TyrantPickaxe plugin) {
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "暴君之镐");
            meta.setLore(Arrays.asList(
                    ChatColor.RED + "右键实体: 瞬移到背后 (冷却 3s)",
                    ChatColor.RED + "  (2秒内暴击重置冷却)",
                    ChatColor.RED + "下蹲+右键: 快速位移 (动态冷却)",
                    "",
                    ChatColor.DARK_GRAY + "“权力... 无限的权力!”"
            ));
            
            // 添加自定义数据标签，用于识别
            meta.getPersistentDataContainer().set(plugin.getItemKey(), PersistentDataType.BYTE, (byte) 1);
            
            // 隐藏附魔和属性
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.setUnbreakable(true);
            
            pickaxe.setItemMeta(meta);
        }

        return pickaxe;
    }

    /**
     * 检查一个 ItemStack 是否是 暴君之镐
     * @param item 要检查的物品
     * @param plugin 主插件实例 (用于获取 Key)
     * @return 如果是暴君之镐则返回 true
     */
    public static boolean isTyrantPickaxe(ItemStack item, TyrantPickaxe plugin) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(plugin.getItemKey(), PersistentDataType.BYTE);
    }
}
