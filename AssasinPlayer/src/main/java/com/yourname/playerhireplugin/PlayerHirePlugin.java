package com.yourname.playerhireplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

public class PlayerHirePlugin extends JavaPlugin {
    
    private static PlayerHirePlugin instance;
    private HireManager hireManager;
    private File dataFile;
    private YamlConfiguration dataConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 检查依赖插件
        if (Bukkit.getPluginManager().getPlugin("CreditPlugin") == null) {
            getLogger().severe("CreditPlugin not found! This plugin depends on CreditPlugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化配置文件 - 使用更安全的方法
        setupConfig();
        setupDataFile();
        
        // 初始化管理器
        try {
            this.hireManager = new HireManager();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize HireManager", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 注册命令
        try {
            this.getCommand("hire").setExecutor(new HireCommand());
        } catch (Exception e) {
            getLogger().warning("Failed to register command: " + e.getMessage());
        }
        
        // 注册事件监听器
        try {
            Bukkit.getPluginManager().registerEvents(new HireListener(), this);
        } catch (Exception e) {
            getLogger().warning("Failed to register listeners: " + e.getMessage());
        }
        
        // 启动任务
        try {
            startContractCheckTask();
        } catch (Exception e) {
            getLogger().warning("Failed to start tasks: " + e.getMessage());
        }
        
        getLogger().info("玩家雇佣市场插件已启用! Version: " + getDescription().getVersion());
    }
    
    @Override
    public void onDisable() {
        if (hireManager != null) {
            try {
                hireManager.saveAllData();
            } catch (Exception e) {
                getLogger().warning("Error while saving data: " + e.getMessage());
            }
        }
        getLogger().info("玩家雇佣市场插件已禁用!");
    }
    
    public static PlayerHirePlugin getInstance() {
        return instance;
    }
    
    public HireManager getHireManager() {
        return hireManager;
    }
    
    private void setupConfig() {
        // 确保配置文件夹存在
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        File configFile = new File(getDataFolder(), "config.yml");
        
        // 如果配置文件不存在，从jar中复制
        if (!configFile.exists()) {
            try (InputStream in = getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                    getLogger().info("默认配置文件已创建");
                } else {
                    getLogger().warning("无法找到默认配置文件");
                    // 创建基本的配置文件
                    saveDefaultConfig();
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "创建配置文件时出错", e);
                // 创建基本的配置文件作为后备
                saveDefaultConfig();
            }
        }
        
        // 重载配置以确保使用最新版本
        reloadConfig();
    }
    
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
                getLogger().info("数据文件已创建");
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
    
    private void startContractCheckTask() {
        // 每30秒检查一次合约状态
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (hireManager != null) {
                try {
                    hireManager.checkActiveContracts();
                } catch (Exception e) {
                    getLogger().warning("Error in contract check task: " + e.getMessage());
                }
            }
        }, 0L, 600L); // 30秒 = 600 tick
    }
}
