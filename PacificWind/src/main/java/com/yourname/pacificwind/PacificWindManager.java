// [file name]: PacificWindManager.java
package com.yourname.pacificwind;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PacificWindManager {
    
    private final PacificWindPlugin plugin;
    private final NamespacedKey pacificWindKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey rainCooldownKey;
    
    // 冷却时间存储 (玩家UUID -> 冷却结束时间戳)
    private final Map<UUID, Long> rainCooldowns;
    
    // 蓄力时间存储 (玩家UUID -> 开始蓄力时间戳)
    private final Map<UUID, Long> chargingPlayers;
    
    // 击杀计数存储 (玩家UUID -> 击杀数量)
    private final Map<UUID, Integer> killCounts;
    
    // 重置冷却所需的击杀数量
    private static final int KILLS_TO_RESET_COOLDOWN = 20;
    
    public PacificWindManager(PacificWindPlugin plugin) {
        this.plugin = plugin;
        this.pacificWindKey = new NamespacedKey(plugin, "pacific_wind");
        this.modeKey = new NamespacedKey(plugin, "wind_mode"); // 0=引雷, 1=激流
        this.rainCooldownKey = new NamespacedKey(plugin, "rain_cd");
        this.rainCooldowns = new HashMap<>();
        this.chargingPlayers = new HashMap<>();
        this.killCounts = new HashMap<>();
    }
    
    public Map<UUID, Integer> getKillCounts() {
    return killCounts;
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
            "§7特殊能力:",
            "§7- 潜行+右键蓄力3秒: 召唤下雨(1分钟)",
            "§7- 潜行+左键: 切换引雷/激流模式",
            "§7- 下雨时投掷命中: 引雷+爆炸",
            "§7- 主手持有: 急迫X效果",
            "§7- 击杀20个实体: 重置下雨冷却",
            "",
            "§7当前模式: §a引雷模式 ⚡",
            "",
            "§7召唤条件:",
            "§7- 只能在地狱使用",
            "§7- 整个服务器只能召唤一次"
        ));
        
        // 添加附魔效果 - 初始为引雷模式
        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        meta.addEnchant(Enchantment.IMPALING, 12, true);
        meta.addEnchant(Enchantment.CHANNELING, 1, true);
        // 激流附魔初始不添加，模式切换时动态修改
        
        // 设置不可破坏
        meta.setUnbreakable(true);
        
        // 设置持久化数据，标记为太平洋之风
        meta.getPersistentDataContainer().set(pacificWindKey, PersistentDataType.BYTE, (byte) 1);
        // 设置初始模式为引雷(0)
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.INTEGER, 0);
        
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
     * 获取三叉戟当前模式
     * @return 0=引雷模式, 1=激流模式
     */
    public int getWindMode(ItemStack item) {
        if (!isPacificWind(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(modeKey, PersistentDataType.INTEGER, 0);
    }
    
    /**
     * 切换三叉戟模式
     */
    public void toggleWindMode(Player player, ItemStack item) {
        if (!isPacificWind(item)) return;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        int currentMode = container.getOrDefault(modeKey, PersistentDataType.INTEGER, 0);
        int newMode = (currentMode == 0) ? 1 : 0;
        
        // 更新模式数据
        container.set(modeKey, PersistentDataType.INTEGER, newMode);
        
        // 更新附魔
        if (newMode == 0) {
            // 引雷模式
            meta.addEnchant(Enchantment.CHANNELING, 1, true);
            meta.removeEnchant(Enchantment.RIPTIDE);
        } else {
            // 激流模式
            meta.addEnchant(Enchantment.RIPTIDE, 3, true);
            meta.removeEnchant(Enchantment.CHANNELING);
        }
        
        // 更新Lore
        updateLoreWithMode(meta, newMode);
        
        item.setItemMeta(meta);
        
        // 播放音效
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.5f);
        
        // 发送提示
        String modeName = (newMode == 0) ? "§a引雷模式 ⚡" : "§b激流模式 🌊";
        player.sendMessage("§9[太平洋之风] §7已切换到 " + modeName);
    }
    
    /**
     * 更新Lore显示当前模式
     */
    private void updateLoreWithMode(ItemMeta meta, int mode) {
        if (meta.getLore() == null) return;
        
        java.util.List<String> lore = meta.getLore();
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("当前模式:")) {
                String modeText = (mode == 0) ? "§7当前模式: §a引雷模式 ⚡" : "§7当前模式: §b激流模式 🌊";
                lore.set(i, modeText);
                break;
            }
        }
        meta.setLore(lore);
    }
    
    /**
     * 检查玩家是否在蓄力
     */
    public boolean isCharging(UUID playerId) {
        return chargingPlayers.containsKey(playerId);
    }
    
    /**
     * 开始蓄力
     */
    public void startCharging(UUID playerId) {
        chargingPlayers.put(playerId, System.currentTimeMillis());
    }
    
    /**
     * 结束蓄力
     */
    public void stopCharging(UUID playerId) {
        chargingPlayers.remove(playerId);
    }
    
    /**
     * 获取蓄力时间（毫秒）
     */
    public long getChargingTime(UUID playerId) {
        if (!chargingPlayers.containsKey(playerId)) return 0;
        return System.currentTimeMillis() - chargingPlayers.get(playerId);
    }
    
    /**
     * 检查下雨技能冷却
     */
    public boolean isRainOnCooldown(UUID playerId) {
        if (!rainCooldowns.containsKey(playerId)) return false;
        long cooldownEnd = rainCooldowns.get(playerId);
        return System.currentTimeMillis() < cooldownEnd;
    }
    
    /**
     * 获取剩余冷却时间（秒）
     */
    public long getRainCooldownRemaining(UUID playerId) {
        if (!rainCooldowns.containsKey(playerId)) return 0;
        long cooldownEnd = rainCooldowns.get(playerId);
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }
    
    /**
     * 开始下雨冷却（5分钟）
     */
    public void startRainCooldown(UUID playerId) {
        rainCooldowns.put(playerId, System.currentTimeMillis() + (5 * 60 * 1000)); // 5分钟
    }
    
    /**
     * 清除下雨冷却（管理员命令用）
     */
    public void clearRainCooldown(UUID playerId) {
        rainCooldowns.remove(playerId);
    }
    
    /**
     * 获取玩家击杀数量
     */
    public int getKillCount(UUID playerId) {
        return killCounts.getOrDefault(playerId, 0);
    }
    
    /**
     * 增加玩家击杀数量
     */
    public void addKill(UUID playerId) {
        int currentKills = getKillCount(playerId);
        int newKills = currentKills + 1;
        killCounts.put(playerId, newKills);
        
        // 检查是否达到重置冷却的击杀数
        if (newKills >= KILLS_TO_RESET_COOLDOWN) {
            // 重置击杀计数
            killCounts.put(playerId, 0);
            
            // 清除下雨冷却
            clearRainCooldown(playerId);
            
            // 通知玩家
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§9[太平洋之风] §a✅ 已击杀20个实体，下雨冷却已重置!");
                player.sendMessage("§7现在可以再次召唤降雨了!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                player.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation(), 30, 0.5, 1, 0.5, 0.5);
            }
        }
    }
    
    /**
     * 重置玩家击杀计数
     */
    public void resetKillCount(UUID playerId) {
        killCounts.put(playerId, 0);
    }
    
    /**
     * 给玩家太平洋之风三叉戟
     */
    public void givePacificWindToPlayer(Player player) {
        ItemStack pacificWind = createPacificWind();
        
        if (player.getInventory().addItem(pacificWind).isEmpty()) {
            player.sendMessage("§9🌊 你获得了 §9太平洋之风 ");
            player.sendMessage("§7特殊能力:");
            player.sendMessage("§7- 潜行+右键蓄力3秒: 召唤下雨(1分钟)");
            player.sendMessage("§7- 潜行+左键: 切换引雷/激流模式");
            player.sendMessage("§7- 下雨时投掷命中: 引雷+爆炸");
            player.sendMessage("§7- 主手持有: 急迫X效果");
            player.sendMessage("§7- 击杀20个实体: 重置下雨冷却");
            
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
