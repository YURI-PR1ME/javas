package com.yourname.creditplugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.util.Arrays;

public class ReviveItem {
    
    public static ItemStack createReviveStation() {
        ItemStack item = new ItemStack(Material.RESPAWN_ANCHOR);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6复活选择台");
        meta.setLore(Arrays.asList(
            "§7右键使用",
            "§e选择要复活的玩家",
            "§c消耗6点信用点"
        ));
        
        // 添加NBT标签
        NamespacedKey key = new NamespacedKey(CreditPlugin.getInstance(), "revive_station");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        item.setItemMeta(meta);
        return item;
    }
    
    public static boolean isReviveStation(ItemStack item) {
        if (item == null || item.getType() != Material.RESPAWN_ANCHOR) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(CreditPlugin.getInstance(), "revive_station");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
