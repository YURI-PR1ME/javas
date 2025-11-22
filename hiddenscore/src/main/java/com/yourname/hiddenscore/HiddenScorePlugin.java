package com.yourname.hiddenscore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class HiddenScorePlugin extends JavaPlugin {
    
    private static HiddenScorePlugin instance;
    private ScoreManager scoreManager;
    private File dataFile;
    private YamlConfiguration dataConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置文件
        saveDefaultConfig();
        setupDataFile();
        
        // 初始化管理器
        this.scoreManager = new ScoreManager();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new ScoreListener(), this);
        
        // 注册命令
        this.getCommand("hiddenscore").setExecutor(new ScoreCommand());
        
        // 启动定时任务
        startDailyTasks();
        
        getLogger().info("隐藏分系统已启用!");
    }
    
    @Override
    public void onDisable() {
        scoreManager.saveAllData();
        getLogger().info("隐藏分系统已禁用!");
    }
    
    public static HiddenScorePlugin getInstance() {
        return instance;
    }
    
    public ScoreManager getScoreManager() {
        return scoreManager;
    }
    
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "scores.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
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
    
    private void startDailyTasks() {
        // 每10秒检查一次游戏时间（用于每日统计）
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            scoreManager.checkDailyStats();
        }, 0L, 200L); // 10秒 = 200 tick
    }
}
