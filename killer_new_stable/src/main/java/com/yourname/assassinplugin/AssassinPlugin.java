package com.yourname.assassinplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class AssassinPlugin extends JavaPlugin {
    
    private static AssassinPlugin instance;
    private AssassinManager assassinManager;
    private File dataFile;
    private YamlConfiguration dataConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        if (Bukkit.getPluginManager().getPlugin("CreditPlugin") == null) {
            getLogger().severe("CreditPlugin not found! This plugin depends on CreditPlugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        saveDefaultConfig();
        setupDataFile();
        
        this.assassinManager = new AssassinManager();
        
        try {
            this.getCommand("assassin").setExecutor(new AssassinCommand());
        } catch (Exception e) {
            getLogger().warning("Failed to register command: " + e.getMessage());
        }
        
        try {
            Bukkit.getPluginManager().registerEvents(new AssassinListener(), this);
            Bukkit.getPluginManager().registerEvents(new AssassinGUIListener(), this);
            Bukkit.getPluginManager().registerEvents(new CommunicationBookListener(), this);
        } catch (Exception e) {
            getLogger().warning("Failed to register listeners: " + e.getMessage());
        }
        
        startAssassinTask();
        
        getLogger().info("买凶系统已启用! Version: " + getDescription().getVersion());
    }
    
    @Override
    public void onDisable() {
        if (assassinManager != null) {
            assassinManager.saveAllContracts();
        }
        getLogger().info("买凶系统已禁用!");
    }
    
    public static AssassinPlugin getInstance() {
        return instance;
    }
    
    public AssassinManager getAssassinManager() {
        return assassinManager;
    }
    
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "contracts.yml");
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
    
    private void startAssassinTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (assassinManager != null) {
                assassinManager.checkActiveContracts();
            }
        }, 0L, 100L);
    }
}
