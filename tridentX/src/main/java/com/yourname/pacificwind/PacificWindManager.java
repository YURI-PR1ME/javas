// [file name]: PacificWindManager.java
package com.yourname.pacificwind;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class PacificWindManager {
    
    private final PacificWindPlugin plugin;
    private final NamespacedKey pacificWindKey;
    
    public PacificWindManager(PacificWindPlugin plugin) {
        this.plugin = plugin;
        this.pacificWindKey = new NamespacedKey(plugin, "pacific_wind");
    }
    
    /**
     * 创建太平洋之风三叉戟
     */
    public ItemStack createPacificWind() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        
        // 设置名称和Lore
        meta.setDisplayName("§9太平洋之风 §bPacific Wind");
        meta.setLore(Arrays.asList(
            "§8« §7SUN的呼唤 §8»",
            "",
            "§c曾几何时，本不需要战争....§7",
            "",
            "§6直到LUNAR嫉妒SUN的力量，那份，不属于他的../",
            "§6SUN本可杀死LUNAR,成为双界之王..",
            "",
            "§8传说: 这把三叉戟曾属于",
            "§8一位统治主世界的神明...",
            "",
            "§7召唤条件:",
            "§7- 只能在地狱使用",
            "§7- 整个服务器只能召唤一次"
        ));
        
        // 添加附魔效果
        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        meta.addEnchant(Enchantment.IMPALING, 12, true);
        meta.addEnchant(Enchantment.CHANNELING, 1, true);
        //meta.addEnchant(Enchantment.RIPTIDE, 1, true);
        
        // 设置不可破坏
        meta.setUnbreakable(true);
        
        // 设置持久化数据，标记为太平洋之风
        meta.getPersistentDataContainer().set(pacificWindKey, PersistentDataType.BYTE, (byte) 1);
        
        trident.setItemMeta(meta);
        return trident;
    }
    
    /**
     * 检查物品是否是太平洋之风三叉戟
     */
    public boolean isPacificWind(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(pacificWindKey, PersistentDataType.BYTE);
    }
    
    /**
     * 给玩家太平洋之风三叉戟
     */
    public void givePacificWindToPlayer(Player player) {
        ItemStack pacificWind = createPacificWind();
        
        if (player.getInventory().addItem(pacificWind).isEmpty()) {
            player.sendMessage("§9🌊 你获得了 §9太平洋之风 ");
            player.sendMessage("§7右键§5地狱§7的末地传送门框架召唤§4暴君§7");
            player.sendMessage("§6注意: 整个服务器只能召唤一次暴君!");
            
            // 播放获得音效
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 1.0f, 1.2f);
            
            // 粒子效果
            player.spawnParticle(org.bukkit.Particle.NAUTILUS, player.getLocation(), 20, 1, 1, 1);
        } else {
            // 背包已满，掉落物品
            player.getWorld().dropItemNaturally(player.getLocation(), pacificWind);
            player.sendMessage("§6💡 背包已满，太平洋之风三叉戟已掉落在地面上");
        }
    }
}
