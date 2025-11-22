package com.yourname.drownedking;

import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import java.util.*;

public class DrownedKingBoss {
    private final UUID bossId;
    private final UUID entityId;
    private final Location spawnLocation;
    private final long spawnTime;
    private final String spawnedBy;
    private final BossBar bossBar;
    
    private boolean active;
    private boolean completed;
    private boolean success;
    private long completionTime;
    private int playersKilled;
    private int surgeAttackCount; // 电涌攻击计数
    private boolean inTridentFrenzy; // 是否处于三叉戟狂欢节状态
    private long frenzyStartTime; // 狂欢节开始时间
    
    // 新增：玩家死亡计数器
    private final Map<UUID, Integer> playerDeathCounts;
    
    public DrownedKingBoss(UUID bossId, UUID entityId, Location spawnLocation, String spawnedBy) {
        this.bossId = bossId;
        this.entityId = entityId;
        this.spawnLocation = spawnLocation;
        this.spawnedBy = spawnedBy;
        this.spawnTime = System.currentTimeMillis();
        this.active = true;
        this.completed = false;
        this.success = false;
        this.playersKilled = 0;
        this.surgeAttackCount = 0;
        this.inTridentFrenzy = false;
        this.frenzyStartTime = 0;
        this.playerDeathCounts = new HashMap<>();
        
        // 创建Boss血条
        this.bossBar = org.bukkit.Bukkit.createBossBar(
            "§4溺尸王 §c(深渊主宰)", 
            BarColor.PURPLE, 
            BarStyle.SEGMENTED_10
        );
        this.bossBar.setVisible(true);
    }
    
    // Getters and Setters
    public UUID getBossId() { return bossId; }
    public UUID getEntityId() { return entityId; }
    public Location getSpawnLocation() { return spawnLocation; }
    public String getSpawnedBy() { return spawnedBy; }
    public long getSpawnTime() { return spawnTime; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public long getCompletionTime() { return completionTime; }
    public void setCompletionTime(long completionTime) { this.completionTime = completionTime; }
    public int getPlayersKilled() { return playersKilled; }
    public void incrementPlayersKilled() { this.playersKilled++; }
    public BossBar getBossBar() { return bossBar; }
    public int getSurgeAttackCount() { return surgeAttackCount; }
    public void incrementSurgeAttackCount() { this.surgeAttackCount++; }
    public void resetSurgeAttackCount() { this.surgeAttackCount = 0; }
    public boolean isInTridentFrenzy() { return inTridentFrenzy; }
    public void setInTridentFrenzy(boolean inTridentFrenzy) { 
        this.inTridentFrenzy = inTridentFrenzy;
        if (inTridentFrenzy) {
            this.frenzyStartTime = System.currentTimeMillis();
        }
    }
    public long getFrenzyStartTime() { return frenzyStartTime; }
    
    // 新增：玩家死亡计数方法
    public int getPlayerDeathCount(UUID playerId) {
        return playerDeathCounts.getOrDefault(playerId, 0);
    }
    
    public void incrementPlayerDeathCount(UUID playerId) {
        int currentCount = getPlayerDeathCount(playerId);
        playerDeathCounts.put(playerId, currentCount + 1);
    }
    
    public boolean shouldRetreatAfterPlayerDeath(UUID playerId) {
        return getPlayerDeathCount(playerId) >= 2; // 同一玩家死亡2次后退场
    }
    
    public Map<UUID, Integer> getPlayerDeathCounts() {
        return new HashMap<>(playerDeathCounts);
    }
    
    public void complete(boolean success) {
        this.completed = true;
        this.success = success;
        this.completionTime = System.currentTimeMillis();
        this.active = false;
        this.bossBar.setVisible(false);
        this.bossBar.removeAll();
    }
    
    public void updateBossBar(double progress) {
        this.bossBar.setProgress(Math.max(0, Math.min(1, progress)));
    }
    
    public void addPlayerToBossBar(Player player) {
        this.bossBar.addPlayer(player);
    }
    
    public void removePlayerFromBossBar(Player player) {
        this.bossBar.removePlayer(player);
    }
}
