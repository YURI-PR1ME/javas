package com.yourname.creditsurveillance;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreditSurveillance extends JavaPlugin {
    
    private static CreditSurveillance instance;
    private SurveillanceManager surveillanceManager;
    private Map<UUID, PlayerSurveillanceData> playerData;
    
    // 主插件引用
    private JavaPlugin creditPlugin;
    
    @Override
    public void onEnable() {
        instance = this;
        this.playerData = new HashMap<>();
        
        // 尝试获取主插件
        creditPlugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("CreditPlugin");
        if (creditPlugin == null) {
            getLogger().severe("主插件 CreditPlugin 未找到！监管系统将无法工作！");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("公民信用监管系统已启用！");
        
        // 初始化管理器
        this.surveillanceManager = new SurveillanceManager(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new SurveillanceListener(this), this);
        
        // 启动监管检查任务
        startSurveillanceTask();
        
        // 启动AI更新任务
        startAITask();
    }
    
    @Override
    public void onDisable() {
        // 清理所有监管者
        for (PlayerSurveillanceData data : playerData.values()) {
            data.removeAllGuardians();
        }
        playerData.clear();
        
        getLogger().info("公民信用监管系统已禁用！");
    }
    
    public static CreditSurveillance getInstance() {
        return instance;
    }
    
    public SurveillanceManager getSurveillanceManager() {
        return surveillanceManager;
    }
    
    public JavaPlugin getCreditPlugin() {
        return creditPlugin;
    }
    
    public PlayerSurveillanceData getPlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, k -> new PlayerSurveillanceData(playerId));
    }
    
    public PlayerSurveillanceData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }
    
    private void startSurveillanceTask() {
        // 每2秒检查一次玩家信用点状态
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    surveillanceManager.updatePlayerSurveillance(player);
                }
            }
        }.runTaskTimer(this, 0L, 40L); // 2秒 = 40 tick
    }
    
    private void startAITask() {
        // 每1秒更新一次监管者AI
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerSurveillanceData data : playerData.values()) {
                    data.updateGuardianAI();
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 1秒 = 20 tick
    }
    
    // 清理离线玩家数据
    public void cleanupOfflinePlayers() {
        playerData.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                entry.getValue().removeAllGuardians();
                return true;
            }
            return false;
        });
    }
}
