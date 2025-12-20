package com.yourname.creditplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class CreditPlugin extends JavaPlugin {
    
    private static CreditPlugin instance;
    private CreditManager creditManager;
    private File dataFile;
    private YamlConfiguration dataConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置文件
        saveDefaultConfig();
        setupDataFile();
        
        // 初始化管理器
        this.creditManager = new CreditManager();
        
        // 注册命令
        this.getCommand("credit").setExecutor(new CreditCommand());
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new BookListener(), this);
        getServer().getPluginManager().registerEvents(new ReviveListener(), this);
        getServer().getPluginManager().registerEvents(new PortalListener(), this); // 新增传送门监听器
        
        // 注册合成配方
        registerRecipes();
        
        // 注册任务
        startGameTimeCheckTask();
        
        getLogger().info("公民信用点系统已启用!");
    }
    
    @Override
    public void onDisable() {
        creditManager.saveAllData();
        getLogger().info("公民信用点系统已禁用!");
    }
    
    public static CreditPlugin getInstance() {
        return instance;
    }
    
    public CreditManager getCreditManager() {
        return creditManager;
    }
    
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("保存数据文件时出错: " + e.getMessage());
        }
    }
    
    public YamlConfiguration getDataConfig() {
        return dataConfig;
    }
    
    private void startGameTimeCheckTask() {
        // 每10秒检查一次游戏时间（用于杀人日检测）
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            creditManager.checkKillingDay();
        }, 0L, 200L); // 10秒 = 200 tick
    }
    
    private void registerRecipes() {
        // 注册复活选择台合成配方
        creditManager.registerReviveStationRecipe();
    }
}
