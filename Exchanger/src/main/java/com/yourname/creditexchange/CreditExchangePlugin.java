package com.yourname.creditexchange;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class CreditExchangePlugin extends JavaPlugin {
    
    private static CreditExchangePlugin instance;
    private ExchangeManager exchangeManager;
    private File dataFile;
    private YamlConfiguration dataConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置文件
        saveDefaultConfig();
        setupDataFile();
        
        // 初始化管理器
        this.exchangeManager = new ExchangeManager();
        
        // 注册命令
        this.getCommand("exchange").setExecutor(new ExchangeCommand());
        
        getLogger().info("信用点兑换系统已启用!");
    }
    
    @Override
    public void onDisable() {
        saveData();
        getLogger().info("信用点兑换系统已禁用!");
    }
    
    public static CreditExchangePlugin getInstance() {
        return instance;
    }
    
    public ExchangeManager getExchangeManager() {
        return exchangeManager;
    }
    
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "exchange_data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("创建数据文件时出错: " + e.getMessage());
            }
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
}
