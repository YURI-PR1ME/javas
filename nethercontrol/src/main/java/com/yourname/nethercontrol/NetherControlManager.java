// NetherControlManager.java
package com.yourname.nethercontrol;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.List;

public class NetherControlManager {
    
    private boolean unlocked;
    private NamespacedKey starKey;
    
    public NetherControlManager() {
        this.starKey = new NamespacedKey(NetherControlPlugin.getInstance(), "nether_star_item");
        loadConfig();
    }
    
    public void loadConfig() {
        FileConfiguration config = NetherControlPlugin.getInstance().getConfig();
        this.unlocked = config.getBoolean("unlocked", false);
    }
    
    public void saveConfig() {
        FileConfiguration config = NetherControlPlugin.getInstance().getConfig();
        config.set("unlocked", unlocked);
        NetherControlPlugin.getInstance().saveConfig();
    }
    
    public boolean isUnlocked() {
        return unlocked;
    }
    
    public void setUnlocked(boolean unlocked) {
        boolean previousState = this.unlocked;
        this.unlocked = unlocked;
        saveConfig();
        
        // 如果状态发生变化，立即执行一次信用点检查
        if (previousState != unlocked) {
            NetherControlPlugin.getInstance().getCreditIntegration().checkAllPlayers();
        }
        
        // 广播解锁消息
        if (unlocked) {
            String message = NetherControlPlugin.getInstance().getConfig().getString("messages.portal-unlocked", 
                "&a✨ 地狱门限制已解除！现在可以自由进出地狱。");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            String message = NetherControlPlugin.getInstance().getConfig().getString("messages.portal-locked", 
                "&c🔒 地狱门限制已封锁！将根据信用点自动传送玩家。");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
    
    // 创建沉星物品
    public ItemStack createStarItem() {
        ItemStack star = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = star.getItemMeta();
        
        FileConfiguration config = NetherControlPlugin.getInstance().getConfig();
        
        // 设置显示名称
        String displayName = config.getString("star-item.name", "&6沉星");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        
        // 设置描述
        List<String> lore = config.getStringList("star-item.lore");
        if (lore.isEmpty()) {
            lore = Arrays.asList(
                "&7右键使用解锁地狱门限制",
                "&e使用后所有玩家可以自由进出地狱",
                "&c一次性物品，使用后消失"
            );
        }
        
        // 翻译颜色代码
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lore);
        
        // 添加NBT标签标识
        meta.getPersistentDataContainer().set(starKey, PersistentDataType.BYTE, (byte) 1);
        
        star.setItemMeta(meta);
        return star;
    }
    
    // 检查是否是沉星物品
    public boolean isStarItem(ItemStack item) {
        if (item == null || item.getType() != Material.HEART_OF_THE_SEA) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        return meta.getPersistentDataContainer().has(starKey, PersistentDataType.BYTE);
    }
    
    // 处理沉星使用
    public boolean handleStarUse() {
        if (!unlocked) {
            setUnlocked(true);
            return true;
        }
        return false;
    }
}
