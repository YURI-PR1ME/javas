package com.yourname.playerhireplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;

public class TrackingCompass {
    
    public static ItemStack createTrackingCompass(UUID contractId, UUID targetId) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        
        Player target = Bukkit.getPlayer(targetId);
        String targetName = target != null ? target.getName() : "未知目标";
        
        meta.setDisplayName("§6目标追踪指南针");
        meta.setLore(Arrays.asList(
            "§7合约: " + contractId.toString().substring(0, 8),
            "§7目标: " + targetName,
            "",
            "§e右键点击 §7- 显示目标信息",
            "§a指南针指针 §7- 始终指向目标位置"
        ));
        
        // 添加NBT标签
        NamespacedKey contractKey = new NamespacedKey(PlayerHirePlugin.getInstance(), "hire_contract");
        meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, contractId.toString());
        
        NamespacedKey targetKey = new NamespacedKey(PlayerHirePlugin.getInstance(), "tracking_target");
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, targetId.toString());
        
        compass.setItemMeta(meta);
        return compass;
    }
    
    public static boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(PlayerHirePlugin.getInstance(), "hire_contract");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }
    
    public static UUID getContractId(ItemStack compass) {
        if (!isTrackingCompass(compass)) return null;
        
        ItemMeta meta = compass.getItemMeta();
        NamespacedKey key = new NamespacedKey(PlayerHirePlugin.getInstance(), "hire_contract");
        
        try {
            return UUID.fromString(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        } catch (Exception e) {
            return null;
        }
    }
    
    public static UUID getTargetId(ItemStack compass) {
        if (!isTrackingCompass(compass)) return null;
        
        ItemMeta meta = compass.getItemMeta();
        NamespacedKey key = new NamespacedKey(PlayerHirePlugin.getInstance(), "tracking_target");
        
        try {
            return UUID.fromString(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        } catch (Exception e) {
            return null;
        }
    }
    
    // 更新指南针显示信息
    public static void updateCompassDisplay(ItemStack compass) {
        if (!isTrackingCompass(compass)) return;
        
        ItemMeta meta = compass.getItemMeta();
        UUID targetId = getTargetId(compass);
        UUID contractId = getContractId(compass);
        
        Player target = Bukkit.getPlayer(targetId);
        String targetName = target != null ? target.getName() : "未知目标";
        String targetStatus = target != null && target.isOnline() ? "§a在线" : "§c离线";
        
        meta.setDisplayName("§6目标追踪指南针");
        meta.setLore(Arrays.asList(
            "§7合约: " + contractId.toString().substring(0, 8),
            "§7目标: " + targetName,
            "§7状态: " + targetStatus,
            "",
            "§e右键点击 §7- 显示目标信息",
            "§a指南针指针 §7- 始终指向目标位置"
        ));
        
        compass.setItemMeta(meta);
    }
}
