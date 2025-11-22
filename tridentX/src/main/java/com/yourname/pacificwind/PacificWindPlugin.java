// [file name]: PacificWindPlugin.java
package com.yourname.pacificwind;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class PacificWindPlugin extends JavaPlugin {
    
    private static PacificWindPlugin instance;
    private PacificWindManager windManager;
    private File customConfigFile;
    private FileConfiguration customConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 创建数据文件夹
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 创建自定义配置文件
        customConfigFile = new File(getDataFolder(), "data.yml");
        if (!customConfigFile.exists()) {
            try {
                customConfigFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("创建数据文件时出错: " + e.getMessage());
            }
        }
        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);
        
        this.windManager = new PacificWindManager(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PacificWindListener(this), this);
        
        // 注册命令
        getCommand("pacificwind").setExecutor(new PacificWindCommand(this));
        
        getLogger().info("§9太平洋之风插件已启用!");
        getLogger().info("暴君召唤状态: " + (isTyrantSummoned() ? "§c已召唤" : "§a未召唤"));
    }
    
    @Override
    public void onDisable() {
        saveCustomConfig();
        getLogger().info("§9太平洋之风插件已禁用!");
    }
    
    public static PacificWindPlugin getInstance() {
        return instance;
    }
    
    public PacificWindManager getWindManager() {
        return windManager;
    }
    
    /**
     * 检查暴君是否已被召唤
     */
    public boolean isTyrantSummoned() {
        return customConfig.getBoolean("tyrant-summoned", false);
    }
    
    /**
     * 设置暴君召唤状态
     */
    public void setTyrantSummoned(boolean summoned) {
        customConfig.set("tyrant-summoned", summoned);
        saveCustomConfig();
        
        if (summoned) {
            getLogger().warning("暴君已被召唤，服务器将无法再次召唤暴君");
        }
    }
    
    /**
     * 重置暴君召唤状态（管理员命令）
     */
    public void resetTyrantSummoned() {
        customConfig.set("tyrant-summoned", false);
        saveCustomConfig();
        getLogger().info("暴君召唤状态已重置，现在可以重新召唤暴君");
    }
    
    /**
     * 保存自定义配置
     */
    private void saveCustomConfig() {
        try {
            customConfig.save(customConfigFile);
        } catch (IOException e) {
            getLogger().severe("保存数据文件时出错: " + e.getMessage());
        }
    }
}
