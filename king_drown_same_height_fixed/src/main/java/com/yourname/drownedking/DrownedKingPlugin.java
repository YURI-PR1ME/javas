package com.yourname.drownedking;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class DrownedKingPlugin extends JavaPlugin {
    
    private static DrownedKingPlugin instance;
    private DrownedKingManager drownedKingManager;
    private File dataFile;
    private YamlConfiguration dataConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置并添加新选项
        saveDefaultConfig();
        setupDataFile();
        
        // 确保新配置项存在
        FileConfiguration config = getConfig();
        if (!config.contains("trident_frenzy_block_damage")) {
            config.set("trident_frenzy_block_damage", true); // 默认开启方块破坏
            saveConfig();
        }
        
        // 确保消息配置存在
        if (!config.contains("messages.player_killed_by_minion")) {
            config.set("messages.player_killed_by_minion", "§7溺尸守卫 §c在溺尸王的命令下击杀了 {player}!");
        }
        if (!config.contains("messages.player_killed_by_large_trident")) {
            config.set("messages.player_killed_by_large_trident", "§4溺尸王 §c用深渊三叉戟击杀了 {player}!");
        }
        if (!config.contains("messages.player_killed_by_small_trident")) {
            config.set("messages.player_killed_by_small_trident", "§4溺尸王 §c用穿刺三叉戟击杀了 {player}!");
        }
        saveConfig();
        
        this.drownedKingManager = new DrownedKingManager(this);
        
        try {
            this.getCommand("drownedking").setExecutor(new DrownedKingCommand(this));
        } catch (Exception e) {
            getLogger().warning("Failed to register command: " + e.getMessage());
        }
        
        try {
            Bukkit.getPluginManager().registerEvents(new DrownedKingListener(this), this);
        } catch (Exception e) {
            getLogger().warning("Failed to register listeners: " + e.getMessage());
        }
        
        startBossTask();
        
        getLogger().info("溺尸王Boss插件已启用!");
    }
    
    @Override
    public void onDisable() {
        if (drownedKingManager != null) {
            drownedKingManager.saveAllBosses();
        }
        getLogger().info("溺尸王Boss插件已禁用!");
    }
    
    public static DrownedKingPlugin getInstance() {
        return instance;
    }
    
    public DrownedKingManager getDrownedKingManager() {
        return drownedKingManager;
    }
    
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "bosses.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "创建数据文件时出错", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "保存数据文件时出错", e);
        }
    }
    
    public YamlConfiguration getDataConfig() {
        return dataConfig;
    }
    
    private void startBossTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (drownedKingManager != null) {
                drownedKingManager.checkActiveBosses();
            }
        }, 0L, 100L);
    }
}
