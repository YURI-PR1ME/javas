package com.yourname.hiddenscore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ScoreManager {
    
    private final Map<UUID, Integer> playerScores = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hasNetheriteIngot = new ConcurrentHashMap<>();
    private final Set<UUID> hasWarpSpeedAchievement = ConcurrentHashMap.newKeySet();
    
    // 新增：玩家死亡次数记录
    private final Map<UUID, Integer> playerDeathCount = new ConcurrentHashMap<>();
    
    // 新增：全服首次记录
    private boolean hasShieldFirstClaimed = false;
    private boolean hasEnderPearlFirstUsed = false;
    private boolean hasHoeFirstUsed = false;
    private boolean hasHeavyHammerFirstClaimed = false;
    private boolean hasDiamondArmorFirstClaimed = false;
    
    // 新增：玩家个人首次记录
    private final Set<UUID> hasTotemActivated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hasCreditGT100Rewarded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hasCreditGT50Rewarded = ConcurrentHashMap.newKeySet();
    
    private long lastExpStatDay = -1;
    private long lastCreditStatDay = -1;
    private boolean hasNetheriteFirstClaimed = false;
    
    public ScoreManager() {
        loadAllData();
    }
    
    // 初始化玩家分数
    public void initializePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerScores.containsKey(uuid)) {
            playerScores.put(uuid, 0);
            savePlayerData(player);
        }
        
        // 检查信用点条件
        checkCreditRewards(player);
    }
    
    // 处理Boss击杀
    public void handleBossKill(Player player, String bossType) {
        if (bossType.equals("drowned_king") || bossType.equals("tyrant_boss")) {
            addScore(player, 100);
            getPluginLogger().info("玩家 " + player.getName() + " 击杀" + bossType + "，隐藏分+100");
        }
    }
    
    // 处理玩家击杀
    public void handlePlayerKill(Player killer, Player victim) {
        addScore(killer, 10);
        getPluginLogger().info("玩家 " + killer.getName() + " 击杀玩家 " + victim.getName() + "，隐藏分+10");
    }
    
    // 处理怪物击杀
    public void handleMonsterKill(Player player) {
        addScore(player, 5);
        // 注意：这里不在控制台输出，避免刷屏
    }
    
    // 处理下界合金锭获取
    public void handleNetheriteIngot(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!hasNetheriteIngot.containsKey(uuid) || !hasNetheriteIngot.get(uuid)) {
            // 首次获得
            hasNetheriteIngot.put(uuid, true);
            
            if (!hasNetheriteFirstClaimed) {
                // 全服首次获得
                addScore(player, 2);
                hasNetheriteFirstClaimed = true;
                getPluginLogger().info("玩家 " + player.getName() + " 首次获得下界合金锭，隐藏分+2");
            }
        }
    }
    
    // 处理曲速泡成就
    public void handleWarpSpeedAchievement(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!hasWarpSpeedAchievement.contains(uuid)) {
            hasWarpSpeedAchievement.add(uuid);
            addScore(player, 10);
            getPluginLogger().info("玩家 " + player.getName() + " 获得曲速泡成就，隐藏分+10");
        }
    }
    
    // ========== 新增方法 ==========
    
    // 处理玩家死亡
    public void handlePlayerDeath(Player player) {
        UUID uuid = player.getUniqueId();
        int deathCount = playerDeathCount.getOrDefault(uuid, 0) + 1;
        playerDeathCount.put(uuid, deathCount);
        
        // 检查是否达到15次死亡
        if (deathCount == 15) {
            addScore(player, 8);
            getPluginLogger().info("玩家 " + player.getName() + " 死亡15次，隐藏分+8");
        }
        
        savePlayerData(player);
    }
    
    // 处理不死图腾激活
    public void handleTotemActivation(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!hasTotemActivated.contains(uuid)) {
            hasTotemActivated.add(uuid);
            addScore(player, 10);
            getPluginLogger().info("玩家 " + player.getName() + " 首次激活不死图腾，隐藏分+10");
        }
    }
    
    // 处理首次获得盾牌
    public void handleFirstShield(Player player) {
        if (!hasShieldFirstClaimed) {
            hasShieldFirstClaimed = true;
            addScore(player, 10);
            getPluginLogger().info("玩家 " + player.getName() + " 全服首次获得盾牌，隐藏分+10");
        }
    }
    
    // 处理首次使用末影珍珠
    public void handleFirstEnderPearl(Player player) {
        if (!hasEnderPearlFirstUsed) {
            hasEnderPearlFirstUsed = true;
            addScore(player, 8);
            getPluginLogger().info("玩家 " + player.getName() + " 全服首次使用末影珍珠，隐藏分+8");
        }
    }
    
    // 处理首次使用锄头
    public void handleFirstHoeUse(Player player) {
        if (!hasHoeFirstUsed) {
            hasHoeFirstUsed = true;
            addScore(player, 17);
            getPluginLogger().info("玩家 " + player.getName() + " 全服首次使用锄头，隐藏分+17");
        }
    }
    
    // 处理首次获得重锤
    public void handleFirstHeavyHammer(Player player) {
        if (!hasHeavyHammerFirstClaimed) {
            hasHeavyHammerFirstClaimed = true;
            addScore(player, 9);
            getPluginLogger().info("玩家 " + player.getName() + " 全服首次获得重锤，隐藏分+9");
        }
    }
    
    // 处理首次获得钻石甲
    public void handleFirstDiamondArmor(Player player) {
        if (!hasDiamondArmorFirstClaimed) {
            hasDiamondArmorFirstClaimed = true;
            addScore(player, 10);
            getPluginLogger().info("玩家 " + player.getName() + " 全服首次获得钻石甲，隐藏分+10");
        }
    }
    
    // 检查信用点奖励
    private void checkCreditRewards(Player player) {
        UUID uuid = player.getUniqueId();
        int credits = getPlayerCredits(player);
        
        // 检查信用点大于100
        if (credits > 100 && !hasCreditGT100Rewarded.contains(uuid)) {
            hasCreditGT100Rewarded.add(uuid);
            addScore(player, 100);
            getPluginLogger().info("玩家 " + player.getName() + " 信用点大于100，隐藏分+100");
        }
        
        // 检查信用点大于50
        if (credits > 50 && !hasCreditGT50Rewarded.contains(uuid)) {
            hasCreditGT50Rewarded.add(uuid);
            addScore(player, 30);
            getPluginLogger().info("玩家 " + player.getName() + " 信用点大于50，隐藏分+30");
        }
    }
    
    // 检查物品是否为重锤（自定义物品）
    public boolean isHeavyHammer(ItemStack item) {
        if (item == null) return false;
        
        // 假设重锤是钻石斧并具有特定的显示名称
        return item.getType() == Material.DIAMOND_AXE && 
               item.hasItemMeta() && 
               item.getItemMeta().getDisplayName() != null &&
               item.getItemMeta().getDisplayName().contains("重锤");
    }
    
    // 检查玩家是否穿着钻石甲
    public boolean hasDiamondArmor(Player player) {
        PlayerInventory inventory = player.getInventory();
        return inventory.getBoots() != null && inventory.getBoots().getType() == Material.DIAMOND_BOOTS &&
               inventory.getLeggings() != null && inventory.getLeggings().getType() == Material.DIAMOND_LEGGINGS &&
               inventory.getChestplate() != null && inventory.getChestplate().getType() == Material.DIAMOND_CHESTPLATE &&
               inventory.getHelmet() != null && inventory.getHelmet().getType() == Material.DIAMOND_HELMET;
    }
    
    // ========== 原有方法 ==========
    
    // 检查每日统计
    public void checkDailyStats() {
        // 获取主世界时间
        long currentDay = Bukkit.getWorlds().get(0).getFullTime() / 24000;
        
        // 经验排行榜统计（每游戏日）
        if (currentDay != lastExpStatDay) {
            lastExpStatDay = currentDay;
            processExpLeaderboard();
        }
        
        // 信用点排行榜统计（每2游戏日）
        if (currentDay % 2 == 0 && currentDay != lastCreditStatDay) {
            lastCreditStatDay = currentDay;
            processCreditLeaderboard();
        }
    }
    
    // 处理经验排行榜
    private void processExpLeaderboard() {
        Player topPlayer = null;
        int highestExp = -1;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            int exp = getTotalExperience(player);
            if (exp > highestExp) {
                highestExp = exp;
                topPlayer = player;
            }
        }
        
        if (topPlayer != null) {
            addScore(topPlayer, 3);
            getPluginLogger().info("玩家 " + topPlayer.getName() + " 成为经验排行榜首，隐藏分+3");
        }
    }
    
    // 处理信用点排行榜
    private void processCreditLeaderboard() {
        Player topPlayer = null;
        int highestCredits = -1;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            int credits = getPlayerCredits(player);
            
            if (credits > highestCredits) {
                highestCredits = credits;
                topPlayer = player;
            }
        }
        
        if (topPlayer != null) {
            addScore(topPlayer, 2);
            getPluginLogger().info("玩家 " + topPlayer.getName() + " 成为信用点排行榜首，隐藏分+2");
        }
    }
    
    // 获取玩家信用点（需要您根据实际插件调整）
    private int getPlayerCredits(Player player) {
        try {
            // 尝试通过反射或其他方式获取信用点
            // 这里是一个示例，您需要根据实际插件调整
            Class<?> creditPluginClass = Class.forName("com.yourname.creditplugin.CreditPlugin");
            Object creditPluginInstance = creditPluginClass.getMethod("getInstance").invoke(null);
            Object creditManager = creditPluginClass.getMethod("getCreditManager").invoke(creditPluginInstance);
            return (int) creditManager.getClass().getMethod("getCredits", Player.class).invoke(creditManager, player);
        } catch (Exception e) {
            // 如果无法获取信用点，返回0
            getPluginLogger().warning("无法获取玩家 " + player.getName() + " 的信用点: " + e.getMessage());
            return 0;
        }
    }
    
    // 处理信用点最低统计（管理员触发）
    public void processLowestCreditPlayer() {
        Player lowestPlayer = null;
        int lowestCredits = Integer.MAX_VALUE;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            int credits = getPlayerCredits(player);
            
            if (credits < lowestCredits) {
                lowestCredits = credits;
                lowestPlayer = player;
            }
        }
        
        if (lowestPlayer != null) {
            addScore(lowestPlayer, 9);
            getPluginLogger().info("玩家 " + lowestPlayer.getName() + " 信用点最低，隐藏分+9");
        }
    }
    
    // 计算玩家总经验（包括等级和经验条）
    private int getTotalExperience(Player player) {
        int level = player.getLevel();
        float exp = player.getExp();
        
        // Minecraft经验计算公式
        int totalExp = 0;
        
        if (level <= 15) {
            totalExp = (int) (Math.pow(level, 2) + 6 * level);
        } else if (level <= 30) {
            totalExp = (int) (2.5 * Math.pow(level, 2) - 40.5 * level + 360);
        } else {
            totalExp = (int) (4.5 * Math.pow(level, 2) - 162.5 * level + 2220);
        }
        
        totalExp += (int) (exp * getExpToNextLevel(level));
        return totalExp;
    }
    
    // 获取升级所需经验
    private int getExpToNextLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }
    
    // 添加分数
    public void addScore(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int currentScore = playerScores.getOrDefault(uuid, 0);
        playerScores.put(uuid, currentScore + amount);
        savePlayerData(player);
    }
    
    // 获取分数
    public int getScore(Player player) {
        return playerScores.getOrDefault(player.getUniqueId(), 0);
    }
    
    // 获取所有玩家分数（用于管理员查看）
    public Map<UUID, Integer> getAllScores() {
        return new HashMap<>(playerScores);
    }
    
    // 数据保存和加载
    public void saveAllData() {
        for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
            HiddenScorePlugin.getInstance().getDataConfig().set("scores." + entry.getKey().toString(), entry.getValue());
        }
        
        // 保存其他数据
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.last_exp_stat_day", lastExpStatDay);
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.last_credit_stat_day", lastCreditStatDay);
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.netherite_first_claimed", hasNetheriteFirstClaimed);
        
        // 保存新增的全服首次数据
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.shield_first_claimed", hasShieldFirstClaimed);
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.ender_pearl_first_used", hasEnderPearlFirstUsed);
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.hoe_first_used", hasHoeFirstUsed);
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.heavy_hammer_first_claimed", hasHeavyHammerFirstClaimed);
        HiddenScorePlugin.getInstance().getDataConfig().set("stats.diamond_armor_first_claimed", hasDiamondArmorFirstClaimed);
        
        // 保存玩家特定数据
        for (Map.Entry<UUID, Boolean> entry : hasNetheriteIngot.entrySet()) {
            HiddenScorePlugin.getInstance().getDataConfig().set("netherite." + entry.getKey().toString(), entry.getValue());
        }
        
        for (UUID uuid : hasWarpSpeedAchievement) {
            HiddenScorePlugin.getInstance().getDataConfig().set("warpspeed." + uuid.toString(), true);
        }
        
        // 保存新增的玩家数据
        for (Map.Entry<UUID, Integer> entry : playerDeathCount.entrySet()) {
            HiddenScorePlugin.getInstance().getDataConfig().set("deaths." + entry.getKey().toString(), entry.getValue());
        }
        
        for (UUID uuid : hasTotemActivated) {
            HiddenScorePlugin.getInstance().getDataConfig().set("totem." + uuid.toString(), true);
        }
        
        for (UUID uuid : hasCreditGT100Rewarded) {
            HiddenScorePlugin.getInstance().getDataConfig().set("credit_100." + uuid.toString(), true);
        }
        
        for (UUID uuid : hasCreditGT50Rewarded) {
            HiddenScorePlugin.getInstance().getDataConfig().set("credit_50." + uuid.toString(), true);
        }
        
        HiddenScorePlugin.getInstance().saveData();
    }
    
    private void loadAllData() {
        YamlConfiguration config = HiddenScorePlugin.getInstance().getDataConfig();
        
        // 加载分数数据
        if (config.contains("scores")) {
            for (String key : config.getConfigurationSection("scores").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                int score = config.getInt("scores." + key);
                playerScores.put(uuid, score);
            }
        }
        
        // 加载统计数据
        lastExpStatDay = config.getLong("stats.last_exp_stat_day", -1);
        lastCreditStatDay = config.getLong("stats.last_credit_stat_day", -1);
        hasNetheriteFirstClaimed = config.getBoolean("stats.netherite_first_claimed", false);
        
        // 加载新增的全服首次数据
        hasShieldFirstClaimed = config.getBoolean("stats.shield_first_claimed", false);
        hasEnderPearlFirstUsed = config.getBoolean("stats.ender_pearl_first_used", false);
        hasHoeFirstUsed = config.getBoolean("stats.hoe_first_used", false);
        hasHeavyHammerFirstClaimed = config.getBoolean("stats.heavy_hammer_first_claimed", false);
        hasDiamondArmorFirstClaimed = config.getBoolean("stats.diamond_armor_first_claimed", false);
        
        // 加载玩家特定数据
        if (config.contains("netherite")) {
            for (String key : config.getConfigurationSection("netherite").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                boolean hasIngot = config.getBoolean("netherite." + key);
                hasNetheriteIngot.put(uuid, hasIngot);
            }
        }
        
        if (config.contains("warpspeed")) {
            for (String key : config.getConfigurationSection("warpspeed").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                hasWarpSpeedAchievement.add(uuid);
            }
        }
        
        // 加载新增的玩家数据
        if (config.contains("deaths")) {
            for (String key : config.getConfigurationSection("deaths").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                int deathCount = config.getInt("deaths." + key);
                playerDeathCount.put(uuid, deathCount);
            }
        }
        
        if (config.contains("totem")) {
            for (String key : config.getConfigurationSection("totem").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                hasTotemActivated.add(uuid);
            }
        }
        
        if (config.contains("credit_100")) {
            for (String key : config.getConfigurationSection("credit_100").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                hasCreditGT100Rewarded.add(uuid);
            }
        }
        
        if (config.contains("credit_50")) {
            for (String key : config.getConfigurationSection("credit_50").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                hasCreditGT50Rewarded.add(uuid);
            }
        }
    }
    
    public void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        HiddenScorePlugin.getInstance().getDataConfig().set("scores." + uuid.toString(), getScore(player));
        HiddenScorePlugin.getInstance().getDataConfig().set("deaths." + uuid.toString(), playerDeathCount.getOrDefault(uuid, 0));
        HiddenScorePlugin.getInstance().saveData();
    }
    
    // 修复：正确获取Logger实例
    private Logger getPluginLogger() {
        return HiddenScorePlugin.getInstance().getLogger();
    }
}
