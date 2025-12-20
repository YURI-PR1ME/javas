package com.yourname.shopplugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.util.Arrays;

public class ShopTerminal {
    
    public static ItemStack createShopTerminal() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6商店终端");
        meta.setLore(Arrays.asList(
            "§7右键打开商店",
            "§e使用信用点购买物品",
            "§c合成获得"
        ));
        
        // 添加NBT标签标识这是商店终端
        NamespacedKey key = new NamespacedKey(ShopPlugin.getInstance(), "shop_terminal");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        item.setItemMeta(meta);
        return item;
    }
    
    public static boolean isShopTerminal(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_CHEST) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(ShopPlugin.getInstance(), "shop_terminal");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
